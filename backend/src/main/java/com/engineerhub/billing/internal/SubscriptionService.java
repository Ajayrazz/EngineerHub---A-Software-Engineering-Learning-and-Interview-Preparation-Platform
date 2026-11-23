package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionCheckout;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionConfirmView;
import com.engineerhub.billing.internal.SubscriptionDtos.SubscriptionView;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Start, confirm, view and cancel of auto-renewing subscriptions (docs/21 §2, §3, §6). The client
 * names only a {@code planPriceId}; amount, currency and period come from the server's price table
 * and are snapshotted. Nothing here grants access: renewals and the first payment become access
 * only through {@link PaymentCapturedHandler#subscriptionCharged} after a verified webhook.
 * Provider calls happen outside database transactions.
 *
 * <p>Cycles: a Razorpay subscription needs a fixed number of billing cycles, so a subscription is
 * created for 10 years (120 months or 10 years); it ends earlier when the user cancels or payments
 * stop (to be confirmed against the provider's limits with real test keys).
 */
@Service
class SubscriptionService {

  static final int CYCLES_MONTH = 120;
  static final int CYCLES_YEAR = 10;
  private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._:-]{8,80}$");
  private static final int MAX_STARTS_PER_HOUR = 10;

  private final JdbcClient jdbc;
  private final Clock clock;
  private final UserDirectory directory;
  private final PaymentProvider provider;
  private final TransactionTemplate tx;
  private final OutboxPublisher events;

  SubscriptionService(
      JdbcClient jdbc,
      Clock clock,
      UserDirectory directory,
      PaymentProvider provider,
      TransactionTemplate tx,
      OutboxPublisher events) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.directory = directory;
    this.provider = provider;
    this.tx = tx;
    this.events = events;
  }

  private record PriceRow(
      UUID planId,
      String planName,
      String mode,
      String unit,
      String currency,
      long amount,
      boolean purchasable,
      String providerPlanRef) {}

  private PriceRow price(UUID planPriceId) {
    return jdbc.sql(
            """
            select pl.id, pl.name, pp.billing_mode, pp.period_unit, pp.currency, pp.amount_incl_tax_minor,
                   (pp.active and pl.active and exists (select 1 from billing.plan_entitlements e where e.plan_id = pl.id)),
                   pp.provider_plan_ref
            from billing.plan_prices pp join billing.plans pl on pl.id = pp.plan_id where pp.id = :id
            """)
        .param("id", planPriceId)
        .query(
            (rs, i) ->
                new PriceRow(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getLong(6),
                    rs.getBoolean(7),
                    rs.getString(8)))
        .optional()
        .orElseThrow(() -> ApiException.notFound("Price not found."));
  }

  // ================================================================= start

  SubscriptionCheckout start(Authentication auth, UUID planPriceId, String idempotencyKey) {
    if (!provider.configured()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Online payment is not available yet.");
    }
    if (idempotencyKey != null && !IDEMPOTENCY.matcher(idempotencyKey).matches()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Idempotency-Key must be 8-80 characters (letters, digits, . _ : -).");
    }
    var user = directory.current(auth);
    if (!user.active()) {
      throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED, "Your account cannot make purchases.");
    }

    // 1) record the subscription (own transaction); the database allows one live subscription per
    // user and plan
    Object[] created =
        tx.execute(
            status -> {
              if (idempotencyKey != null) {
                var existing =
                    jdbc.sql(
                            "select id, status, provider_subscription_ref, plan_price_id from billing.subscriptions where user_id = :u and idempotency_key = :k")
                        .param("u", user.id())
                        .param("k", idempotencyKey)
                        .query(
                            (rs, i) ->
                                new Object[] {
                                  rs.getObject(1, UUID.class),
                                  rs.getString(2),
                                  rs.getString(3),
                                  rs.getObject(4, UUID.class)
                                })
                        .optional();
                if (existing.isPresent()) {
                  Object[] e = existing.get();
                  if (!planPriceId.equals(e[3])) {
                    throw ApiException.conflict(
                        "This Idempotency-Key was used for a different purchase.");
                  }
                  return new Object[] {"existing", e};
                }
              }
              PriceRow p = price(planPriceId);
              if (!p.purchasable()) {
                throw ApiException.conflict("This price is not available.");
              }
              if (!p.mode().equals("RECURRING")) {
                throw ApiException.conflict(
                    "This price is a one-time pass: buy it as a pass, not a subscription.");
              }
              long recent =
                  jdbc.sql(
                          "select count(*) from billing.subscriptions where user_id = :u and created_at > :since")
                      .param("u", user.id())
                      .param("since", Timestamp.from(clock.instant().minusSeconds(3600)))
                      .query(Long.class)
                      .single();
              if (recent >= MAX_STARTS_PER_HOUR) {
                throw new ApiException(
                    ErrorCode.RATE_LIMITED, "Too many attempts. Try again later.");
              }
              UUID id = Ids.newId();
              Instant now = clock.instant();
              try {
                jdbc.sql(
                        """
                        insert into billing.subscriptions (id, user_id, plan_id, plan_price_id, period_unit, currency, amount_minor, status, provider, idempotency_key, created_at, updated_at)
                        values (:id, :u, :pl, :pp, :unit, :c, :a, 'created', :prov, :k, :now, :now)
                        """)
                    .param("id", id)
                    .param("u", user.id())
                    .param("pl", p.planId())
                    .param("pp", planPriceId)
                    .param("unit", p.unit())
                    .param("c", p.currency())
                    .param("a", p.amount())
                    .param("prov", provider.name())
                    .param("k", idempotencyKey)
                    .param("now", Timestamp.from(now))
                    .update();
              } catch (DuplicateKeyException e) {
                throw ApiException.conflict(
                    "You already have an auto-renewing subscription for this plan.");
              }
              return new Object[] {"new", id, p};
            });

    if ("existing".equals(created[0])) {
      Object[] e = (Object[]) created[1];
      String status = (String) e[1];
      if (!status.equals("pending") || e[2] == null) {
        throw ApiException.conflict("This subscription is already " + status + ".");
      }
      PriceRow p = price(planPriceId);
      return checkout((UUID) e[0], (String) e[2], p);
    }
    UUID subscriptionId = (UUID) created[1];
    PriceRow p = (PriceRow) created[2];

    // 2) provider plan (once per price) and subscription, no transaction held
    String subscriptionRef;
    try {
      String planRef = ensureProviderPlan(planPriceId, p);
      subscriptionRef =
          provider
              .createSubscription(
                  planRef,
                  p.unit().equals("year") ? CYCLES_YEAR : CYCLES_MONTH,
                  Map.of("subscriptionId", subscriptionId.toString()))
              .subscriptionId();
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s -> setStatus(subscriptionId, "failed", "created"));
      throw e;
    }

    // 3) remember it
    tx.executeWithoutResult(
        s ->
            jdbc.sql(
                    "update billing.subscriptions set provider_subscription_ref = :r, status = 'pending', updated_at = :now where id = :id and status = 'created'")
                .param("r", subscriptionRef)
                .param("now", Timestamp.from(clock.instant()))
                .param("id", subscriptionId)
                .update());
    return checkout(subscriptionId, subscriptionRef, p);
  }

  private String ensureProviderPlan(UUID planPriceId, PriceRow p) {
    if (p.providerPlanRef() != null) {
      return p.providerPlanRef();
    }
    String created = provider.createPlan(p.unit(), p.amount(), p.currency(), p.planName());
    // two first buyers may both create one: the first to store wins and both use the winner
    tx.executeWithoutResult(
        s ->
            jdbc.sql(
                    "update billing.plan_prices set provider_plan_ref = :r where id = :id and provider_plan_ref is null")
                .param("r", created)
                .param("id", planPriceId)
                .update());
    return jdbc.sql("select provider_plan_ref from billing.plan_prices where id = :id")
        .param("id", planPriceId)
        .query(String.class)
        .single();
  }

  private SubscriptionCheckout checkout(UUID id, String ref, PriceRow p) {
    return new SubscriptionCheckout(
        id, ref, provider.publicKeyId(), p.amount(), p.currency(), p.unit(), p.planName(), "test");
  }

  private void setStatus(UUID id, String to, String from) {
    jdbc.sql(
            "update billing.subscriptions set status = :to, updated_at = :now where id = :id and status = :from")
        .param("to", to)
        .param("now", Timestamp.from(clock.instant()))
        .param("id", id)
        .param("from", from)
        .update();
  }

  // ================================================================= confirm / view (never grant)

  SubscriptionConfirmView confirm(
      Authentication auth,
      UUID id,
      String subscriptionRefFromBrowser,
      String paymentId,
      String signature) {
    UUID user = directory.current(auth).id();
    SubscriptionView view = view(user, id);
    String ref =
        jdbc.sql(
                "select provider_subscription_ref from billing.subscriptions where id = :id and user_id = :u")
            .param("id", id)
            .param("u", user)
            .query(String.class)
            .optional()
            .orElse(null);
    // the subscription id must be the one WE stored, not whatever the browser reports
    boolean valid =
        ref != null
            && ref.equals(subscriptionRefFromBrowser)
            && provider.verifySubscriptionSignature(ref, paymentId, signature);
    return new SubscriptionConfirmView(valid, view);
  }

  SubscriptionView view(Authentication auth, UUID id) {
    return view(directory.current(auth).id(), id);
  }

  private SubscriptionView view(UUID user, UUID id) {
    return jdbc.sql(
            """
            select s.id, s.status, p.name, s.amount_minor, s.currency, s.period_unit,
                   exists (select 1 from billing.payments pay where pay.subscription_id = s.id), s.created_at
            from billing.subscriptions s join billing.plans p on p.id = s.plan_id
            where s.id = :id and s.user_id = :u
            """)
        .param("id", id)
        .param("u", user)
        .query(
            (rs, i) ->
                new SubscriptionView(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getString(5).strip(),
                    rs.getString(6),
                    "cancel_scheduled".equals(rs.getString(2)),
                    rs.getBoolean(7),
                    rs.getTimestamp(8).toInstant()))
        .optional()
        .orElseThrow(
            () -> ApiException.notFound("Subscription not found.")); // someone else's looks missing
  }

  // ================================================================= cancel auto-renew

  /**
   * Stops future renewals at the end of the paid cycle. Paid access is untouched and nothing is
   * refunded (docs/21 §7: cancelling does not refund the current period).
   */
  SubscriptionView cancel(Authentication auth, UUID id) {
    UUID user = directory.current(auth).id();
    SubscriptionView current = view(user, id);
    if (current.status().equals("cancel_scheduled")) {
      return current; // idempotent
    }
    if (!current.status().equals("active") && !current.status().equals("past_due")) {
      throw ApiException.conflict("Only a running subscription can be cancelled.");
    }
    String ref =
        jdbc.sql(
                "select provider_subscription_ref from billing.subscriptions where id = :id and user_id = :u")
            .param("id", id)
            .param("u", user)
            .query(String.class)
            .single();
    provider.cancelSubscriptionAtCycleEnd(ref); // 503 (status unchanged) if the provider refuses
    tx.executeWithoutResult(
        s -> {
          int n =
              jdbc.sql(
                      "update billing.subscriptions set status = 'cancel_scheduled', cancel_requested_at = :now, updated_at = :now where id = :id and status in ('active','past_due')")
                  .param("now", Timestamp.from(clock.instant()))
                  .param("id", id)
                  .update();
          if (n > 0) {
            events.publish(
                "billing.subscription.autorenewcancelled",
                1,
                "user",
                user.toString(),
                Map.of("userId", user, "subscriptionId", id));
          }
        });
    return view(user, id);
  }
}
