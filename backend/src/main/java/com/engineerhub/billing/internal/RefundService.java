package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.RefundDtos.AdminRefundDetail;
import com.engineerhub.billing.internal.RefundDtos.AdminRefundRow;
import com.engineerhub.billing.internal.RefundDtos.MyRefundView;
import com.engineerhub.billing.internal.RefundDtos.RefundAttempt;
import com.engineerhub.billing.internal.RefundDtos.RefundFlag;
import com.engineerhub.billing.internal.RefundDtos.RefundPolicyView;
import com.engineerhub.billing.internal.RefundDtos.RefundSettingsRequest;
import com.engineerhub.billing.internal.RefundDtos.RefundSettingsView;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refund requests, review, processing and the credit note (docs/21 §7). The policy is the owner's
 * (P-6): nothing here decides who deserves a refund. Mechanism only: a user can always ask, an
 * admin decides (and a second admin when the owner sets an approval threshold), the provider call
 * happens outside any database transaction, and every transition is a guarded update that is
 * audited. A refund never exceeds what was paid (pending plus processed refunds are summed under
 * the request lock). Cancelling a subscription and refunding are independent actions.
 */
@Service
class RefundService {

  private static final Logger log = LoggerFactory.getLogger(RefundService.class);
  private static final int MAX_REQUESTS_PER_DAY = 10;
  private static final Duration NO_ACCESS_AFTER = Duration.ofMinutes(15);
  private static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final UserDirectory directory;
  private final AuditLog audit;
  private final OutboxPublisher events;
  private final PaymentProvider provider;
  private final DocumentService documents;
  private final TransactionTemplate tx;

