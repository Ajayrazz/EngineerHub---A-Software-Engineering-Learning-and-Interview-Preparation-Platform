package com.engineerhub.billing.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class BillingDtos {

  private BillingDtos() {}

  // ---------------------------------------------------------------- requests

  record PlanCreate(
      @NotBlank
          @Size(max = 40)
          @Pattern(
              regexp = "^[a-z0-9]+(-[a-z0-9]+)*$",
              message = "lowercase letters, digits and single hyphens")
          String code,
      @NotBlank @Size(max = 100) String name) {}

  record PlanPatch(@Size(min = 1, max = 100) String name, Boolean active) {}

  record PriceCreate(
      @NotBlank @Pattern(regexp = "^(ONE_TIME|RECURRING)$") String billingMode,
      @NotBlank @Pattern(regexp = "^(month|year)$") String periodUnit,
      @Min(100) @Max(100_000_000) long amountInclTaxMinor) {}

  record PricePatch(@NotNull Boolean active) {}

  record EntitlementsRequest(
      @NotNull @Size(max = 10)
          List<@NotBlank @Pattern(regexp = "^premium_content$") String> codes) {}

  record AccessGrantRequest(
      @NotNull UUID planId,
      @Min(1) @Max(3660) int days,
      @NotBlank @Size(min = 5, max = 500) String reason) {}

  record RevokeRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

  // ---------------------------------------------------------------- views

  record PriceView(
      UUID id,
      String billingMode,
      String periodUnit,
      String currency,
      long amountInclTaxMinor,
      boolean active) {}

  record PlanView(
      UUID id,
      String code,
      String name,
      boolean active,
      List<String> entitlements,
      List<PriceView> prices) {}

  record PeriodView(
      UUID id,
      String planName,
      Instant startsAt,
      Instant endsAt,
      String sourceType,
      boolean revoked,
      String revokedReason,
      String reason,
      Instant createdAt) {}

  record AccessView(boolean premium, Instant validThrough, List<PeriodView> periods) {}

  record PaymentView(
      UUID id,
      String planName,
      long amountMinor,
      String currency,
      String status,
      Instant capturedAt,
      String documentNumber,
      String refundRequestStatus,
      boolean refundRequestable) {}

  /**
   * The user's running auto-renewing subscription. {@code renewsOn} is set only while it will
   * really renew (status active); a cancel-scheduled one just runs to the valid-through date.
   */
  record SubscriptionSummary(
      UUID id,
      String status,
      String planName,
      long amountMinor,
      String currency,
      String periodUnit,
      Instant renewsOn) {}

  /** Status is ACTIVE, EXPIRING_SOON, EXPIRED or NONE; amounts are tax-inclusive minor units. */
  record BillingSummary(
      String status,
      boolean premium,
      Instant validThrough,
      Long daysRemaining,
      boolean renewalDue,
      int renewalWindowDays,
      SubscriptionSummary subscription,
      boolean documentsAvailable,
      List<PaymentView> payments) {}
}
