import { describe, expect, it } from "vitest";
import { buildCsp, isCheckoutPath } from "./csp";

const base = {
  nonce: "abc123",
  isDev: false,
  issuerOrigin: "http://localhost:8081",
  secure: false,
};

describe("buildCsp", () => {
  it("never mentions the payment provider on ordinary pages", () => {
    const csp = buildCsp({ ...base, checkout: false });
    expect(csp).not.toContain("razorpay");
    expect(csp).toContain("frame-src 'none'");
    expect(csp).toContain("script-src 'self' 'nonce-abc123' 'strict-dynamic'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("form-action 'self' http://localhost:8081");
  });

  it("allows the provider only for frames, connections and images on the checkout page, never for scripts directly", () => {
    const csp = buildCsp({ ...base, checkout: true });
    const directive = (name: string) => csp.split("; ").find((d) => d.startsWith(`${name} `)) ?? "";
    expect(directive("frame-src")).toContain("https://checkout.razorpay.com");
    expect(directive("connect-src")).toContain("https://api.razorpay.com");
    expect(directive("img-src")).toContain("https://cdn.razorpay.com");
    expect(directive("script-src")).not.toContain("razorpay"); // the script is loaded by our nonce'd code (strict-dynamic)
    expect(directive("default-src")).toBe("default-src 'self'");
    expect(csp).not.toContain("*"); // no wildcards
    expect(csp).not.toContain("unsafe-inline");
  });

  it("only the pricing page counts as the checkout page", () => {
    expect(isCheckoutPath("/pricing")).toBe(true);
    for (const p of [
      "/",
      "/billing",
      "/pricing/x",
      "/pricing/",
      "/admin/billing",
      "/courses/pricing",
      "/PRICING",
    ])
      expect(isCheckoutPath(p), p).toBe(false);
  });

  it("adds upgrade-insecure-requests only over https and unsafe-eval only in dev", () => {
    expect(buildCsp({ ...base, secure: true, checkout: false })).toContain(
      "upgrade-insecure-requests",
    );
    expect(buildCsp({ ...base, checkout: false })).not.toContain("upgrade-insecure-requests");
    expect(buildCsp({ ...base, isDev: true, checkout: false })).toContain("'unsafe-eval'");
    expect(buildCsp({ ...base, checkout: false })).not.toContain("'unsafe-eval'");
  });
});
