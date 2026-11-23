package com.engineerhub.billing.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class PurchaseDtos {

  private PurchaseDtos() {}

  record QuoteRequest(@NotNull UUID planPriceId) {}

  /** What the user will pay: the final, tax-inclusive amount decided by the server. */
  record QuoteView(
      UUID planPriceId,
      String planName,
      String billingMode,
      String periodUnit,
      String currency,
      long amountInclTaxMinor,
      boolean purchasable,
      String note) {}

  record PurchaseRequest(@NotNull UUID planPriceId) {}

  /** Everything Razorpay Checkout needs in the browser (no secret). */
  record CheckoutView(
      UUID intentId,
      String orderId,
      String keyId,
      long amountMinor,
      String currency,
      String planName,
      String mode) {}

  record ConfirmRequest(
      @NotBlank @Size(max = 60) @Pattern(regexp = "^order_[A-Za-z0-9]+$") String razorpayOrderId,
      @NotBlank @Size(max = 60) @Pattern(regexp = "^pay_[A-Za-z0-9]+$") String razorpayPaymentId,
      @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Fa-f0-9]+$") String razorpaySignature) {}

  record IntentView(
      UUID intentId,
      String status,
      long amountMinor,
      String currency,
      String planName,
      Instant createdAt) {}

  record ConfirmView(boolean signatureValid, IntentView intent) {}

  record PaymentsConfig(boolean paymentsEnabled, String mode) {}
}
