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
