package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.PurchaseDtos.CheckoutView;
import com.engineerhub.billing.internal.PurchaseDtos.ConfirmView;
import com.engineerhub.billing.internal.PurchaseDtos.IntentView;
import com.engineerhub.billing.internal.PurchaseDtos.QuoteView;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Quote and start of a purchase (docs/21 §2, §6). The client names only a {@code planPriceId}: the
 * amount, the currency and the period always come from the server's price table and are snapshotted
 * into the intent. This service never grants access: that is the payment-captured handler's job
 * after a verified webhook.
 *
 * <p>The provider call happens <em>outside</em> any database transaction (short timeouts, no
 * connection held).
 */
@Service
class PurchaseService {

  private static final Pattern IDEMPOTENCY = Pattern.compile("^[A-Za-z0-9._:-]{8,80}$");
  private static final int MAX_INTENTS_PER_HOUR = 20;
  private static final Duration INTENT_LIFETIME = Duration.ofMinutes(30);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final UserDirectory directory;
  private final PaymentProvider provider;
  private final TransactionTemplate tx;

  PurchaseService(
      JdbcClient jdbc,
      Clock clock,
      UserDirectory directory,
      PaymentProvider provider,
      TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.directory = directory;
    this.provider = provider;
    this.tx = tx;
  }

  private record PriceRow(
      UUID planId,
      String planName,
      String mode,
      String unit,
      String currency,
      long amount,
      boolean purchasable) {}

