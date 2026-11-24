package com.engineerhub.billing.internal;

import com.engineerhub.billing.EntitlementService;
import com.engineerhub.billing.internal.BillingDtos.AccessView;
import com.engineerhub.billing.internal.BillingDtos.BillingSummary;
import com.engineerhub.billing.internal.BillingDtos.GrantRequest;
import com.engineerhub.billing.internal.BillingDtos.PaymentView;
import com.engineerhub.billing.internal.BillingDtos.PeriodView;
import com.engineerhub.billing.internal.BillingDtos.PlanCreate;
import com.engineerhub.billing.internal.BillingDtos.PlanPatch;
import com.engineerhub.billing.internal.BillingDtos.PlanView;
import com.engineerhub.billing.internal.BillingDtos.PriceCreate;
import com.engineerhub.billing.internal.BillingDtos.PriceView;
import com.engineerhub.billing.internal.BillingDtos.SubscriptionSummary;
import com.engineerhub.identity.Authorizer;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Billing core: plans/prices (admin-configured, never seeded), access periods and the entitlement
 * decision.
 *
 * <p>Entitlements are derived <em>only</em> from non-revoked access periods whose plan carries the
 * code and that are running at read time (no cache that could outlive a revocation; expiry is
 * checked on every read). Staff who hold {@code content.premium.read} keep access through their
 * role, as before.
 */
@Service
class BillingService implements EntitlementService {

  static final String PREMIUM = "premium_content";
  private static final Set<String> CODES = Set.of(PREMIUM);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final Authorizer authz;
  private final UserDirectory directory;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final int renewalWindowDays;

  BillingService(
      JdbcClient jdbc,
      Clock clock,
      Authorizer authz,
      UserDirectory directory,
      AuditLog audit,
      OutboxPublisher events,
      @org.springframework.beans.factory.annotation.Value(
              "${engineerhub.billing.renewal-window-days:7}")
          int renewalWindowDays) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.authz = authz;
    this.directory = directory;
    this.audit = audit;
    this.events = events;
    this.renewalWindowDays = renewalWindowDays;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ================================================================= the entitlement decision

  @Override
  public boolean canReadPremium(
      Authentication authentication, String resourceType, Object resourceId) {
    if (authentication == null) {
      return false;
    }
    if (authz.can(authentication, "content.premium.read", resourceType, resourceId)) {
      return true; // staff through their role (global, or scoped to this resource for
      // instructors/setters)
    }
    // Read-only lookup by token subject: this runs inside read-only transactions, so it must never
    // provision
    // a user. Someone with no local record cannot hold an access period anyway.
    return directory
        .findBySubject(authentication.getName())
        .filter(com.engineerhub.identity.UserRef::active)
        .map(u -> hasEntitlement(u.id(), PREMIUM))
        .orElse(false);
  }

  boolean hasEntitlement(UUID userId, String code) {
    return jdbc.sql(
            """
            select exists (
              select 1 from billing.access_periods ap
              join billing.plan_entitlements pe on pe.plan_id = ap.plan_id
              where ap.user_id = :u and pe.entitlement_code = :c and ap.revoked_at is null
                and ap.starts_at <= :now and :now < ap.ends_at)
            """)
        .param("u", userId)
        .param("c", code)
        .param("now", now())
        .query(Boolean.class)
        .single();
  }

  // ================================================================= plans (admin) and the public
  // price list

