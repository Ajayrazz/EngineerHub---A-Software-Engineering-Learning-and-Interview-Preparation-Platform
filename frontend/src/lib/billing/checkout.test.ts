import { describe, expect, it } from "vitest";
import {
  isFinal,
  newIdempotencyKey,
  pollDelaysMs,
  razorpayOptions,
  statusMessage,
} from "./checkout";

describe("checkout helpers", () => {
  it("builds provider options only from the server's checkout data", () => {
    const o = razorpayOptions({
      intentId: "i",
      orderId: "order_abc",
      keyId: "rzp_test_k",
      amountMinor: 29950,
      currency: "INR",
      planName: "Premium",
      mode: "test",
    });
    expect(o).toEqual({
      key: "rzp_test_k",
      amount: 29950,
      currency: "INR",
      order_id: "order_abc",
      name: "EngineerHub",
      description: "Premium",
    });
    // nothing personal or secret can ride along
    expect(Object.keys(o).sort()).toEqual([
      "amount",
      "currency",
      "description",
      "key",
      "name",
      "order_id",
    ]);
    expect(JSON.stringify(o)).not.toMatch(/secret|email|prefill/i);
  });

  it("generates distinct, valid idempotency keys", () => {
    const a = newIdempotencyKey();
    expect(a).toMatch(/^buy-[0-9a-f-]{36}$/);
    expect(a.length).toBeLessThanOrEqual(80);
    expect(a).not.toBe(newIdempotencyKey());
  });

  it("knows which statuses end polling", () => {
    for (const s of ["paid", "failed", "needs_review", "expired"]) expect(isFinal(s), s).toBe(true);
    for (const s of ["payment_pending", "created", undefined])
      expect(isFinal(s), String(s)).toBe(false);
  });

  it("never claims success before the server says paid", () => {
    expect(statusMessage("paid")).toContain("active");
    for (const s of ["payment_pending", "created", undefined])
      expect(statusMessage(s)).not.toContain("is active");
    expect(statusMessage("needs_review")).toContain("review");
    expect(statusMessage("failed")).toContain("not been charged");
  });

  it("polls for about a minute and a half with growing intervals", () => {
    const d = pollDelaysMs();
    expect(d[0]).toBe(1000);
    expect(d.at(-1)).toBe(5000);
    const total = d.reduce((a, b) => a + b, 0);
    expect(total).toBeGreaterThan(60_000);
    expect(total).toBeLessThan(150_000);
  });
});
