export const REASONS: { value: string; label: string }[] = [
  { value: "duplicate_payment", label: "I was charged more than once" },
  { value: "not_activated", label: "I paid but my access did not start" },
  { value: "technical_issue", label: "A technical problem stopped me using the service" },
  { value: "other", label: "Another reason" },
];

export function reasonLabel(value?: string): string {
  return REASONS.find((r) => r.value === value)?.label ?? "Another reason";
}

/** What a learner sees. Internal states they cannot act on are folded into plain wording. */
export function refundStatusLabel(status?: string): string {
  switch (status) {
    case "requested":
      return "Waiting for review";
    case "under_review":
      return "Being reviewed";
    case "approved":
    case "processing":
      return "Refund in progress";
    case "processed":
      return "Refunded";
    case "rejected":
      return "Not approved";
    case "failed":
      return "Being looked into";
    default:
      return "Unknown";
  }
}

/** A request that still blocks a new one for the same payment. */
export function isOpenRefund(status?: string): boolean {
  return ["requested", "under_review", "approved", "processing", "failed"].includes(status ?? "");
}

/** What the admin can do next with a request in this state. */
export function adminActions(status?: string): {
  review: boolean;
  approve: boolean;
  reject: boolean;
  retry: boolean;
} {
  return {
    review: status === "requested",
    approve: status === "requested" || status === "under_review",
    reject: status === "requested" || status === "under_review" || status === "failed",
    retry: status === "failed",
  };
}
