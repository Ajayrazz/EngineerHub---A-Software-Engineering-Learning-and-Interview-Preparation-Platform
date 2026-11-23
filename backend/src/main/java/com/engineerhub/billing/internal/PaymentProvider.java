package com.engineerhub.billing.internal;

import java.util.Map;

/**
 * The payment provider as the billing core sees it (docs/21 §2): the core never talks to Razorpay
 * directly, so a second provider stays possible and tests use a fake.
 */
interface PaymentProvider {

  record ProviderOrder(String orderId, long amountMinor, String currency) {}

  record ProviderSubscription(String subscriptionId) {}

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

  /** Stops renewals at the end of the current cycle (never refunds, never ends paid access). */
  void cancelSubscriptionAtCycleEnd(String subscriptionRef);

  /**
   * The browser's subscription checkout callback signature over {@code payment_id|subscription_id}
   * (a UX speed-up only).
   */
  boolean verifySubscriptionSignature(String subscriptionRef, String paymentId, String signature);
}
