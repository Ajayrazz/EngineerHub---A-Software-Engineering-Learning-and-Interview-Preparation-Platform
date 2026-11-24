package com.engineerhub.billing.internal;

import com.engineerhub.shared.error.ApiException;
import com.engineerhub.shared.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Test double for the Razorpay HTTP call only: order creation is simulated, everything else
 * (credentials, signature verification) is the real {@link RazorpayProvider} code running with fake
 * test credentials.
 */
@Component
@Primary
class FakeRazorpayProvider extends RazorpayProvider {

  record Order(
      String orderId,
      long amountMinor,
      String currency,
      String receipt,
      Map<String, String> notes) {}

  private static final AtomicInteger SEQ = new AtomicInteger();

  record Plan(String planId, String period, long amountMinor, String currency, String name) {}

  record Subscription(
      String subscriptionId, String planId, int totalCycles, Map<String, String> notes) {}

  final List<Plan> plans = new CopyOnWriteArrayList<>();
  final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
  final List<String> cancelledSubscriptions = new CopyOnWriteArrayList<>();
  volatile boolean failNextSubscription = false;
  volatile boolean failNextCancel = false;

  record Refund(String refundId, String paymentRef, long amountMinor, Map<String, String> notes) {}

  final List<Refund> refunds = new CopyOnWriteArrayList<>();
  volatile boolean failNextRefund = false;

  /** What the provider "lists" for reconciliation: tests fill these. */
  final List<ProviderPayment> listedPayments = new CopyOnWriteArrayList<>();

  final List<ProviderRefundRow> listedRefunds = new CopyOnWriteArrayList<>();
  volatile boolean listingComplete = true;
  volatile boolean failNextListing = false;

  /** What the provider reports for a new refund: "processed" or "pending". */
  volatile String refundStatus = "processed";

  volatile boolean disabled = false;
  volatile boolean failNext = false;
  final List<Order> orders = new CopyOnWriteArrayList<>();

  FakeRazorpayProvider(RazorpayProperties props) {
    super(props);
  }

  void reset() {
    disabled = false;
    failNext = false;
    orders.clear();
    plans.clear();
    subscriptions.clear();
    cancelledSubscriptions.clear();
    failNextSubscription = false;
    failNextCancel = false;
    refunds.clear();
    failNextRefund = false;
    refundStatus = "processed";
    listedPayments.clear();
    listedRefunds.clear();
    listingComplete = true;
    failNextListing = false;
  }

  private static ApiException unavailable() {
    return new ApiException(
        ErrorCode.SERVICE_UNAVAILABLE,
        "The payment provider is temporarily unavailable. Please retry shortly.");
  }

  @Override
  public String createPlan(String period, long amountMinor, String currency, String name) {
    if (!configured() || failNextSubscription) {
      failNextSubscription = false;
      throw unavailable();
    }
    String id = "plan_IT" + SEQ.incrementAndGet() + Long.toString(System.nanoTime(), 36);
    plans.add(new Plan(id, period, amountMinor, currency, name));
    return id;
  }

  @Override
  public ProviderSubscription createSubscription(
      String planRef, int totalCycles, Map<String, String> notes) {
    if (!configured() || failNextSubscription) {
      failNextSubscription = false;
      throw unavailable();
    }
    String id = "sub_IT" + SEQ.incrementAndGet() + Long.toString(System.nanoTime(), 36);
    subscriptions.add(new Subscription(id, planRef, totalCycles, notes));
    return new ProviderSubscription(id);
  }

  @Override
  public Listing<ProviderPayment> listPayments(
      java.time.Instant from, java.time.Instant to, int maxItems) {
    if (failNextListing) {
      failNextListing = false;
      throw unavailable();
    }
    return new Listing<>(List.copyOf(listedPayments), listingComplete);
  }

  @Override
  public Listing<ProviderRefundRow> listRefunds(
      java.time.Instant from, java.time.Instant to, int maxItems) {
    return new Listing<>(List.copyOf(listedRefunds), listingComplete);
  }

  @Override
  public ProviderRefund refundPayment(
      String paymentRef, long amountMinor, Map<String, String> notes) {
    if (failNextRefund) {
      failNextRefund = false;
      throw unavailable();
    }
    String id = "rfnd_IT" + SEQ.incrementAndGet() + Long.toString(System.nanoTime(), 36);
    refunds.add(new Refund(id, paymentRef, amountMinor, notes));
    return new ProviderRefund(id, refundStatus);
  }

  @Override
  public void cancelSubscription(String subscriptionRef) {
    if (failNextCancel) {
      failNextCancel = false;
      throw unavailable();
    }
    cancelledSubscriptions.add(subscriptionRef);
  }

  @Override
  public boolean configured() {
    return !disabled && super.configured();
  }

  @Override
  public ProviderOrder createOrder(
      long amountMinor, String currency, String receipt, Map<String, String> notes) {
    if (!configured()) {
      throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "Online payment is not available yet.");
    }
    if (failNext) {
      failNext = false;
      throw new ApiException(
          ErrorCode.SERVICE_UNAVAILABLE,
          "The payment provider is temporarily unavailable. Please retry shortly.");
    }
    String id = "order_IT" + SEQ.incrementAndGet() + Long.toString(System.nanoTime(), 36);
    orders.add(new Order(id, amountMinor, currency, receipt, notes));
    return new ProviderOrder(id, amountMinor, currency);
  }
}
