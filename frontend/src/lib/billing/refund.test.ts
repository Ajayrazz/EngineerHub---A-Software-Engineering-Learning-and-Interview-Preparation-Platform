import { describe, expect, it } from "vitest";
import { adminActions, isOpenRefund, reasonLabel, refundStatusLabel } from "./refund";

describe("refund helpers", () => {
  it("words every status for a learner, and never leaks an unknown one", () => {
    expect(refundStatusLabel("requested")).toBe("Waiting for review");
    expect(refundStatusLabel("under_review")).toBe("Being reviewed");
    expect(refundStatusLabel("processing")).toBe("Refund in progress");
    expect(refundStatusLabel("approved")).toBe("Refund in progress");
    expect(refundStatusLabel("processed")).toBe("Refunded");
    expect(refundStatusLabel("rejected")).toBe("Not approved");
    expect(refundStatusLabel("failed")).toBe("Being looked into");
    expect(refundStatusLabel("weird")).toBe("Unknown");
    expect(refundStatusLabel(undefined)).toBe("Unknown");
  });

  it("knows which requests are still open", () => {
    for (const s of ["requested", "under_review", "approved", "processing", "failed"])
      expect(isOpenRefund(s), s).toBe(true);
    for (const s of ["processed", "rejected", undefined, "x"])
      expect(isOpenRefund(s), String(s)).toBe(false);
  });

  it("offers an admin only the actions that make sense in each state", () => {
    expect(adminActions("requested")).toEqual({
      review: true,
      approve: true,
      reject: true,
      retry: false,
    });
    expect(adminActions("under_review")).toEqual({
      review: false,
      approve: true,
      reject: true,
      retry: false,
    });
    expect(adminActions("failed")).toEqual({
      review: false,
      approve: false,
      reject: true,
      retry: true,
    });
    for (const s of ["processing", "processed", "rejected", "approved", undefined])
      expect(adminActions(s), String(s)).toEqual({
        review: false,
        approve: false,
        reject: false,
        retry: false,
      });
  });

  it("labels reasons", () => {
    expect(reasonLabel("duplicate_payment")).toContain("more than once");
    expect(reasonLabel("nope")).toBe("Another reason");
  });
});
