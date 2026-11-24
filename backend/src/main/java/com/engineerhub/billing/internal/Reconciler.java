package com.engineerhub.billing.internal;

import com.engineerhub.billing.internal.PaymentProvider.ProviderPayment;
import com.engineerhub.billing.internal.PaymentProvider.ProviderRefundRow;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The pure comparison behind reconciliation (docs/21 §6.7): what the provider lists versus what we
 * recorded. It decides nothing and changes nothing; it only reports drift for a human. Absence from
 * the provider's list is reported only when that list is complete, and only for payments well
 * inside the window (a margin absorbs clock and cut-off differences).
 */
final class Reconciler {

  private Reconciler() {}

  static final Duration STUCK_AFTER = Duration.ofMinutes(15);
  static final Duration MARGIN = Duration.ofHours(1);

  record LocalPayment(
      UUID id,
      String providerRef,
      long amountMinor,
      String currency,
      String status,
      long processedRefundMinor,
      long pendingRefundMinor,
      Instant capturedAt) {}

  record LocalRefund(UUID id, String providerRef, String status, Instant updatedAt) {}

  record Finding(
      String kind,
      String providerRef,
      String orderRef,
      UUID localPaymentId,
      UUID localRefundId,
      Long amountMinor,
      String currency,
      String detail) {}

  static List<Finding> compare(
      List<ProviderPayment> providerPayments,
      boolean paymentsComplete,
      List<ProviderRefundRow> providerRefunds,
      Map<String, LocalPayment> localByRef,
      Set<String> knownOrders,
      Map<String, LocalRefund> localRefundsByRef,
      Instant from,
      Instant to,
      Instant now) {
    List<Finding> out = new ArrayList<>();
    Set<String> seenAtProvider = new HashSet<>();
    // The payments listing's amount_refunded proved unreliable against the real provider (it showed
    // 0
    // for a payment with two processed refunds), while the refunds listing was exact. The
    // provider's
    // refunded total is therefore the larger of the two figures.
    Map<String, Long> refundedByPayment = new java.util.HashMap<>();
    for (ProviderRefundRow rr : providerRefunds) {
      if (rr.status().equals("processed")) {
        refundedByPayment.merge(rr.paymentId(), rr.amountMinor(), Long::sum);
      }
    }
    for (ProviderPayment pp : providerPayments) {
      seenAtProvider.add(pp.id());
      boolean wasCaptured = pp.status().equals("captured") || pp.status().equals("refunded");
      if (!wasCaptured) {
        continue; // created, authorized or failed attempts carry no money
      }
      LocalPayment lp = localByRef.get(pp.id());
      if (lp == null) {
        boolean ours = pp.orderId() != null && knownOrders.contains(pp.orderId());
        out.add(
            new Finding(
                ours ? "missing_payment" : "unknown_provider_payment",
                pp.id(),
                blankToNull(pp.orderId()),
                null,
                null,
                pp.amountMinor(),
                pp.currency(),
                ours
                    ? "The provider captured this payment for one of our orders but we never recorded it (a lost webhook)."
                    : "The provider captured a payment that matches nothing we know."));
        continue;
      }
      if (lp.amountMinor() != pp.amountMinor() || !lp.currency().equals(pp.currency())) {
        out.add(
            new Finding(
                "amount_mismatch",
                pp.id(),
                blankToNull(pp.orderId()),
                lp.id(),
                null,
                pp.amountMinor(),
                pp.currency(),
                "We recorded %d %s but the provider has %d %s."
                    .formatted(lp.amountMinor(), lp.currency(), pp.amountMinor(), pp.currency())));
      }
      long providerRefunded =
          Math.max(pp.amountRefundedMinor(), refundedByPayment.getOrDefault(pp.id(), 0L));
      long ours = lp.processedRefundMinor();
      boolean inFlight =
          lp.pendingRefundMinor() > 0 && providerRefunded == ours + lp.pendingRefundMinor();
      if (providerRefunded != ours && !inFlight) {
        out.add(
            new Finding(
                "refund_amount_mismatch",
                pp.id(),
                blankToNull(pp.orderId()),
                lp.id(),
                null,
                providerRefunded,
                pp.currency(),
                "The provider shows %d refunded, we recorded %d (a refund made outside this system, or a lost event)."
                    .formatted(providerRefunded, ours)));
      }
    }
    for (ProviderRefundRow rr : providerRefunds) {
      LocalRefund lr = localRefundsByRef.get(rr.id());
      if (lr != null
          && rr.status().equals("processed")
          && lr.status().equals("pending")
          && lr.updatedAt().isBefore(now.minus(STUCK_AFTER))) {
        out.add(
            new Finding(
                "refund_stuck",
                rr.id(),
                null,
                null,
                lr.id(),
                rr.amountMinor(),
                null,
                "The provider processed this refund but we still show it pending (a lost webhook)."));
      }
    }
    if (paymentsComplete) {
      for (LocalPayment lp : localByRef.values()) {
        boolean inside =
            lp.capturedAt().isAfter(from.plus(MARGIN))
                && lp.capturedAt().isBefore(to.minus(MARGIN));
        if (inside && !seenAtProvider.contains(lp.providerRef())) {
          out.add(
              new Finding(
                  "missing_at_provider",
                  lp.providerRef(),
                  null,
                  lp.id(),
                  null,
                  lp.amountMinor(),
                  lp.currency(),
                  "We recorded this payment but the provider does not list it."));
        }
      }
    }
    return out;
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
