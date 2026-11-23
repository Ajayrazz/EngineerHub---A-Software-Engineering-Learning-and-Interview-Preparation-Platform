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
