/** Mirrors the API's shape checks (the server stays authoritative): format only, not registration or checksum. */
export const GSTIN_RE = /^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$/;
export const SAC_RE = /^[0-9]{4,8}$/;

/** "18" or "18.5" (percent) to basis points; null unless 0.01..50.00 with at most two decimals. */
export function percentToBps(input: string): number | null {
  const m = /^\s*(\d{1,2})(?:\.(\d{1,2}))?\s*$/.exec(input);
  if (!m) return null;
  const bps = Number(m[1]) * 100 + Number((m[2] ?? "").padEnd(2, "0"));
  return bps >= 1 && bps <= 5000 ? bps : null;
}

export function bpsToPercent(bps: number): string {
  return `${Math.floor(bps / 100)}.${String(bps % 100).padStart(2, "0")}%`;
}

export type SellerForm = {
  legalName: string;
  address: string;
  state: string;
  gstin: string;
  sac: string;
};

/** Problems that would make the server refuse these settings (empty list = fine). */
export function settingsProblems(mode: string, f: SellerForm, activeRules: number): string[] {
  const out: string[] = [];
  if (f.gstin.trim() && !GSTIN_RE.test(f.gstin.trim()))
    out.push("GSTIN must have the 15-character GST format.");
  if (f.sac.trim() && !SAC_RE.test(f.sac.trim()))
    out.push("The service code (SAC) must be 4 to 8 digits.");
  if (mode === "ENABLED") {
    if (
      !f.legalName.trim() ||
      !f.address.trim() ||
      !f.state.trim() ||
      !f.gstin.trim() ||
      !f.sac.trim()
    )
      out.push("ENABLED needs the legal name, address, state, GSTIN and service code.");
    if (activeRules === 0) out.push("ENABLED needs at least one active tax rule.");
  }
  return out;
}
