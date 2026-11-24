package com.engineerhub.billing.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.engineerhub.billing.internal.PaymentProvider.ProviderPayment;
import com.engineerhub.billing.internal.PaymentProvider.ProviderRefundRow;
import com.engineerhub.billing.internal.Reconciler.Finding;
import com.engineerhub.billing.internal.Reconciler.LocalPayment;
import com.engineerhub.billing.internal.Reconciler.LocalRefund;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The drift rules of reconciliation as a pure function: every kind, and every case that must stay
 * quiet.
 */
class ReconcilerTest {

  private static final Instant NOW = Instant.parse("2026-06-10T12:00:00Z");
  private static final Instant FROM = NOW.minus(Duration.ofHours(48));

  private static ProviderPayment pp(
      String id, long amount, String status, String order, long refunded) {
    return new ProviderPayment(id, amount, "INR", status, order, refunded);
  }

  private static LocalPayment lp(
      String ref, long amount, long processed, long pending, Duration age) {
    return new LocalPayment(
        UUID.randomUUID(), ref, amount, "INR", "captured", processed, pending, NOW.minus(age));
  }

  private static List<Finding> compare(
      List<ProviderPayment> provider,
      boolean complete,
      List<ProviderRefundRow> refunds,
      Map<String, LocalPayment> local,
      Set<String> orders,
      Map<String, LocalRefund> localRefunds) {
    return Reconciler.compare(
        provider, complete, refunds, local, orders, localRefunds, FROM, NOW, NOW);
  }

  private static List<String> kinds(List<Finding> f) {
    return f.stream().map(Finding::kind).toList();
  }

  @Test
  void everythingMatchingIsQuiet() {
    var local = Map.of("pay_1", lp("pay_1", 29950, 0, 0, Duration.ofHours(5)));
    assertThat(
            compare(
                List.of(pp("pay_1", 29950, "captured", "order_1", 0)),
                true,
                List.of(),
                local,
                Set.of("order_1"),
                Map.of()))
        .isEmpty();
  }

  @Test
  void aCapturedPaymentWeNeverRecordedIsMissingIfTheOrderIsOursAndUnknownOtherwise() {
    var f1 =
        compare(
            List.of(pp("pay_9", 100, "captured", "order_ours", 0)),
            true,
            List.of(),
            Map.of(),
            Set.of("order_ours"),
            Map.of());
    assertThat(kinds(f1)).containsExactly("missing_payment");
    assertThat(f1.get(0).orderRef()).isEqualTo("order_ours");
    assertThat(f1.get(0).amountMinor()).isEqualTo(100);
    var f2 =
        compare(
            List.of(pp("pay_9", 100, "captured", "order_theirs", 0)),
            true,
            List.of(),
            Map.of(),
            Set.of("order_ours"),
            Map.of());
    assertThat(kinds(f2)).containsExactly("unknown_provider_payment");
    var f3 =
        compare(
            List.of(pp("pay_9", 100, "captured", "", 0)),
            true,
            List.of(),
            Map.of(),
            Set.of(),
            Map.of());
    assertThat(kinds(f3)).containsExactly("unknown_provider_payment");
    assertThat(f3.get(0).orderRef()).isNull();
    // a payment that was refunded later was still captured once
    assertThat(
            kinds(
                compare(
                    List.of(pp("pay_9", 100, "refunded", "order_ours", 100)),
                    true,
                    List.of(),
                    Map.of(),
                    Set.of("order_ours"),
                    Map.of())))
        .containsExactly("missing_payment");
  }

  @Test
  void attemptsThatNeverCarriedMoneyAreIgnored() {
    for (String status : List.of("created", "authorized", "failed")) {
      assertThat(
              compare(
                  List.of(pp("pay_x", 100, status, "order_ours", 0)),
                  true,
                  List.of(),
                  Map.of(),
                  Set.of("order_ours"),
                  Map.of()))
          .as(status)
          .isEmpty();
    }
  }

  @Test
  void anAmountOrCurrencyThatDiffersIsReported() {
    var local = Map.of("pay_1", lp("pay_1", 29950, 0, 0, Duration.ofHours(5)));
    assertThat(
            kinds(
                compare(
                    List.of(pp("pay_1", 30000, "captured", "o", 0)),
                    true,
                    List.of(),
                    local,
                    Set.of(),
                    Map.of())))
        .containsExactly("amount_mismatch");
    var other = new ProviderPayment("pay_1", 29950, "USD", "captured", "o", 0);
    assertThat(kinds(compare(List.of(other), true, List.of(), local, Set.of(), Map.of())))
        .containsExactly("amount_mismatch");
  }

