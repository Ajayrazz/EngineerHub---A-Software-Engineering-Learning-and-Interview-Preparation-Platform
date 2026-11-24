package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.ReconciliationDtos.GateItemView;
import com.engineerhub.billing.internal.ReconciliationDtos.LiveGateView;
import com.engineerhub.billing.internal.ReconciliationDtos.MachineCheck;
import com.engineerhub.identity.UserDirectory;
import com.engineerhub.shared.audit.AuditEvent;
import com.engineerhub.shared.audit.AuditLog;
import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Live Readiness Gate (docs/21 §9): the owner's recorded attestations that real money may be
 * taken. The system cannot verify a legal entity or a KYC approval, so those are attestations by a
 * SUPER_ADMIN; only the items the system CAN check have a machine prerequisite (tax mode chosen,
 * refund policy written, seller details present). Un-ticking anything switches the gate off at
 * once.
 *
 * <p>The gate does NOT enable live payments: the application refuses live mode at startup whatever
 * is recorded here (ADR-055). It is the precondition a future, deliberate change must check.
 */
@Service
class LiveGateService {

  static final Map<String, String> ITEMS = new LinkedHashMap<>();

  static {
    ITEMS.put("legal_entity", "Legal entity finalised");
    ITEMS.put("razorpay_kyc", "Razorpay KYC / live account approved");
    ITEMS.put("bank_details", "Bank details recorded with the provider");
    ITEMS.put("tax_mode_confirmed", "Tax mode confirmed by the accountant (P-4)");
    ITEMS.put("policies_published", "Refund and cancellation policy, Terms and Privacy published");
    ITEMS.put("live_keys_installed", "Live keys and webhook secret installed");
    ITEMS.put("webhook_verified_live", "Webhook endpoint verified in live");
    ITEMS.put("seller_details_real", "Invoice seller details entered (real data only)");
    ITEMS.put("test_checklist_signed", "Test-mode end-to-end checklist signed off");
  }

  private final JdbcClient jdbc;
  private final Clock clock;
  private final AuditLog audit;
  private final UserDirectory directory;

  LiveGateService(JdbcClient jdbc, Clock clock, AuditLog audit, UserDirectory directory) {
    this.jdbc = jdbc;
    this.clock = clock;
    this.audit = audit;
    this.directory = directory;
  }

  /** What the system itself can check for an item; null when it is purely an attestation. */
  private MachineCheck check(String code) {
    switch (code) {
      case "tax_mode_confirmed":
        boolean tax =
            jdbc.sql("select tax_mode <> 'UNCONFIGURED' from billing.tax_settings where id = 1")
                .query(Boolean.class)
                .single();
        return new MachineCheck(
            tax, tax ? "A tax mode is chosen." : "The tax mode is still unconfigured.");
      case "policies_published":
        boolean policy =
            jdbc.sql("select policy_text is not null from billing.refund_settings where id = 1")
                .query(Boolean.class)
                .single();
        return new MachineCheck(
            policy,
            policy
                ? "A refund policy is published (Terms and Privacy are not tracked here: confirm them yourself)."
                : "No refund policy is published yet.");
      case "seller_details_real":
        boolean seller =
            jdbc.sql(
                    "select seller_legal_name is not null and seller_address is not null from billing.tax_settings where id = 1")
                .query(Boolean.class)
                .single();
        return new MachineCheck(
            seller,
            seller
                ? "Seller name and address are present (that they are real is your attestation)."
                : "Seller legal name and address are missing.");
      default:
        return null;
    }
  }

  @Transactional(readOnly = true)
  LiveGateView view() {
    List<GateItemView> items = new ArrayList<>();
    boolean all = true;
    for (var e : ITEMS.entrySet()) {
      var row =
          jdbc.sql(
                  "select done, note, confirmed_at from billing.live_readiness where item_code = :c")
              .param("c", e.getKey())
              .query(
                  (rs, i) ->
                      new Object[] {
                        rs.getBoolean(1),
                        rs.getString(2),
                        rs.getTimestamp(3) == null ? null : rs.getTimestamp(3).toInstant()
                      })
              .single();
      boolean done = (Boolean) row[0];
      all &= done;
      items.add(
          new GateItemView(
              e.getKey(),
              e.getValue(),
              done,
              (String) row[1],
              (java.time.Instant) row[2],
              check(e.getKey())));
    }
    var gate =
        jdbc.sql("select live_ready, changed_at from billing.live_gate where id = 1")
            .query((rs, i) -> new Object[] {rs.getBoolean(1), rs.getTimestamp(2).toInstant()})
            .single();
    return new LiveGateView(
        (Boolean) gate[0],
        (java.time.Instant) gate[1],
        all,
        items,
        "This build refuses live payments at startup whatever is recorded here. The gate is the recorded precondition for ever lifting that, not the switch.");
  }

  @Transactional
  LiveGateView setItem(Authentication actor, String code, boolean done, String note) {
    if (!ITEMS.containsKey(code)) {
      throw ApiException.notFound("Unknown checklist item.");
    }
    if (done) {
      MachineCheck c = check(code);
      if (c != null && !c.ok()) {
        throw ApiException.conflict("This cannot be ticked yet: " + c.message());
      }
    }
    var admin = directory.current(actor).id();
    jdbc.sql(
            "update billing.live_readiness set done = :d, note = :n, confirmed_by = :by, confirmed_at = :now where item_code = :c")
        .param("d", done)
        .param("n", note == null || note.isBlank() ? null : note.strip(), java.sql.Types.VARCHAR)
        .param("by", admin)
        .param("now", Timestamp.from(clock.instant()))
        .param("c", code)
        .update();
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.live.item.changed", "live_gate", code)
            .withChange(null, Map.of("done", done)));
    if (!done) {
      switchOff(actor, admin, "item un-ticked: " + code);
    }
    return view();
  }

  private void switchOff(Authentication actor, java.util.UUID admin, String why) {
    int n =
        jdbc.sql(
                "update billing.live_gate set live_ready = false, changed_at = :now, changed_by = :by where id = 1 and live_ready")
            .param("now", Timestamp.from(clock.instant()))
            .param("by", admin)
            .update();
    if (n > 0) {
      audit.record(
          actor,
          admin,
          AuditEvent.of("billing.live.ready.changed", "live_gate", "1")
              .withChange(Map.of("liveReady", true), Map.of("liveReady", false))
              .withReason(why));
    }
  }

  @Transactional
  LiveGateView setReady(Authentication actor, boolean ready) {
    var admin = directory.current(actor).id();
    if (!ready) {
      switchOff(actor, admin, "switched off by an administrator");
      return view();
    }
    LiveGateView v = view();
    List<String> missing = new ArrayList<>();
    for (GateItemView i : v.items()) {
      if (!i.done()) {
        missing.add(i.label());
      } else if (i.check() != null && !i.check().ok()) {
        missing.add(i.label() + " (" + i.check().message() + ")");
      }
    }
    if (!missing.isEmpty()) {
      throw new ApiException(
          ErrorCode.VALIDATION_FAILED,
          "The gate cannot be opened yet. Still open: " + String.join("; ", missing) + ".");
    }
    jdbc.sql(
            "update billing.live_gate set live_ready = true, changed_at = :now, changed_by = :by where id = 1")
        .param("now", Timestamp.from(clock.instant()))
        .param("by", admin)
        .update();
    audit.record(
        actor,
        admin,
        AuditEvent.of("billing.live.ready.changed", "live_gate", "1")
            .withChange(Map.of("liveReady", v.liveReady()), Map.of("liveReady", true)));
    return view();
  }
}
