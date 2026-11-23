package com.engineerhub.billing.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

final class SubscriptionDtos {

  private SubscriptionDtos() {}

  record SubscribeRequest(@NotNull UUID planPriceId) {}

  /** Everything Razorpay Checkout needs for a subscription (no secret). */
  record SubscriptionCheckout(
      UUID subscriptionId,
      String providerSubscriptionId,
      String keyId,
      long amountMinor,
      String currency,
      String periodUnit,
      String planName,
      String mode) {}

  record SubscriptionConfirmRequest(
      @NotBlank @Size(max = 60) @Pattern(regexp = "^sub_[A-Za-z0-9]+$")
          String razorpaySubscriptionId,
      @NotBlank @Size(max = 60) @Pattern(regexp = "^pay_[A-Za-z0-9]+$") String razorpayPaymentId,
      @NotBlank @Size(max = 128) @Pattern(regexp = "^[A-Fa-f0-9]+$") String razorpaySignature) {}

  /**
   * {@code paid} is true once a verified charge has been recorded: only then does the user have
   * access.
   */
  record SubscriptionView(
      UUID id,
      String status,
      String planName,
      long amountMinor,
      String currency,
      String periodUnit,
      boolean cancelScheduled,
      boolean paid,
      Instant createdAt) {}

  record SubscriptionConfirmView(boolean signatureValid, SubscriptionView subscription) {}
}