  @Test
  void refundTotalsMustAgreeButAnInFlightRefundIsNotDrift() {
    var local = Map.of("pay_1", lp("pay_1", 29950, 10000, 0, Duration.ofHours(5)));
    assertThat(
            compare(
                List.of(pp("pay_1", 29950, "captured", "o", 10000)),
                true,
                List.of(),
                local,
                Set.of(),
                Map.of()))
        .isEmpty();
    var f =
        compare(
            List.of(pp("pay_1", 29950, "captured", "o", 15000)),
            true,
            List.of(),
            local,
            Set.of(),
            Map.of());
    assertThat(kinds(f)).containsExactly("refund_amount_mismatch");
    assertThat(f.get(0).amountMinor()).isEqualTo(15000);
    // a refund made in the provider dashboard that we never heard of
    var none = Map.of("pay_1", lp("pay_1", 29950, 0, 0, Duration.ofHours(5)));
    assertThat(
            kinds(
                compare(
                    List.of(pp("pay_1", 29950, "refunded", "o", 29950)),
                    true,
                    List.of(),
                    none,
                    Set.of(),
                    Map.of())))
        .containsExactly("refund_amount_mismatch");
    // our refund is pending and the provider already shows it: that is "stuck" territory, not a
    // mismatch
    var inflight = Map.of("pay_1", lp("pay_1", 29950, 0, 10000, Duration.ofHours(5)));
    assertThat(
            compare(
                List.of(pp("pay_1", 29950, "captured", "o", 10000)),
                true,
                List.of(),
                inflight,
                Set.of(),
                Map.of()))
        .isEmpty();
    // but a pending refund that does NOT explain the provider's total still is
    assertThat(
            kinds(
                compare(
                    List.of(pp("pay_1", 29950, "captured", "o", 12345)),
                    true,
                    List.of(),
                    inflight,
                    Set.of(),
                    Map.of())))
        .containsExactly("refund_amount_mismatch");
  }

  @Test
  void aRefundTheProviderFinishedButWeStillShowPendingIsStuckOnlyAfterAWhile() {
    UUID id = UUID.randomUUID();
    var old =
        Map.of(
            "rfnd_1", new LocalRefund(id, "rfnd_1", "pending", NOW.minus(Duration.ofMinutes(16))));
    var fresh =
        Map.of(
            "rfnd_1", new LocalRefund(id, "rfnd_1", "pending", NOW.minus(Duration.ofMinutes(14))));
    var row = List.of(new ProviderRefundRow("rfnd_1", "pay_1", 500, "processed"));
    var f = compare(List.of(), true, row, Map.of(), Set.of(), old);
    assertThat(kinds(f)).containsExactly("refund_stuck");
    assertThat(f.get(0).localRefundId()).isEqualTo(id);
    assertThat(compare(List.of(), true, row, Map.of(), Set.of(), fresh)).isEmpty();
    // not processed at the provider, already processed here, or unknown here: nothing to say
    assertThat(
            compare(
                List.of(),
                true,
                List.of(new ProviderRefundRow("rfnd_1", "pay_1", 500, "pending")),
                Map.of(),
                Set.of(),
                old))
        .isEmpty();
    var done =
        Map.of(
            "rfnd_1", new LocalRefund(id, "rfnd_1", "processed", NOW.minus(Duration.ofHours(2))));
    assertThat(compare(List.of(), true, row, Map.of(), Set.of(), done)).isEmpty();
    assertThat(compare(List.of(), true, row, Map.of(), Set.of(), Map.of())).isEmpty();
  }

  @Test
  void aPaymentMissingAtTheProviderIsReportedOnlyForACompleteListingAndInsideTheMargin() {
    var local =
        Map.of(
            "pay_old", lp("pay_old", 100, 0, 0, Duration.ofHours(5)),
            "pay_recent",
                lp(
                    "pay_recent",
                    100,
                    0,
                    0,
                    Duration.ofMinutes(30)), // inside the 1 hour margin of "now"
            "pay_edge",
                lp(
                    "pay_edge",
                    100,
                    0,
                    0,
                    Duration.ofHours(47).plusMinutes(30))); // inside the margin of the window start
    var f = compare(List.of(), true, List.of(), local, Set.of(), Map.of());
    assertThat(kinds(f)).containsExactly("missing_at_provider");
    assertThat(f.get(0).providerRef()).isEqualTo("pay_old");
    // an incomplete listing proves nothing about absence
    assertThat(compare(List.of(), false, List.of(), local, Set.of(), Map.of())).isEmpty();
    // present at the provider: fine
    assertThat(
            compare(
                List.of(pp("pay_old", 100, "captured", "o", 0)),
                true,
                List.of(),
                local,
                Set.of(),
                Map.of()))
        .isEmpty();
  }

  @Test
  void severalDriftsAreAllReported() {
    var local = Map.of("pay_1", lp("pay_1", 100, 0, 0, Duration.ofHours(5)));
    var provider =
        List.of(
            pp("pay_1", 200, "captured", "o", 0), pp("pay_2", 300, "captured", "order_ours", 0));
    assertThat(kinds(compare(provider, true, List.of(), local, Set.of("order_ours"), Map.of())))
        .containsExactlyInAnyOrder("amount_mismatch", "missing_payment");
  }
}
