const KIND_LABELS: Record<string, string> = {
  missing_payment: "Payment we never recorded (lost webhook)",
  unknown_provider_payment: "Provider payment matching nothing of ours",
  amount_mismatch: "Amount differs from the provider",
  refund_amount_mismatch: "Refunded total differs from the provider",
  refund_stuck: "Refund finished at the provider, still pending here",
  missing_at_provider: "Our payment is not listed by the provider",
};

export function kindLabel(kind?: string): string {
  return KIND_LABELS[kind ?? ""] ?? "Unknown difference";
}

/** What a run's status means, in words an owner can act on. */
export function runStatusLabel(status?: string): string {
  switch (status) {
    case "ok":
      return "Everything agrees";
    case "drift":
      return "Differences found";
    case "partial":
      return "Compared, but the provider listing was cut off";
    case "skipped":
      return "Skipped (payments are not configured)";
    case "error":
      return "Failed";
    case "running":
      return "Running";
    default:
      return "Unknown";
  }
}

export function needsAttention(status?: string): boolean {
  return status === "drift" || status === "error";
}

/** Items the owner can still tick, in order, for the "what is left" line of the gate. */
export function remainingItems(items: { label?: string; done?: boolean }[]): string[] {
  return items.filter((i) => !i.done).map((i) => i.label ?? "");
}
