/** Amounts are integers in minor units (paise) and always tax-inclusive final prices (docs/21 §5). */
export function formatInr(minor: number): string {
  return new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR" }).format(minor / 100);
}

/** "₹299.00 per month, one-time pass" / "₹2,999.00 per year, auto-renewing". */
export function describePrice(p: {
  billingMode?: string;
  periodUnit?: string;
  amountInclTaxMinor?: number;
}): string {
  const mode = p.billingMode === "RECURRING" ? "auto-renewing" : "one-time pass";
  return `${formatInr(p.amountInclTaxMinor ?? 0)} per ${p.periodUnit ?? "period"}, ${mode}`;
}

/** Whole rupees to minor units without floating-point drift ("299.99" -> 29999); null if not a valid amount. */
export function toMinor(input: string): number | null {
  const m = /^\s*(\d{1,7})(?:\.(\d{1,2}))?\s*$/.exec(input);
  if (!m) return null;
  return Number(m[1]) * 100 + Number((m[2] ?? "").padEnd(2, "0"));
}

export function formatDate(iso?: string): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "—" : d.toISOString().slice(0, 10);
}

type SummaryLike = {
  status?: string;
  validThrough?: string;
  daysRemaining?: number;
  renewalDue?: boolean;
};

/** Headline for the billing page. "Free plan" is kept verbatim for people who never had (or lost) access. */
export function accessHeadline(s: SummaryLike): string {
  switch (s.status) {
    case "ACTIVE":
    case "EXPIRING_SOON":
      return `Premium access until ${formatDate(s.validThrough)}`;
    case "EXPIRED":
      return "Your premium access has ended";
    default:
      return "Free plan";
  }
}

/** Renewal reminder text, or null when there is nothing to remind about. Access never auto-renews for passes. */
export function renewalReminder(s: SummaryLike): string | null {
  if (s.status === "EXPIRED") return "Your access has ended. Renew to get premium content back.";
  if (s.status === "EXPIRING_SOON" && s.renewalDue) {
    const d = s.daysRemaining ?? 0;
    const when = d <= 1 ? "within a day" : `in ${d} days`;
    return `Your premium access ends ${when} (${formatDate(s.validThrough)}) and will not renew automatically. Renew now to keep it without a gap.`;
  }
  return null;
}

const PAYMENT_STATUS: Record<string, string> = {
  captured: "paid",
  refunded: "refunded",
  partially_refunded: "partially refunded",
};

export function paymentStatusLabel(status?: string): string {
  return PAYMENT_STATUS[status ?? ""] ?? "unknown";
}
