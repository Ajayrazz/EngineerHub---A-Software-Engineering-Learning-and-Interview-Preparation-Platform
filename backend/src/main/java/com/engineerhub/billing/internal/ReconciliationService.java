package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.PaymentProvider.Listing;
import com.engineerhub.billing.internal.PaymentProvider.ProviderPayment;
import com.engineerhub.billing.internal.PaymentProvider.ProviderRefundRow;
import com.engineerhub.billing.internal.Reconciler.Finding;
import com.engineerhub.billing.internal.Reconciler.LocalPayment;
import com.engineerhub.billing.internal.Reconciler.LocalRefund;
import com.engineerhub.billing.internal.ReconciliationDtos.FindingView;
import com.engineerhub.billing.internal.ReconciliationDtos.ReconciliationOverview;
import com.engineerhub.billing.internal.ReconciliationDtos.ReconciliationRunView;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.id.Ids;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reconciliation against the provider (docs/21 §6.7) and the housekeeping that belongs with it:
 * stale purchase intents and abandoned subscription checkouts are expired here. A run never changes
 * money records by itself: drift becomes a finding, and only an admin can apply the two safe fixes
 * (record a payment whose webhook was lost, finish a refund the provider already processed) or
 * acknowledge a finding after checking it. Provider calls happen outside database transactions.
 */