  RefundService(
      JdbcClient jdbc,
      Clock clock,
      UserDirectory directory,
      AuditLog audit,
      OutboxPublisher events,
      PaymentProvider provider,
      DocumentService documents,
      TransactionTemplate tx) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.directory = directory;
    this.audit = audit;
    this.events = events;
    this.provider = provider;
    this.documents = documents;
    this.tx = tx;
  }

  private Timestamp now() {
    return Timestamp.from(clock.instant());
  }

  // ================================================================= settings and policy

  @Transactional(readOnly = true)
  RefundSettingsView settings() {
    return jdbc.sql(
            "select policy_text, request_window_days, revoke_access_on_refund, approval_threshold_minor, auto_detect_duplicates, updated_at from billing.refund_settings where id = 1")
        .query(
            (rs, i) ->
                new RefundSettingsView(
                    rs.getString(1),
                    rs.getObject(2, Integer.class),
                    rs.getBoolean(3),
                    rs.getObject(4, Long.class),
                    rs.getBoolean(5),
                    rs.getTimestamp(6).toInstant()))
        .single();
  }

  @Transactional
  RefundSettingsView updateSettings(Authentication actor, RefundSettingsRequest r) {
    String policy =
        r.policyText() == null || r.policyText().isBlank() ? null : r.policyText().strip();
    var before = settings();
    UUID admin = directory.current(actor).id();
    jdbc.sql(
            "update billing.refund_settings set policy_text = :p, request_window_days = :w, revoke_access_on_refund = :rv, approval_threshold_minor = :t, auto_detect_duplicates = :d, updated_at = :now, updated_by = :by where id = 1")
        .param("p", policy, java.sql.Types.VARCHAR)
        .param("w", r.requestWindowDays(), java.sql.Types.INTEGER)
        .param("rv", r.revokeAccessOnRefund())
        .param("t", r.approvalThresholdMinor(), java.sql.Types.BIGINT)
        .param("d", r.autoDetectDuplicates())
        .param("now", now())
        .param("by", admin)
        .update();
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.refund.settings.changed", "refund_settings", "1")
            .withChange(
                Map.of(
                    "windowDays", String.valueOf(before.requestWindowDays()),
                    "revokeAccess", before.revokeAccessOnRefund(),
                    "policyPublished", before.policyText() != null),
                Map.of(
                    "windowDays", String.valueOf(r.requestWindowDays()),
                    "revokeAccess", r.revokeAccessOnRefund(),
                    "policyPublished", policy != null)));
    return settings();
  }

  RefundPolicyView policy() {
    var s = settings();
    return new RefundPolicyView(s.policyText(), s.requestWindowDays());
  }

  // ================================================================= the user's side

  private long refundable(UUID paymentId) {
    return jdbc.sql(
            """
            select p.amount_minor - coalesce((select sum(r.amount_minor) from billing.refunds r where r.payment_id = p.id and r.status in ('pending','processed')), 0)
            from billing.payments p where p.id = :id
            """)
        .param("id", paymentId)
        .query(Long.class)
        .single();
  }

  @Transactional
  MyRefundView create(Authentication auth, UUID paymentId, String reason, String details) {
    UUID user = directory.current(auth).id();
    var pay =
        jdbc.sql("select user_id, status, captured_at from billing.payments where id = :id")
            .param("id", paymentId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant()
                    })
            .optional();
    if (pay.isEmpty() || !user.equals(pay.get()[0])) {
      throw ApiException.notFound(
          "Payment not found."); // someone else's looks exactly like missing
    }
    String status = (String) pay.get()[1];
    if (!status.equals("captured") && !status.equals("partially_refunded")) {
      throw ApiException.conflict("This payment has already been refunded.");
    }
    var s = settings();
    Instant now = clock.instant();
    if (s.requestWindowDays() != null
        && ((Instant) pay.get()[2]).plus(Duration.ofDays(s.requestWindowDays())).isBefore(now)) {
      throw ApiException.conflict("The window for requesting a refund of this payment has passed.");
    }
    if (refundable(paymentId) <= 0) {
      throw ApiException.conflict("Nothing is left to refund on this payment.");
    }
    long recent =
        jdbc.sql(
                "select count(*) from billing.refund_requests where user_id = :u and created_at > :since")
            .param("u", user)
            .param("since", Timestamp.from(now.minus(Duration.ofDays(1))))
            .query(Long.class)
            .single();
    if (recent >= MAX_REQUESTS_PER_DAY) {
      throw new ApiException(ErrorCode.RATE_LIMITED, "Too many requests. Try again later.");
    }
    UUID id = Ids.newId();
    try {
      jdbc.sql(
              "insert into billing.refund_requests (id, payment_id, user_id, reason, details, status, created_at, updated_at) values (:id, :p, :u, :r, :d, 'requested', :now, :now)")
          .param("id", id)
          .param("p", paymentId)
          .param("u", user)
          .param("r", reason)
          .param(
              "d",
              details == null || details.isBlank() ? null : details.strip(),
              java.sql.Types.VARCHAR)
          .param("now", now())
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("There is already an open refund request for this payment.");
    }
    events.publish(
        "billing.refund.requested",
        1,
        "user",
        user.toString(),
        Map.of("userId", user, "requestId", id));
    return mine(user).stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
  }

  /** Not a read-only transaction: resolving the caller may provision the local user. */
  List<MyRefundView> mine(Authentication auth) {
    return mine(directory.current(auth).id());
  }

  private List<MyRefundView> mine(UUID user) {
    return jdbc.sql(
            """
            select rr.id, rr.payment_id, pl.name, p.amount_minor, rr.reason, rr.status, rr.decision_note, rr.created_at,
                   (select cast(sum(r.amount_minor) as bigint) from billing.refunds r where r.request_id = rr.id and r.status = 'processed'),
                   (select r.id from billing.refunds r where r.request_id = rr.id and r.status = 'processed' order by r.created_at desc limit 1)
            from billing.refund_requests rr
            join billing.payments p on p.id = rr.payment_id
            left join billing.purchase_intents i on i.id = p.intent_id
            left join billing.subscriptions sub on sub.id = p.subscription_id
            join billing.plans pl on pl.id = coalesce(i.plan_id, sub.plan_id)
            where rr.user_id = :u order by rr.created_at desc limit 50
            """)
        .param("u", user)
        .query(
            (rs, i) -> {
              UUID refundId = rs.getObject(10, UUID.class);
              String note = rs.getString(7);
              String status = rs.getString(6);
              String credit =
                  refundId == null
                      ? null
                      : jdbc.sql("select doc_number from billing.credit_notes where refund_id = :r")
                          .param("r", refundId)
                          .query(String.class)
                          .optional()
                          .orElse(null);
              return new MyRefundView(
                  rs.getObject(1, UUID.class),
                  rs.getObject(2, UUID.class),
                  rs.getString(3),
                  rs.getLong(4),
                  rs.getString(5),
                  status,
                  status.equals("rejected") || status.equals("processed") ? note : null,
                  rs.getObject(9, Long.class),
                  refundId,
                  credit,
                  rs.getTimestamp(8).toInstant());
            })
        .list();
  }

  // ================================================================= the admin's side

  private static final String ROW_SQL =
      """
      select rr.id, rr.status, rr.reason, rr.user_id, pl.name, p.amount_minor, rr.created_at, p.id,
             p.amount_minor - coalesce((select sum(r.amount_minor) from billing.refunds r where r.payment_id = p.id and r.status in ('pending','processed')), 0)
      from billing.refund_requests rr
      join billing.payments p on p.id = rr.payment_id
      left join billing.purchase_intents i on i.id = p.intent_id
      left join billing.subscriptions sub on sub.id = p.subscription_id
      join billing.plans pl on pl.id = coalesce(i.plan_id, sub.plan_id)
      """;

  private AdminRefundRow rowOf(java.sql.ResultSet rs) throws java.sql.SQLException {
    UUID userId = rs.getObject(4, UUID.class);
    return new AdminRefundRow(
        rs.getObject(1, UUID.class),
        rs.getString(2),
        rs.getString(3),
        directory.findById(userId).map(u -> u.username()).orElse("unknown"),
        rs.getString(5),
        rs.getLong(6),
        rs.getLong(9),
        rs.getTimestamp(7).toInstant());
  }

  @Transactional(readOnly = true)
  List<AdminRefundRow> queue(String status) {
    if (status != null
        && !List.of(
                "requested",
                "under_review",
                "approved",
                "processing",
                "processed",
                "rejected",
                "failed")
            .contains(status)) {
      throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown status.");
    }
    return jdbc.sql(
            ROW_SQL
                + (status == null ? "" : " where rr.status = :s")
                + " order by rr.created_at desc limit 100")
        .params(status == null ? Map.of() : Map.of("s", status))
        .query((rs, i) -> rowOf(rs))
        .list();
  }

  @Transactional(readOnly = true)
  AdminRefundDetail detail(UUID id) {
    var found =
        jdbc.sql(ROW_SQL + " where rr.id = :id")
            .param("id", id)
            .query((rs, i) -> new Object[] {rowOf(rs), rs.getObject(8, UUID.class)})
            .optional()
            .orElseThrow(() -> ApiException.notFound("Refund request not found."));
    AdminRefundRow row = (AdminRefundRow) found[0];
    UUID paymentId = (UUID) found[1];
    var extra =
        jdbc.sql(
                "select rr.details, rr.decision_note, rr.approved_amount_minor, p.status, p.captured_at, rr.reviewer_id from billing.refund_requests rr join billing.payments p on p.id = rr.payment_id where rr.id = :id")
            .param("id", id)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getString(1),
                      rs.getString(2),
                      rs.getObject(3, Long.class),
                      rs.getString(4),
                      rs.getTimestamp(5).toInstant(),
                      rs.getObject(6, UUID.class)
                    })
            .single();
    List<RefundAttempt> attempts =
        jdbc.sql(
                "select id, amount_minor, status, created_at from billing.refunds where request_id = :id order by created_at")
            .param("id", id)
            .query(
                (rs, i) ->
                    new RefundAttempt(
                        rs.getObject(1, UUID.class),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toInstant()))
            .list();
    boolean hasDocument =
        jdbc.sql("select exists (select 1 from billing.documents where payment_id = :p)")
            .param("p", paymentId)
            .query(Boolean.class)
            .single();
    List<RefundFlag> flags = flags().stream().filter(f -> f.paymentId().equals(paymentId)).toList();
    return new AdminRefundDetail(
        row,
        (String) extra[0],
        (String) extra[1],
        (Long) extra[2],
        (String) extra[3],
        (Instant) extra[4],
        hasDocument,
        extra[5] != null,
        settings().approvalThresholdMinor(),
        attempts,
        flags);
  }

  /**
   * Starts the review: assigns the reviewer (the second pair of eyes is judged against this
   * person).
   */
  @Transactional
  AdminRefundDetail review(Authentication actor, UUID id) {
    UUID admin = directory.current(actor).id();
    int n =
        jdbc.sql(
                "update billing.refund_requests set status = 'under_review', reviewer_id = :a, updated_at = :now where id = :id and status = 'requested'")
            .param("a", admin)
            .param("now", now())
            .param("id", id)
            .update();
    if (n == 0) {
      exists(id);
      throw ApiException.conflict("Only a new request can be taken into review.");
    }
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.refund.review.started", "refund_request", id.toString()));
    return detail(id);
  }

  @Transactional
  AdminRefundDetail reject(Authentication actor, UUID id, String note) {
    UUID admin = directory.current(actor).id();
    int n =
        jdbc.sql(
                "update billing.refund_requests set status = 'rejected', decided_by = :a, decision_note = :n, updated_at = :now where id = :id and status in ('requested','under_review','failed')")
            .param("a", admin)
            .param("n", note.strip())
            .param("now", now())
            .param("id", id)
            .update();
    if (n == 0) {
      exists(id);
      throw ApiException.conflict("This request can no longer be rejected.");
    }
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.refund.rejected", "refund_request", id.toString())
            .withReason(note.strip()));
    UUID user = requestUser(id);
    events.publish(
        "billing.refund.rejected",
        1,
        "user",
        user.toString(),
        Map.of("userId", user, "requestId", id));
    return detail(id);
  }

  private void exists(UUID id) {
    if (requestUserOrNull(id) == null) {
      throw ApiException.notFound("Refund request not found.");
    }
  }

  private UUID requestUserOrNull(UUID id) {
    return jdbc.sql("select user_id from billing.refund_requests where id = :id")
        .param("id", id)
        .query(UUID.class)
        .optional()
        .orElse(null);
  }

  private UUID requestUser(UUID id) {
    return requestUserOrNull(id);
  }

  /** Approve and process: amount defaults to everything still refundable. */
  AdminRefundDetail approve(Authentication actor, UUID id, Long amount, String note) {
    return start(actor, id, amount, note, false);
  }

  /**
   * After a failed attempt. The admin confirms they checked the provider, because a retry can
   * refund twice.
   */
  AdminRefundDetail retry(Authentication actor, UUID id, boolean confirmed) {
    if (!confirmed) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "Confirm that the provider shows no refund for this payment before retrying.");
    }
    return start(actor, id, null, "Retry after a failed attempt", true);
  }

  private AdminRefundDetail start(
      Authentication actor, UUID id, Long amount, String note, boolean retry) {
    UUID admin = directory.current(actor).id();
    // 1) decide and record the pending refund (own transaction, request row locked)
    Object[] prepared =
        tx.execute(
            status -> {
              var r =
                  jdbc.sql(
                          "select status, payment_id, reviewer_id, approved_amount_minor from billing.refund_requests where id = :id for update")
                      .param("id", id)
                      .query(
                          (rs, i) ->
                              new Object[] {
                                rs.getString(1),
                                rs.getObject(2, UUID.class),
                                rs.getObject(3, UUID.class),
                                rs.getObject(4, Long.class)
                              })
                      .optional()
                      .orElseThrow(() -> ApiException.notFound("Refund request not found."));
              String st = (String) r[0];
              boolean allowed =
                  retry ? st.equals("failed") : st.equals("requested") || st.equals("under_review");
              if (!allowed) {
                throw ApiException.conflict(
                    retry
                        ? "Only a failed attempt can be retried."
                        : "This request cannot be approved now.");
              }
              UUID paymentId = (UUID) r[1];
              long remaining = refundable(paymentId);
              long wanted = retry ? (Long) r[3] : amount == null ? remaining : amount;
              if (wanted <= 0 || wanted > remaining) {
                throw new ApiException(
                    ErrorCode.VALIDATION_FAILED,
                    "The amount must be between 1 and what is still refundable.");
              }
              Long threshold = settings().approvalThresholdMinor();
              if (threshold != null
                  && wanted >= threshold
                  && (r[2] == null || admin.equals(r[2]))) {
                throw ApiException.conflict(
                    "This amount needs a second approver: someone other than the reviewer must approve it, after the review has been started.");
              }
              UUID refundId = Ids.newId();
              jdbc.sql(
                      "insert into billing.refunds (id, request_id, payment_id, amount_minor, provider, status, created_at, updated_at) values (:id, :req, :p, :a, :prov, 'pending', :now, :now)")
                  .param("id", refundId)
                  .param("req", id)
                  .param("p", paymentId)
                  .param("a", wanted)
                  .param("prov", provider.name())
                  .param("now", now())
                  .update();
              jdbc.sql(
                      "update billing.refund_requests set status = 'processing', decided_by = :a, decision_note = :n, approved_amount_minor = :amt, updated_at = :now where id = :id")
                  .param("a", admin)
                  .param("n", note.strip())
                  .param("amt", wanted)
                  .param("now", now())
                  .param("id", id)
                  .update();
              String paymentRef =
                  jdbc.sql("select provider_payment_ref from billing.payments where id = :id")
                      .param("id", paymentId)
                      .query(String.class)
                      .single();
              audit.record(
                  actor,
                  admin,
                  AuditEvent.of(
                          retry ? "billing.refund.retried" : "billing.refund.approved",
                          "refund_request",
                          id.toString())
                      .withChange(null, Map.of("amountMinor", wanted))
                      .withReason(note.strip()));
              return new Object[] {refundId, paymentRef, wanted};
            });
    UUID refundId = (UUID) prepared[0];

    // 2) the provider call, no transaction held
    PaymentProvider.ProviderRefund result;
    try {
      result =
          provider.refundPayment(
              (String) prepared[1], (Long) prepared[2], Map.of("refundId", refundId.toString()));
    } catch (RuntimeException e) {
      tx.executeWithoutResult(
          s -> {
            jdbc.sql(
                    "update billing.refunds set status = 'failed', updated_at = :now where id = :id and status = 'pending'")
                .param("now", now())
                .param("id", refundId)
                .update();
            jdbc.sql(
                    "update billing.refund_requests set status = 'failed', updated_at = :now where id = :id and status = 'processing'")
                .param("now", now())
                .param("id", id)
                .update();
          });
      throw e;
    }

    // 3) remember the provider's refund; finish now if it is already processed
    tx.executeWithoutResult(
        s -> {
          jdbc.sql(
                  "update billing.refunds set provider_refund_ref = :r, updated_at = :now where id = :id")
              .param("r", result.refundId())
              .param("now", now())
              .param("id", refundId)
              .update();
          if ("processed".equals(result.status())) {
            finalizeRefund(refundId);
          }
        });
    return detail(id);
  }

  // ================================================================= finishing a refund

  /** Idempotent: only a pending refund is finished. Runs inside the caller's transaction. */
  boolean finalizeRefund(UUID refundId) {
    var r =
        jdbc.sql(
                "select payment_id, request_id, amount_minor, status from billing.refunds where id = :id for update")
            .param("id", refundId)
            .query(
                (rs, i) ->
                    new Object[] {
                      rs.getObject(1, UUID.class),
                      rs.getObject(2, UUID.class),
                      rs.getLong(3),
                      rs.getString(4)
                    })
            .optional();
    if (r.isEmpty() || !"pending".equals(r.get()[3])) {
      return false;
    }
    UUID paymentId = (UUID) r.get()[0];
    UUID requestId = (UUID) r.get()[1];
    jdbc.sql("update billing.refunds set status = 'processed', updated_at = :now where id = :id")
        .param("now", now())
        .param("id", refundId)
        .update();
    long total =
        jdbc.sql("select amount_minor from billing.payments where id = :id")
            .param("id", paymentId)
            .query(Long.class)
            .single();
    long refunded =
        jdbc.sql(
                "select coalesce(sum(amount_minor), 0) from billing.refunds where payment_id = :p and status = 'processed'")
            .param("p", paymentId)
            .query(Long.class)
            .single();
    boolean full = refunded >= total;
    jdbc.sql("update billing.payments set status = :s where id = :id")
        .param("s", full ? "refunded" : "partially_refunded")
        .param("id", paymentId)
        .update();
    var req =
        jdbc.sql("select user_id, decided_by from billing.refund_requests where id = :id")
            .param("id", requestId)
            .query(
                (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)})
            .single();
    jdbc.sql(
            "update billing.refund_requests set status = 'processed', updated_at = :now where id = :id and status in ('processing','failed')")
        .param("now", now())
        .param("id", requestId)
        .update();
    if (full && settings().revokeAccessOnRefund()) {
      // the access this payment bought ends with it (full refunds only; the owner can switch this
      // off)
      jdbc.sql(
              "update billing.access_periods set revoked_at = :now, revoked_by = :by, revoked_reason = 'Payment refunded in full' where source_type = 'payment' and source_ref = :ref and revoked_at is null")
          .param("now", now())
          .param("by", req[1])
          .param("ref", paymentId.toString())
          .update();
    }
    documents.issueCreditNote(refundId);
    UUID user = (UUID) req[0];
    events.publish(
        "billing.refund.processed",
        1,
        "user",
        user.toString(),
        Map.of(
            "userId",
            user,
            "paymentId",
            paymentId,
            "refundId",
            refundId,
            "amountMinor",
            r.get()[2]));
    return true;
  }

  /** refund.processed / refund.failed from the provider's webhook. */
  @Transactional
  String providerEvent(String type, String providerRefundRef) {
    var r =
        jdbc.sql(
                "select id, request_id from billing.refunds where provider = :p and provider_refund_ref = :r for update")
            .param("p", provider.name())
            .param("r", providerRefundRef)
            .query(
                (rs, i) -> new Object[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)})
            .optional();
    if (r.isEmpty()) {
      log.warn("Refund event for an unknown refund ignored");
      return "unknown_refund";
    }
    UUID refundId = (UUID) r.get()[0];
    if (type.equals("refund.processed")) {
      return finalizeRefund(refundId) ? "applied" : "ignored_no_transition";
    }
    int n =
        jdbc.sql(
                "update billing.refunds set status = 'failed', updated_at = :now where id = :id and status = 'pending'")
            .param("now", now())
            .param("id", refundId)
            .update();
    if (n > 0) {
      jdbc.sql(
              "update billing.refund_requests set status = 'failed', updated_at = :now where id = :id and status = 'processing'")
          .param("now", now())
          .param("id", r.get()[1])
          .update();
    }
    return n > 0 ? "applied" : "ignored_no_transition";
  }

  // ================================================================= automatic flags

  /**
   * Flags, not decisions: payments an admin should look at. A payment taken twice for the same plan
   * within a day, and money taken with no access granted a few minutes later. Both suggest an
   * action and neither refunds anything by itself.
   */
  @Transactional(readOnly = true)
  List<RefundFlag> flags() {
    Instant now = clock.instant();
    String planOf = "coalesce(i.plan_id, sub.plan_id)";
    String from =
        " from billing.payments p left join billing.purchase_intents i on i.id = p.intent_id left join billing.subscriptions sub on sub.id = p.subscription_id join billing.plans pl on pl.id = coalesce(i.plan_id, sub.plan_id) ";
    List<RefundFlag> out = new java.util.ArrayList<>();
    if (settings().autoDetectDuplicates()) {
      out.addAll(
          jdbc.sql(
                  "select p.id, p.user_id, pl.name, p.amount_minor, p.captured_at"
                      + from
                      + "where p.status = 'captured' and exists (select 1 from billing.payments q left join billing.purchase_intents qi on qi.id = q.intent_id left join billing.subscriptions qs on qs.id = q.subscription_id where q.id <> p.id and q.user_id = p.user_id and q.status = 'captured' and coalesce(qi.plan_id, qs.plan_id) = "
                      + planOf
                      + " and abs(extract(epoch from (q.captured_at - p.captured_at))) < :win) order by p.captured_at desc limit 100")
              .param("win", DUPLICATE_WINDOW.toSeconds())
              .query(
                  (rs, n) ->
                      flag(
                          rs,
                          "duplicate_capture",
                          "Taken more than once for the same plan within a day. Verify, then refund the later payment if it is a duplicate."))
              .list());
    }
    out.addAll(
        jdbc.sql(
                "select p.id, p.user_id, pl.name, p.amount_minor, p.captured_at"
                    + from
                    + "where p.status = 'captured' and p.captured_at < :cutoff and not exists (select 1 from billing.access_periods ap where ap.source_type = 'payment' and ap.source_ref = p.id::text) order by p.captured_at desc limit 100")
            .param("cutoff", Timestamp.from(now.minus(NO_ACCESS_AFTER)))
            .query(
                (rs, n) ->
                    flag(
                        rs,
                        "no_access_granted",
                        "Money was taken but no access exists for it. Investigate: activate the access or refund."))
            .list());
    return out;
  }

  private RefundFlag flag(java.sql.ResultSet rs, String kind, String suggestion)
      throws java.sql.SQLException {
    UUID userId = rs.getObject(2, UUID.class);
    return new RefundFlag(
        rs.getObject(1, UUID.class),
        directory.findById(userId).map(u -> u.username()).orElse("unknown"),
        rs.getString(3),
        rs.getLong(4),
        rs.getTimestamp(5).toInstant(),
        kind,
        suggestion);
  }
}
