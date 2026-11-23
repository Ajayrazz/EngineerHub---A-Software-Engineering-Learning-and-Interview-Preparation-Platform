package com.engineerhub.billing.internal;

import com.engineerhub.shared.id.Ids;
import com.engineerhub.shared.messaging.OutboxPublisher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * THE single place where a captured payment becomes access (docs/21 §2). Called only for provider
 * events whose signature was verified. Steps: find the intent by the provider order, ignore a
 * payment already recorded, validate amount and currency against the intent's price snapshot (a
 * mismatch flags the intent for review and grants nothing), record the payment, append an immutable
 * access period, emit events.
 *
 * <p>Overlap rule (Pending Decision P-1, proposed default): a pass bought while one is running
 * starts when the latest running period ends ("extend"). Month/year are calendar months/years in
 * UTC.
 */
@Service
class PaymentCapturedHandler {

  record Captured(
      String provider, String paymentRef, String orderRef, long amountMinor, String currency) {}

  private static final Logger log = LoggerFactory.getLogger(PaymentCapturedHandler.class);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final OutboxPublisher events;

  PaymentCapturedHandler(JdbcClient jdbc, Clock clock, OutboxPublisher events) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.events = events;
  }

  /**
   * @return what happened: granted, duplicate, unknown_order, amount_mismatch, not_pending
   */
  @Transactional
  String handle(Captured c) {
    var intent =
        jdbc.sql(
                "select id, user_id, plan_id, period_unit, currency, amount_minor, status from billing.purchase_intents where provider = :p and provider_order_ref = :o for update")
            .param("p", c.provider())
            .param("o", c.orderRef())
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getObject(2, UUID.class),
                      rs.getObject(3, UUID.class),
                      rs.getString(4),
                      rs.getString(5),
                      rs.getLong(6),
                      rs.getString(7)
                    })
            .optional();
    if (intent.isEmpty()) {
      log.warn("Captured payment for an unknown order ignored");
      return "unknown_order";
    }
    Object[] i = intent.get();
    UUID intentId = (UUID) i[0];
    UUID userId = (UUID) i[1];
    String status = (String) i[6];

    boolean known =
        jdbc.sql(
                    "select count(*) from billing.payments where provider = :p and provider_payment_ref = :r")
                .param("p", c.provider())
                .param("r", c.paymentRef())
                .query(Long.class)
                .single()
            > 0;
    if (known) {
      return "duplicate"; // order.paid after payment.captured, redelivery, replay
    }
    if (!c.currency().equals(i[4]) || c.amountMinor() != (Long) i[5]) {
      jdbc.sql(
              "update billing.purchase_intents set status = 'needs_review', updated_at = :now where id = :id")
          .param("now", Timestamp.from(clock.instant()))
          .param("id", intentId)
          .update();
      log.warn(
          "Captured payment amount/currency does not match intent {}; flagged for review, no access granted",
          intentId);
      return "amount_mismatch";
    }
    if (status.equals("paid")) {
      return "duplicate"; // a second, different payment for an already paid intent is an anomaly
      // handled by review (refund tooling, M6.6)
    }

    Instant now = clock.instant();
    UUID paymentId = Ids.newId();
    int inserted =
        jdbc.sql(
                "insert into billing.payments (id, intent_id, user_id, provider, provider_payment_ref, amount_minor, currency, status, captured_at, created_at) values (:id, :i, :u, :p, :r, :a, :c, 'captured', :now, :now) on conflict (provider, provider_payment_ref) do nothing")
            .param("id", paymentId)
            .param("i", intentId)
            .param("u", userId)
            .param("p", c.provider())
            .param("r", c.paymentRef())
            .param("a", c.amountMinor())
            .param("c", c.currency())
            .param("now", Timestamp.from(now))
            .update();
    if (inserted == 0) {
      return "duplicate"; // lost a race with a concurrent delivery of the same payment (no aborted
      // transaction)
    }

    Instant latestEnd =
        jdbc.sql(
                "select max(ap.ends_at) from billing.access_periods ap join billing.plan_entitlements pe on pe.plan_id = ap.plan_id where ap.user_id = :u and pe.entitlement_code = 'premium_content' and ap.revoked_at is null and ap.ends_at > :now")
            .param("u", userId)
            .param("now", Timestamp.from(now))
            .query(Timestamp.class)
            .optional()
            .map(Timestamp::toInstant)
            .orElse(null);
    Instant start = latestEnd != null && latestEnd.isAfter(now) ? latestEnd : now;
    var z = start.atZone(ZoneOffset.UTC);
    Instant end = ("year".equals(i[3]) ? z.plusYears(1) : z.plusMonths(1)).toInstant();
    UUID periodId = Ids.newId();
    jdbc.sql(
            "insert into billing.access_periods (id, user_id, plan_id, starts_at, ends_at, source_type, source_ref, created_at) values (:id, :u, :pl, :s, :e, 'payment', :ref, :now)")
        .param("id", periodId)
        .param("u", userId)
        .param("pl", i[2])
        .param("s", Timestamp.from(start))
        .param("e", Timestamp.from(end))
        .param("ref", paymentId.toString())
        .param("now", Timestamp.from(now))
        .update();
    jdbc.sql(
            "update billing.purchase_intents set status = 'paid', updated_at = :now where id = :id")
        .param("now", Timestamp.from(now))
        .param("id", intentId)
        .update();

    events.publish(
        "billing.payment.succeeded",
        1,
        "user",
        userId.toString(),
        Map.of(
            "userId",
            userId,
            "paymentId",
            paymentId,
            "intentId",
            intentId,
            "amountMinor",
            c.amountMinor(),
            "currency",
            c.currency()));
    events.publish(
        "billing.access.granted",
        1,
        "user",
        userId.toString(),
        Map.of("userId", userId, "periodId", periodId, "endsAt", end.toString()));
    return "granted";
  }

  /** payment.failed for a still-open intent: no access, the user can simply try again. */
  @Transactional
  String failed(String provider, String orderRef) {
    int n =
        jdbc.sql(
                "update billing.purchase_intents set status = 'failed', updated_at = :now where provider = :p and provider_order_ref = :o and status in ('created','payment_pending')")
            .param("now", Timestamp.from(clock.instant()))
            .param("p", provider)
            .param("o", orderRef)
            .update();
    return n > 0 ? "failed_recorded" : "ignored";
  }
}