@Service
class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  static final Duration WINDOW = Duration.ofHours(48);
  static final int MAX_ITEMS = 2000;
  private static final Duration ABANDONED_SUBSCRIPTION = Duration.ofHours(24);

  private final JdbcClient jdbc;
  private final Clock clock;
  private final PaymentProvider provider;
  private final PaymentCapturedHandler handler;
  private final RefundService refunds;
  private final TransactionTemplate tx;
  private final AuditLog audit;
  private final UserDirectory directory;

  ReconciliationService(
      JdbcClient jdbc,
      Clock clock,
      PaymentProvider provider,
      PaymentCapturedHandler handler,
      RefundService refunds,
      TransactionTemplate tx,
      AuditLog audit,
      UserDirectory directory) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.provider = provider;
    this.handler = handler;
    this.refunds = refunds;
    this.tx = tx;
    this.audit = audit;
    this.directory = directory;
  }

  private Timestamp ts(Instant i) {
    return Timestamp.from(i);
  }

  // ================================================================= running

  ReconciliationRunView run(String trigger) {
    Instant now = clock.instant();
    Instant from = now.minus(WINDOW);
    UUID id = Ids.newId();
    // a crashed run must not block the next one forever
    jdbc.sql(
            "update billing.reconciliation_runs set status = 'error', finished_at = :now, note = 'Abandoned: the run did not finish.' where status = 'running' and started_at < :old")
        .param("now", ts(now))
        .param("old", ts(now.minus(Duration.ofHours(1))))
        .update();
    try {
      jdbc.sql(
              "insert into billing.reconciliation_runs (id, trigger, started_at, window_from, window_to, status) values (:id, :t, :now, :from, :to, 'running')")
          .param("id", id)
          .param("t", trigger)
          .param("now", ts(now))
          .param("from", ts(from))
          .param("to", ts(now))
          .update();
    } catch (DuplicateKeyException e) {
      throw ApiException.conflict("A reconciliation run is already in progress.");
    }
    if (!provider.configured()) {
      return finish(
          id,
          "skipped",
          0,
          0,
          0,
          expireStale(now),
          "Payments are not configured: nothing to compare.");
    }
    Listing<ProviderPayment> payments;
    Listing<ProviderRefundRow> refundRows;
    try {
      payments = provider.listPayments(from, now, MAX_ITEMS);
      refundRows = provider.listRefunds(from, now, MAX_ITEMS);
    } catch (RuntimeException e) {
      log.warn("Reconciliation could not read the provider: {}", e.getClass().getSimpleName());
      return finish(
          id, "error", 0, 0, 0, expireStale(now), "The payment provider could not be reached.");
    }
    try {
      Object[] r =
          tx.execute(
              s -> {
                Map<String, LocalPayment> local =
                    localPayments(from.minus(Duration.ofDays(1)), now.plus(Duration.ofDays(1)));
                Set<String> orders = knownOrders(payments.items());
                Map<String, LocalRefund> localRefunds = localRefunds(refundRows.items());
                List<Finding> found =
                    Reconciler.compare(
                        payments.items(),
                        payments.complete(),
                        refundRows.items(),
                        local,
                        orders,
                        localRefunds,
                        from,
                        now,
                        now);
                int added = 0;
                for (Finding f : found) {
                  added += insertFinding(id, f, now);
                }
                return new Object[] {found.size(), added, local.size()};
              });
      int found = (Integer) r[0];
      int added = (Integer) r[1];
      String status =
          found > 0 ? "drift" : payments.complete() && refundRows.complete() ? "ok" : "partial";
      String note =
          found > 0
              ? "%d finding(s), %d new.".formatted(found, added)
              : status.equals("partial")
                  ? "The provider listing was cut off at %d items: absences were not checked."
                      .formatted(MAX_ITEMS)
                  : null;
      return finish(
          id, status, payments.items().size(), (Integer) r[2], added, expireStale(now), note);
    } catch (RuntimeException e) {
      log.error("Reconciliation failed", e);
      return finish(
          id,
          "error",
          payments.items().size(),
          0,
          0,
          0,
          "The comparison failed; see the server log.");
    }
  }

  private ReconciliationRunView finish(
      UUID id,
      String status,
      int providerCount,
      int localCount,
      int added,
      int expired,
      String note) {
    jdbc.sql(
            "update billing.reconciliation_runs set status = :s, finished_at = :now, provider_payments = :pp, local_payments = :lp, findings_new = :n, intents_expired = :e, note = :note where id = :id")
        .param("s", status)
        .param("now", ts(clock.instant()))
        .param("pp", providerCount)
        .param("lp", localCount)
        .param("n", added)
        .param("e", expired)
        .param("note", note, java.sql.Types.VARCHAR)
        .param("id", id)
        .update();
    return runView(id);
  }

  /** Purchases nobody completed and subscription checkouts nobody finished stop being "open". */
  private int expireStale(Instant now) {
    return tx.execute(
        s -> {
          int intents =
              jdbc.sql(
                      "update billing.purchase_intents set status = 'expired', updated_at = :now where status in ('created','payment_pending') and expires_at < :now")
                  .param("now", ts(now))
                  .update();
          jdbc.sql(
                  "update billing.subscriptions set status = 'failed', updated_at = :now where status in ('created','pending') and updated_at < :old and not exists (select 1 from billing.payments p where p.subscription_id = billing.subscriptions.id)")
              .param("now", ts(now))
              .param("old", ts(now.minus(ABANDONED_SUBSCRIPTION)))
              .update();
          return intents;
        });
  }

  private Map<String, LocalPayment> localPayments(Instant from, Instant to) {
    Map<String, LocalPayment> out = new HashMap<>();
    jdbc.sql(
            """
            select p.id, p.provider_payment_ref, p.amount_minor, p.currency, p.status, p.captured_at,
                   coalesce((select sum(r.amount_minor) from billing.refunds r where r.payment_id = p.id and r.status = 'processed'), 0),
                   coalesce((select sum(r.amount_minor) from billing.refunds r where r.payment_id = p.id and r.status = 'pending'), 0)
            from billing.payments p where p.provider = :prov and p.captured_at between :from and :to
            """)
        .param("prov", provider.name())
        .param("from", ts(from))
        .param("to", ts(to))
        .query(
            (rs, i) ->
                new LocalPayment(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getLong(3),
                    rs.getString(4).strip(),
                    rs.getString(5),
                    rs.getLong(7),
                    rs.getLong(8),
                    rs.getTimestamp(6).toInstant()))
        .list()
        .forEach(p -> out.put(p.providerRef(), p));
    return out;
  }

  private Set<String> knownOrders(List<ProviderPayment> payments) {
    Set<String> ids = new HashSet<>();
    for (ProviderPayment p : payments) {
      if (p.orderId() != null && !p.orderId().isBlank()) {
        ids.add(p.orderId());
      }
    }
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql(
                "select provider_order_ref from billing.purchase_intents where provider = :prov and provider_order_ref in (:ids)")
            .param("prov", provider.name())
            .param("ids", ids)
            .query(String.class)
            .list());
  }

  private Map<String, LocalRefund> localRefunds(List<ProviderRefundRow> rows) {
    Set<String> ids = new HashSet<>();
    for (ProviderRefundRow r : rows) {
      ids.add(r.id());
    }
    Map<String, LocalRefund> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    jdbc.sql(
            "select id, provider_refund_ref, status, updated_at from billing.refunds where provider = :prov and provider_refund_ref in (:ids)")
        .param("prov", provider.name())
        .param("ids", ids)
        .query(
            (rs, i) ->
                new LocalRefund(
                    rs.getObject(1, UUID.class),
                    rs.getString(2),
                    rs.getString(3),
                    rs.getTimestamp(4).toInstant()))
        .list()
        .forEach(r -> out.put(r.providerRef(), r));
    return out;
  }

  private int insertFinding(UUID runId, Finding f, Instant now) {
    return jdbc.sql(
            """
            insert into billing.reconciliation_findings (id, run_id, kind, provider_ref, order_ref, local_payment_id, local_refund_id, amount_minor, currency, detail, created_at)
            values (:id, :run, :kind, :ref, :order, :lp, :lr, :amt, :cur, :detail, :now)
            on conflict (kind, provider_ref) where status = 'open' do nothing
            """)
        .param("id", Ids.newId())
        .param("run", runId)
        .param("kind", f.kind())
        .param("ref", f.providerRef())
        .param("order", f.orderRef(), java.sql.Types.VARCHAR)
        .param("lp", f.localPaymentId(), java.sql.Types.OTHER)
        .param("lr", f.localRefundId(), java.sql.Types.OTHER)
        .param("amt", f.amountMinor(), java.sql.Types.BIGINT)
        .param("cur", f.currency(), java.sql.Types.CHAR)
        .param("detail", f.detail())
        .param("now", ts(now))
        .update();
  }

  // ================================================================= reading

  private ReconciliationRunView runView(UUID id) {
    return jdbc.sql(
            "select id, trigger, started_at, finished_at, status, provider_payments, local_payments, findings_new, intents_expired, note from billing.reconciliation_runs where id = :id")
        .param("id", id)
        .query(this::mapRun)
        .single();
  }

  private ReconciliationRunView mapRun(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
    return new ReconciliationRunView(
        rs.getObject(1, UUID.class),
        rs.getString(2),
        rs.getTimestamp(3).toInstant(),
        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant(),
        rs.getString(5),
        rs.getInt(6),
        rs.getInt(7),
        rs.getInt(8),
        rs.getInt(9),
        rs.getString(10));
  }

  List<ReconciliationRunView> runs() {
    return jdbc.sql(
            "select id, trigger, started_at, finished_at, status, provider_payments, local_payments, findings_new, intents_expired, note from billing.reconciliation_runs order by started_at desc limit 10")
        .query(this::mapRun)
        .list();
  }

  List<FindingView> openFindings() {
    return jdbc.sql(
            "select id, kind, provider_ref, order_ref, amount_minor, currency, detail, created_at from billing.reconciliation_findings where status = 'open' order by created_at desc limit 200")
        .query(
            (rs, i) -> {
              String kind = rs.getString(2);
              return new FindingView(
                  rs.getObject(1, UUID.class),
                  kind,
                  rs.getString(3),
                  rs.getString(4),
                  rs.getObject(5, Long.class),
                  rs.getString(6) == null ? null : rs.getString(6).strip(),
                  rs.getString(7),
                  kind.equals("missing_payment") || kind.equals("refund_stuck"),
                  rs.getTimestamp(8).toInstant());
            })
        .list();
  }

  ReconciliationOverview overview() {
    return new ReconciliationOverview(runs(), openFindings());
  }

  // ================================================================= acting on findings

  private Object[] open(UUID id) {
    return jdbc.sql(
            "select kind, provider_ref, order_ref, amount_minor, currency, local_refund_id from billing.reconciliation_findings where id = :id and status = 'open'")
        .param("id", id)
        .query(
            (rs, i) ->
                new Object[] {
                  rs.getString(1),
                  rs.getString(2),
                  rs.getString(3),
                  rs.getObject(4, Long.class),
                  rs.getString(5) == null ? null : rs.getString(5).strip(),
                  rs.getObject(6, UUID.class)
                })
        .optional()
        .orElseThrow(() -> ApiException.notFound("No open finding with this id."));
  }

  private void resolve(Authentication actor, UUID id, String how, String note) {
    UUID admin = directory.current(actor).id();
    int n =
        jdbc.sql(
                "update billing.reconciliation_findings set status = 'resolved', resolved_at = :now, resolved_by = :by, resolution = :how, resolution_note = :note where id = :id and status = 'open'")
            .param("now", ts(clock.instant()))
            .param("by", admin)
            .param("how", how)
            .param("note", note)
            .param("id", id)
            .update();
    if (n == 0) {
      throw ApiException.conflict("This finding was already resolved.");
    }
    audit.record(
        actor,
        admin,
        AuditEvent.of(
                "billing.reconciliation.finding." + how, "reconciliation_finding", id.toString())
            .withReason(note));
  }

  void acknowledge(Authentication actor, UUID id, String note) {
    open(id);
    tx.executeWithoutResult(s -> resolve(actor, id, "acknowledged", note.strip()));
  }

  /**
   * Applies the one safe fix a finding allows; anything else must be acknowledged after a human
   * check.
   */
  void apply(Authentication actor, UUID id) {
    Object[] f = open(id);
    String kind = (String) f[0];
    if (kind.equals("missing_payment")) {
      String outcome =
          handler.handle(
              new PaymentCapturedHandler.Captured(
                  provider.name(), (String) f[1], (String) f[2], (Long) f[3], (String) f[4]));
      if (!outcome.equals("granted") && !outcome.equals("duplicate")) {
        throw ApiException.conflict(
            "The payment could not be applied ("
                + outcome
                + "). Check it and acknowledge the finding.");
      }
      tx.executeWithoutResult(
          s ->
              resolve(
                  actor,
                  id,
                  "applied",
                  "Recorded the payment the provider captured (" + outcome + ")."));
    } else if (kind.equals("refund_stuck")) {
      tx.executeWithoutResult(
          s -> {
            refunds.finalizeRefund((UUID) f[5]);
            resolve(
                actor, id, "applied", "Finished the refund the provider had already processed.");
          });
    } else {
      throw ApiException.conflict(
          "This finding cannot be applied automatically: check it, then acknowledge it with a note.");
    }
  }
}
