package com.engineerhub.billing.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class ReconciliationDtos {

  private ReconciliationDtos() {}

  record ReconciliationRunView(
      UUID id,
      String trigger,
      Instant startedAt,
      Instant finishedAt,
      String status,
      int providerPayments,
      int localPayments,
      int findingsNew,
      int intentsExpired,
      String note) {}

  /**
   * {@code applicable} = an admin can apply the fix (the others are acknowledged after a human
   * check).
   */
  record FindingView(
      UUID id,
      String kind,
      String providerRef,
      String orderRef,
      Long amountMinor,
      String currency,
      String detail,
      boolean applicable,
      Instant createdAt) {}

  record ReconciliationOverview(List<ReconciliationRunView> runs, List<FindingView> openFindings) {}

  record ResolveRequest(@NotBlank @Size(min = 5, max = 500) String note) {}

  // ------------------------------------------------------------------ live gate

  record MachineCheck(boolean ok, String message) {}

  record GateItemView(
      String code,
      String label,
      boolean done,
      String note,
      Instant confirmedAt,
      MachineCheck check) {}

  record LiveGateView(
      boolean liveReady,
      Instant changedAt,
      boolean allItemsDone,
      List<GateItemView> items,
      String enforcement) {}

  record GateItemRequest(@NotNull Boolean done, @Size(max = 500) String note) {}

  record GateReadyRequest(@NotNull Boolean ready) {}
}
