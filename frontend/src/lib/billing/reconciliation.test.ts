import { describe, expect, it } from "vitest";
import { kindLabel, needsAttention, remainingItems, runStatusLabel } from "./reconciliation";

describe("reconciliation helpers", () => {
  it("labels every kind of difference and never shows a raw code", () => {
    for (const k of [
      "missing_payment",
      "unknown_provider_payment",
      "amount_mismatch",
      "refund_amount_mismatch",
      "refund_stuck",
      "missing_at_provider",
    ])
      expect(kindLabel(k), k).not.toContain("_");
    expect(kindLabel("weird")).toBe("Unknown difference");
    expect(kindLabel(undefined)).toBe("Unknown difference");
  });

  it("says what a run found, and what needs attention", () => {
    expect(runStatusLabel("ok")).toBe("Everything agrees");
    expect(runStatusLabel("drift")).toBe("Differences found");
    expect(runStatusLabel("partial")).toContain("cut off");
    expect(runStatusLabel("skipped")).toContain("not configured");
    expect(runStatusLabel("zzz")).toBe("Unknown");
    expect(needsAttention("drift")).toBe(true);
    expect(needsAttention("error")).toBe(true);
    for (const s of ["ok", "partial", "skipped", "running", undefined])
      expect(needsAttention(s), String(s)).toBe(false);
  });

  it("lists what is left on the gate", () => {
    expect(
      remainingItems([{ label: "A", done: true }, { label: "B", done: false }, { label: "C" }]),
    ).toEqual(["B", "C"]);
    expect(remainingItems([])).toEqual([]);
  });
});