  private Optional<PriceRow> price(UUID planPriceId) {
    return jdbc.sql(
            """
            select pl.id, pl.name, pp.billing_mode, pp.period_unit, pp.currency, pp.amount_incl_tax_minor,
                   (pp.active and pl.active and exists (select 1 from billing.plan_entitlements e where e.plan_id = pl.id))
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
                    rs.getBoolean(7)))
        .optional();
  }

  // ================================================================= quote

  QuoteView quote(UUID planPriceId) {
    PriceRow p = price(planPriceId).orElseThrow(() -> ApiException.notFound("Price not found."));
    String note = null;
    boolean purchasable = p.purchasable();
    if (!purchasable) {
      note = "This price is not available.";
    } else if (!provider.configured()) {
      purchasable = false;
      note = "Online payment is not available yet.";
    }
    return new QuoteView(
        planPriceId, p.planName(), p.mode(), p.unit(), p.currency(), p.amount(), purchasable, note);
  }

  // ================================================================= start

  CheckoutView start(Authentication auth, UUID planPriceId, String idempotencyKey) {
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

    // 1) record the intent (own transaction)
    Object[] created =
        tx.execute(
            status -> {
              if (idempotencyKey != null) {
                var existing =
                    jdbc.sql(
                            "select id, status, provider_order_ref, amount_minor, currency, plan_id, plan_price_id from billing.purchase_intents where user_id = :u and idempotency_key = :k")
                        .param("u", user.id())
                        .param("k", idempotencyKey)
                        .query(
                            (rs, i) ->
                                new Object[] {
                                  rs.getObject(1, UUID.class),
                                  rs.getString(2),
                                  rs.getString(3),
                                  rs.getLong(4),
                                  rs.getString(5),
                                  rs.getObject(6, UUID.class),
                                  rs.getObject(7, UUID.class)
                                })
                        .optional();
                if (existing.isPresent()) {
                  Object[] e = existing.get();
                  if (!planPriceId.equals(e[6])) {
                    throw ApiException.conflict(
                        "This Idempotency-Key was used for a different purchase.");
                  }
                  return new Object[] {"existing", e};
                }
              }
              PriceRow p =
                  price(planPriceId).orElseThrow(() -> ApiException.notFound("Price not found."));
              if (!p.purchasable()) {
                throw ApiException.conflict("This price is not available.");
              }
              if (!p.mode().equals("ONE_TIME")) {
                throw ApiException.conflict(
                    "This price auto-renews: start it as a subscription, not a one-time purchase.");
              }
              long recent =
                  jdbc.sql(
                          "select count(*) from billing.purchase_intents where user_id = :u and created_at > :since")
                      .param("u", user.id())
                      .param("since", Timestamp.from(clock.instant().minusSeconds(3600)))
                      .query(Long.class)
                      .single();
              if (recent >= MAX_INTENTS_PER_HOUR) {
                throw new ApiException(
                    ErrorCode.RATE_LIMITED, "Too many purchase attempts. Try again later.");
              }
              UUID id = Ids.newId();
              Instant now = clock.instant();
              try {
                jdbc.sql(
                        """
                        insert into billing.purchase_intents (id, user_id, plan_id, plan_price_id, billing_mode, period_unit, currency, amount_minor, status, provider, idempotency_key, created_at, expires_at, updated_at)
                        values (:id, :u, :pl, :pp, :m, :unit, :c, :a, 'created', :prov, :k, :now, :exp, :now)
                        """)
                    .param("id", id)
                    .param("u", user.id())
                    .param("pl", p.planId())
                    .param("pp", planPriceId)
                    .param("m", p.mode())
                    .param("unit", p.unit())
                    .param("c", p.currency())
                    .param("a", p.amount())
                    .param("prov", provider.name())
                    .param("k", idempotencyKey)
                    .param("now", Timestamp.from(now))
                    .param("exp", Timestamp.from(now.plus(INTENT_LIFETIME)))
                    .update();
              } catch (DuplicateKeyException e) {
                throw ApiException.conflict("This purchase was already started.");
              }
              return new Object[] {"new", id, p};
            });

    if ("existing".equals(created[0])) {
      Object[] e = (Object[]) created[1];
      String status = (String) e[1];
      if (!status.equals("payment_pending") || e[2] == null) {
        throw ApiException.conflict("This purchase is already " + status + ".");
      }
      return checkout(
          (UUID) e[0], (String) e[2], (Long) e[3], (String) e[4], planName((UUID) e[5]));
    }
    UUID intentId = (UUID) created[1];
    PriceRow p = (PriceRow) created[2];

    // 2) provider order (no transaction held)
    PaymentProvider.ProviderOrder order;
    try {
      order =
          provider.createOrder(
              p.amount(),
              p.currency(),
              intentId.toString().replace("-", ""),
              Map.of("intentId", intentId.toString()));
    } catch (RuntimeException e) {
      tx.executeWithoutResult(s -> setStatus(intentId, "failed", "created"));
      throw e;
    }

    // 3) remember the provider order
    tx.executeWithoutResult(
        s ->
            jdbc.sql(
                    "update billing.purchase_intents set provider_order_ref = :o, status = 'payment_pending', updated_at = :now where id = :id and status = 'created'")
                .param("o", order.orderId())
                .param("now", Timestamp.from(clock.instant()))
                .param("id", intentId)
                .update());
    return checkout(intentId, order.orderId(), p.amount(), p.currency(), p.planName());
  }

  private CheckoutView checkout(
      UUID intentId, String orderId, long amount, String currency, String planName) {
    return new CheckoutView(
        intentId, orderId, provider.publicKeyId(), amount, currency, planName, "test");
  }

  private String planName(UUID planId) {
    return jdbc.sql("select name from billing.plans where id = :id")
        .param("id", planId)
        .query(String.class)
        .single();
  }

  private void setStatus(UUID intentId, String to, String from) {
    jdbc.sql(
            "update billing.purchase_intents set status = :to, updated_at = :now where id = :id and status = :from")
        .param("to", to)
        .param("now", Timestamp.from(clock.instant()))
        .param("id", intentId)
        .param("from", from)
        .update();
  }

  // ================================================================= confirm / status (never grant
  // access)

  ConfirmView confirm(
      Authentication auth, UUID intentId, String orderId, String paymentId, String signature) {
    UUID user = directory.current(auth).id();
    IntentView intent = intent(user, intentId);
    String ref =
        jdbc.sql(
                "select provider_order_ref from billing.purchase_intents where id = :id and user_id = :u")
            .param("id", intentId)
            .param("u", user)
            .query(String.class)
            .optional()
            .orElse(null);
    // the order id must be the one WE stored for this intent, not whatever the browser reports
    boolean valid =
        ref != null
            && ref.equals(orderId)
            && provider.verifyCheckoutSignature(ref, paymentId, signature);
    return new ConfirmView(valid, intent);
  }

  IntentView intent(Authentication auth, UUID intentId) {
    return intent(directory.current(auth).id(), intentId);
  }

  private IntentView intent(UUID user, UUID intentId) {
    return jdbc.sql(
            "select i.id, i.status, i.amount_minor, i.currency, p.name, i.created_at from billing.purchase_intents i join billing.plans p on p.id = i.plan_id where i.id = :id and i.user_id = :u")
        .param("id", intentId)
        .param("u", user)
        .query(
            (rs, i) ->
                new IntentView(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getLong(3),
                    rs.getString(4),
                    rs.getString(5),
                    rs.getTimestamp(6).toInstant()))
        .optional()
        .orElseThrow(
            () ->
                ApiException.notFound(
                    "Purchase not found.")); // someone else's intent looks exactly like a missing
    // one
  }

  boolean paymentsEnabled() {
    return provider.configured();
  }
}
