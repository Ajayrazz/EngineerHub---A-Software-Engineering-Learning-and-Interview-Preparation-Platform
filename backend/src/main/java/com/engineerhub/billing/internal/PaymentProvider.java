package com.engineerhub.billing.internal;

import java.util.Map;

/**
 * The payment provider as the billing core sees it (docs/21 §2): the core never talks to Razorpay
 * directly, so a second provider stays possible and tests use a fake.
 */
interface PaymentProvider {

  record ProviderOrder(String orderId, long amountMinor, String currency) {}

  record ProviderSubscription(String subscriptionId) {}

  /**
   * One payment as the provider lists it (statuses are the provider's words, e.g. captured,
   * refunded).
   */
  record ProviderPayment(
      String id,
      long amountMinor,
      String currency,
      String status,
      String orderId,
      long amountRefundedMinor) {}

  record ProviderRefundRow(String id, String paymentId, long amountMinor, String status) {}

  /**
   * {@code complete} is false when the listing was cut off at {@code maxItems}: absences prove
   * nothing then.
   */
  record Listing<T>(java.util.List<T> items, boolean complete) {}

  /** {@code status} is the provider's own word: "processed" or "pending". */
  record ProviderRefund(String refundId, String status) {}

  String name();

  /** True when credentials are present (and valid for the allowed mode). */
  boolean configured();

  /** Public key id for the browser checkout (never the secret). */
  String publicKeyId();

  /** Creates the provider-side order for exactly this server-decided amount. */
  ProviderOrder createOrder(
      long amountMinor, String currency, String receipt, Map<String, String> notes);

  /** The browser's checkout callback signature (a UX speed-up only: it never grants access). */
  boolean verifyCheckoutSignature(String orderId, String paymentId, String signature);

  boolean webhookConfigured();

  boolean verifyWebhookSignature(byte[] rawBody, String signature);

  /**
   * Creates the provider-side plan for a RECURRING price (fixed amount and period; {@code period}
   * is "month" or "year"). Called once per price; the id is cached by the caller.
   */
  String createPlan(String period, long amountMinor, String currency, String name);

  /** Creates a subscription on a provider plan for {@code totalCycles} billing cycles. */
  ProviderSubscription createSubscription(
      String planRef, int totalCycles, Map<String, String> notes);

  /**
   * Cancels the subscription at the provider NOW, so no further charge can happen. Paid access is
   * unaffected (it is derived from our own paid periods) and nothing is refunded. (A cycle-end
   * cancel was tried against the real provider and left the subscription active with no visible
   * cancellation, which is the dangerous failure: a customer charged after cancelling.)
   */
  void cancelSubscription(String subscriptionRef);

  /**
   * The browser's subscription checkout callback signature over {@code payment_id|subscription_id}
   * (a UX speed-up only).
   */
  boolean verifySubscriptionSignature(String subscriptionRef, String paymentId, String signature);

  /** Refunds {@code amountMinor} of a captured payment (full or partial). */
  ProviderRefund refundPayment(String paymentRef, long amountMinor, Map<String, String> notes);

  /** Payments created in [from, to), newest first, at most {@code maxItems}. */
  Listing<ProviderPayment> listPayments(java.time.Instant from, java.time.Instant to, int maxItems);

  /** Refunds created in [from, to), at most {@code maxItems}. */
  Listing<ProviderRefundRow> listRefunds(
      java.time.Instant from, java.time.Instant to, int maxItems);
}
