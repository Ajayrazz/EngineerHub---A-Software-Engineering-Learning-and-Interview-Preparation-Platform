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

final class RefundDtos {

  private RefundDtos() {}

  record RefundRequestCreate(
      @NotNull UUID paymentId,
      @NotBlank @Pattern(regexp = "^(duplicate_payment|not_activated|technical_issue|other)$")
          String reason,
      @Size(max = 2000) String details) {}

  /**
   * Null policy text = the owner has not published one (P-6): the UI says so instead of inventing
   * one.
   */
  record RefundPolicyView(String policyText, Integer requestWindowDays) {}

  record MyRefundView(
      UUID id,
      UUID paymentId,
      String planName,
      long paymentAmountMinor,
      String reason,
      String status,
      String decisionNote,
      Long refundedMinor,
      UUID refundId,
      String creditNoteNumber,
      Instant createdAt) {}

  record RefundSettingsRequest(
      @Size(max = 10000) String policyText,
      @Min(1) @Max(3660) Integer requestWindowDays,
      @NotNull Boolean revokeAccessOnRefund,
      @Min(100) Long approvalThresholdMinor,
      @NotNull Boolean autoDetectDuplicates) {}

  record RefundSettingsView(
      String policyText,
      Integer requestWindowDays,
      boolean revokeAccessOnRefund,
      Long approvalThresholdMinor,
      boolean autoDetectDuplicates,
      Instant updatedAt) {}

  record ApproveRequest(
      @Min(1) Long amountMinor, @NotBlank @Size(min = 5, max = 1000) String note) {}

  record RejectRequest(@NotBlank @Size(min = 5, max = 1000) String note) {}

  /**
   * Retrying can refund twice if the first attempt actually reached the provider: the admin must
   * say they checked.
   */
  record RetryRequest(@NotNull Boolean confirmNotRefundedAtProvider) {}

  record AdminRefundRow(
      UUID id,
      String status,
      String reason,
      String username,
      String planName,
      long paymentAmountMinor,
      long refundableMinor,
      Instant createdAt) {}

  record RefundAttempt(UUID id, long amountMinor, String status, Instant createdAt) {}

  record RefundFlag(
      UUID paymentId,
      String username,
      String planName,
      long amountMinor,
      Instant capturedAt,
      String kind,
      String suggestion) {}

  record AdminRefundDetail(
      AdminRefundRow request,
      String details,
      String decisionNote,
      Long approvedAmountMinor,
      String paymentStatus,
      Instant paymentCapturedAt,
      boolean hasDocument,
      boolean reviewStarted,
      Long approvalThresholdMinor,
      List<RefundAttempt> attempts,
      List<RefundFlag> flags) {}
}