  @Transactional
  PlanView createPlan(Authentication actor, PlanCreate r) {
    UUID id = Ids.newId();
    try {
      jdbc.sql("insert into billing.plans (id, code, name, created_at) values (:id, :c, :n, :now)")
          .param("id", id)
          .param("c", r.code())
          .param("n", r.name().strip())
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A plan with this code already exists.");
    }
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.plan.created", "plan", id.toString())
            .withChange(null, Map.of("code", r.code())));
    return plan(id);
  }

  @Transactional
  PlanView patchPlan(Authentication actor, UUID id, PlanPatch p) {
    var before = plan(id);
    jdbc.sql(
            "update billing.plans set name = coalesce(:n, name), active = coalesce(:a, active) where id = :id")
        .param("n", p.name() == null ? null : p.name().strip())
        .param("a", p.active())
        .param("id", id)
        .update();
    if (p.active() != null && p.active() != before.active()) {
      audit.record(
          actor,
          directory.current(actor).id(),
          AuditEvent.of("billing.plan.active.changed", "plan", id.toString())
              .withChange(Map.of("active", before.active()), Map.of("active", p.active())));
    }
    return plan(id);
  }

  @Transactional
  PlanView setEntitlements(Authentication actor, UUID id, List<String> codes) {
    plan(id);
    Set<String> wanted = new java.util.TreeSet<>(codes);
    if (!CODES.containsAll(wanted)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown entitlement code.");
    }
    List<String> old = entitlements(id);
    jdbc.sql("delete from billing.plan_entitlements where plan_id = :p").param("p", id).update();
    wanted.forEach(
        c ->
            jdbc.sql(
                    "insert into billing.plan_entitlements (plan_id, entitlement_code) values (:p, :c)")
                .param("p", id)
                .param("c", c)
                .update());
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.plan.entitlements.changed", "plan", id.toString())
            .withChange(Map.of("codes", old), Map.of("codes", List.copyOf(wanted))));
    return plan(id);
  }

  @Transactional
  PlanView addPrice(Authentication actor, UUID planId, PriceCreate r) {
    plan(planId);
    UUID id = Ids.newId();
    jdbc.sql(
            "insert into billing.plan_prices (id, plan_id, billing_mode, period_unit, amount_incl_tax_minor, created_at) values (:id, :p, :m, :u, :a, :now)")
        .param("id", id)
        .param("p", planId)
        .param("m", r.billingMode())
        .param("u", r.periodUnit())
        .param("a", r.amountInclTaxMinor())
        .param("now", now())
        .update();
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.price.created", "plan", planId.toString())
            .withChange(
                null,
                Map.of(
                    "priceId",
                    id.toString(),
                    "mode",
                    r.billingMode(),
                    "unit",
                    r.periodUnit(),
                    "amountMinor",
                    r.amountInclTaxMinor())));
    return plan(planId);
  }

  /** Prices are never edited (old payments reference them): they can only be switched off. */
  @Transactional
  PlanView setPriceActive(Authentication actor, UUID planId, UUID priceId, boolean active) {
    plan(planId);
    int n =
        jdbc.sql("update billing.plan_prices set active = :a where id = :id and plan_id = :p")
            .param("a", active)
            .param("id", priceId)
            .param("p", planId)
            .update();
    if (n == 0) {
      throw ApiException.notFound("Price not found.");
    }
    audit.record(
        actor,
        directory.current(actor).id(),
        AuditEvent.of("billing.price.active.changed", "plan", planId.toString())
            .withChange(null, Map.of("priceId", priceId.toString(), "active", active)));
    return plan(planId);
  }

  @Transactional(readOnly = true)
  List<PlanView> plans(boolean publicOnly) {
    List<UUID> ids =
        jdbc.sql(
                "select id from billing.plans "
                    + (publicOnly ? "where active" : "")
                    + " order by created_at, id")
            .query(UUID.class)
            .list();
    List<PlanView> out = new ArrayList<>();
    for (UUID id : ids) {
      PlanView p = plan(id);
      if (publicOnly) {
        var active = p.prices().stream().filter(PriceView::active).toList();
        if (active.isEmpty()) {
          continue; // a plan nobody can buy is not advertised
        }
        p = new PlanView(p.id(), p.code(), p.name(), p.active(), p.entitlements(), active);
      }
      out.add(p);
    }
    return out;
  }

  @Transactional(readOnly = true)
  PlanView plan(UUID id) {
    var row =
        jdbc.sql("select code, name, active from billing.plans where id = :id")
            .param("id", id)
            .query((rs, i) -> new Object[] {rs.getString(1), rs.getString(2), rs.getBoolean(3)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Plan not found."));
    List<PriceView> prices =
        jdbc.sql(
                "select id, billing_mode, period_unit, currency, amount_incl_tax_minor, active from billing.plan_prices where plan_id = :p order by period_unit desc, billing_mode, amount_incl_tax_minor, id")
            .param("p", id)
            .query(
                (rs, i) ->
                    new PriceView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getLong(5),
                        rs.getBoolean(6)))
            .list();
    return new PlanView(
        id, (String) row[0], (String) row[1], (Boolean) row[2], entitlements(id), prices);
  }

  private List<String> entitlements(UUID planId) {
    return jdbc.sql(
            "select entitlement_code from billing.plan_entitlements where plan_id = :p order by 1")
        .param("p", planId)
        .query(String.class)
        .list();
  }

  // ================================================================= access periods

  /** An audited complimentary grant: the only source of access until payments exist (M6.2). */
  @Transactional
  AccessView grant(Authentication actor, UUID userId, GrantRequest r) {
    UUID admin = directory.current(actor).id();
    var user =
        directory.findById(userId).orElseThrow(() -> ApiException.notFound("User not found."));
    var plan = plan(r.planId());
    if (!plan.active()) {
      throw ApiException.conflict("This plan is not active.");
    }
    if (plan.entitlements().isEmpty()) {
      throw ApiException.conflict("This plan grants no entitlement yet. Add one first.");
    }
    Instant start = clock.instant();
    Instant end = start.plus(Duration.ofDays(r.days()));
    UUID id = Ids.newId();
    jdbc.sql(
            "insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, granted_by, reason, created_at) values (:id, :u, :p, :s, :e, 'admin_grant', :by, :r, :now)")
        .param("id", id)
        .param("u", user.id())
        .param("p", plan.id())
        .param("s", Timestamp.from(start))
        .param("e", Timestamp.from(end))
        .param("by", admin)
        .param("r", r.reason().strip())
        .param("now", now())
        .update();
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.access.granted", "user", userId.toString())
            .withChange(
                null,
                Map.of(
                    "periodId",
                    id.toString(),
                    "plan",
                    plan.code(),
                    "days",
                    r.days(),
                    "endsAt",
                    end.toString()))
            .withReason(r.reason().strip()));
    events.publish(
        "billing.access.granted",
        1,
        "user",
        userId.toString(),
        Map.of("userId", userId, "periodId", id, "endsAt", end.toString()));
    return access(userId);
  }

  @Transactional
  AccessView revoke(Authentication actor, UUID periodId, String reason) {
    UUID admin = directory.current(actor).id();
    var row =
        jdbc.sql(
                "select user_id, revoked_at is not null from billing.access_periods where id = :id for update")
            .param("id", periodId)
            .query((rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getBoolean(2)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Access period not found."));
    if ((Boolean) row[1]) {
      throw ApiException.conflict("This access period is already revoked.");
    }
    jdbc.sql(
            "update billing.access_periods set revoked_at = :now, revoked_by = :by, revoked_reason = :r where id = :id")
        .param("now", now())
        .param("by", admin)
        .param("r", reason.strip())
        .param("id", periodId)
        .update();
    UUID userId = (UUID) row[0];
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.access.revoked", "user", userId.toString())
            .withChange(Map.of("periodId", periodId.toString()), null)
            .withReason(reason.strip()));
    events.publish(
        "billing.access.revoked",
        1,
        "user",
        userId.toString(),
        Map.of("userId", userId, "periodId", periodId));
    return access(userId);
  }

  @Transactional(readOnly = true)
  AccessView access(UUID userId) {
    Instant now = clock.instant();
    Map<UUID, PeriodView> periods = new LinkedHashMap<>();
    jdbc.sql(
            """
            select ap.id, p.name, ap.starts_at, ap.ends_at, ap.source_type, ap.revoked_at is not null, ap.revoked_reason, ap.reason, ap.created_at
            from billing.access_periods ap join billing.plans p on p.id = ap.plan_id
            where ap.user_id = :u order by ap.ends_at desc, ap.id limit 50
            """)
        .param("u", userId)
        .query(
            (rs, i) ->
                periods.put(
                    rs.getObject(1, UUID.class),
                    new PeriodView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(),
                        rs.getString(5),
                        rs.getBoolean(6),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getTimestamp(9).toInstant())))
        .list();
    Instant validThrough = premiumCoverageEnd(userId, now);
    return new AccessView(validThrough != null, validThrough, List.copyOf(periods.values()));
  }

  /**
   * End of the user's <em>contiguous</em> premium coverage from {@code now}, or null when no
   * premium period is running. Purchases made while access is running are queued to start when the
   * current period ends (docs/21 overlap rule), so the date to show is the end of the whole chain,
   * not of the period that happens to be running.
   */
  private Instant premiumCoverageEnd(UUID userId, Instant now) {
    List<Instant[]> ranges =
        jdbc.sql(
                """
                select ap.starts_at, ap.ends_at from billing.access_periods ap
                join billing.plan_entitlements pe on pe.plan_id = ap.plan_id
                where ap.user_id = :u and pe.entitlement_code = 'premium_content'
                  and ap.revoked_at is null and ap.ends_at > :now
                order by ap.starts_at, ap.ends_at
                """)
            .param("u", userId)
            .param("now", Timestamp.from(now))
            .query(
                (rs, i) ->
                    new Instant[] {rs.getTimestamp(1).toInstant(), rs.getTimestamp(2).toInstant()})
            .list();
    return coverageEnd(now, ranges);
  }

  /** Pure chain walk: extends from {@code now} through every period that starts by the cursor. */
  static Instant coverageEnd(Instant now, List<Instant[]> sortedRanges) {
    Instant cursor = now;
    for (Instant[] r : sortedRanges) {
      if (!r[0].isAfter(cursor) && r[1].isAfter(cursor)) {
        cursor = r[1];
      }
    }
    return cursor.isAfter(now) ? cursor : null;
  }

  /**
   * The learner's billing page: validity, status, renewal window and payment history. Status is
   * derived at read time (no stored flag to go stale): ACTIVE, EXPIRING_SOON (inside the renewal
   * window), EXPIRED (had paid or granted premium access that has ended) or NONE.
   */
  @Transactional(readOnly = true)
  BillingSummary summary(UUID userId) {
    Instant now = clock.instant();
    Instant validThrough = premiumCoverageEnd(userId, now);
    SubscriptionSummary subscription = liveSubscription(userId, validThrough);
    boolean autoRenewing = subscription != null && subscription.status().equals("active");
    String status;
    Long daysRemaining = null;
    boolean renewalDue = false;
    if (validThrough != null) {
      daysRemaining = (Duration.between(now, validThrough).getSeconds() + 86_399) / 86_400;
      // an actively auto-renewing subscription renews by itself: no "renew now" reminder
      renewalDue = !autoRenewing && daysRemaining <= renewalWindowDays;
      status = renewalDue ? "EXPIRING_SOON" : "ACTIVE";
    } else {
      boolean everHad =
          jdbc.sql(
                  """
                  select exists (select 1 from billing.access_periods ap
                    join billing.plan_entitlements pe on pe.plan_id = ap.plan_id
                    where ap.user_id = :u and pe.entitlement_code = 'premium_content'
                      and ap.revoked_at is null and ap.ends_at <= :now)
                  """)
              .param("u", userId)
              .param("now", Timestamp.from(now))
              .query(Boolean.class)
              .single();
      status = everHad ? "EXPIRED" : "NONE";
    }
    List<PaymentView> payments =
        jdbc.sql(
                """
                select pay.id, pl.name, pay.amount_minor, pay.currency, pay.status, pay.captured_at, d.doc_number,
                       (select rr.status from billing.refund_requests rr where rr.payment_id = pay.id order by rr.created_at desc limit 1),
                       (pay.status in ('captured','partially_refunded')
                        and not exists (select 1 from billing.refund_requests rr where rr.payment_id = pay.id and rr.status in ('requested','under_review','approved','processing','failed'))
                        and (select coalesce(sum(r.amount_minor), 0) from billing.refunds r where r.payment_id = pay.id and r.status in ('pending','processed')) < pay.amount_minor
                        and (select s.request_window_days is null or pay.captured_at + make_interval(days => s.request_window_days) > :now from billing.refund_settings s where s.id = 1))
                from billing.payments pay
                left join billing.purchase_intents i on i.id = pay.intent_id
                left join billing.subscriptions sub on sub.id = pay.subscription_id
                join billing.plans pl on pl.id = coalesce(i.plan_id, sub.plan_id)
                left join billing.documents d on d.payment_id = pay.id
                where pay.user_id = :u order by pay.captured_at desc, pay.id limit 50
                """)
            .param("now", now())
            .param("u", userId)
            .query(
                (rs, i) ->
                    new PaymentView(
                        rs.getObject(1, UUID.class),
                        rs.getString(2),
                        rs.getLong(3),
                        rs.getString(4).strip(),
                        rs.getString(5),
                        rs.getTimestamp(6).toInstant(),
                        rs.getString(7),
                        rs.getString(8),
                        rs.getBoolean(9)))
            .list();
    boolean documentsAvailable =
        jdbc.sql("select tax_mode <> 'UNCONFIGURED' from billing.tax_settings where id = 1")
            .query(Boolean.class)
            .single();
    return new BillingSummary(
        status,
        validThrough != null,
        validThrough,
        daysRemaining,
        renewalDue,
        renewalWindowDays,
        subscription,
        documentsAvailable,
        payments);
  }

  /** The newest subscription that is paid up, failing or scheduled to end; null when none. */
  private SubscriptionSummary liveSubscription(UUID userId, Instant validThrough) {
    return jdbc.sql(
            """
            select s.id, s.status, p.name, s.amount_minor, s.currency, s.period_unit
            from billing.subscriptions s join billing.plans p on p.id = s.plan_id
            where s.user_id = :u and s.status in ('active','past_due','cancel_scheduled')
            order by s.created_at desc limit 1
            """)
        .param("u", userId)
        .query(
            (rs, i) ->
                new SubscriptionSummary(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getLong(4),
                    rs.getString(5).strip(),
                    rs.getString(6),
                    "active".equals(rs.getString(2)) ? validThrough : null))
        .optional()
        .orElse(null);
  }
}
