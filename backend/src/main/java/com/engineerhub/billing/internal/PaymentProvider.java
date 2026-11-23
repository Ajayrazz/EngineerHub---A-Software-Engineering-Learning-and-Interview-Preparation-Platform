package com.engineerhub.billing.internal;

import java.util.Map;

/**
 * The payment provider as the billing core sees it (docs/21 §2): the core never talks to Razorpay
 * directly, so a second provider stays possible and tests use a fake.
 */
interface PaymentProvider {

  record ProviderOrder(String orderId, long amountMinor, String currency) {}

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
}
