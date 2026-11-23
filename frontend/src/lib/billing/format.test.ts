import { describe, expect, it } from "vitest";
import { describePrice, formatDate, formatInr, toMinor } from "./format";

describe("billing format", () => {
  it("formats minor units as rupees with Indian grouping", () => {
    expect(formatInr(29900).replace(/\s/g, "")).toBe("₹299.00");
    expect(formatInr(299900).replace(/\s/g, "")).toBe("₹2,999.00");
    expect(formatInr(10000000).replace(/\s/g, "")).toBe("₹1,00,000.00");
  });

  it("describes price, unit and mode", () => {
    expect(
      describePrice({
        billingMode: "ONE_TIME",
        periodUnit: "month",
        amountInclTaxMinor: 29900,
      }).replace(/\s/g, ""),
    ).toBe("₹299.00permonth,one-timepass");
    expect(
      describePrice({ billingMode: "RECURRING", periodUnit: "year", amountInclTaxMinor: 299900 }),
    ).toContain("auto-renewing");
  });

  it("parses rupee input into integer minor units exactly", () => {
    expect(toMinor("299")).toBe(29900);
    expect(toMinor("299.5")).toBe(29950);
    expect(toMinor("299.99")).toBe(29999);
    expect(toMinor(" 1 ")).toBe(100);
    expect(toMinor("0.10")).toBe(10);
    expect(toMinor("19.99")).toBe(1999); // would be 1998.9999... with floating point
  });

  it("rejects anything that is not a plain amount", () => {
    for (const bad of ["", "abc", "-5", "1.234", "1,000", "1e5", "99999999", "₹5", ".5"])
      expect(toMinor(bad), bad).toBeNull();
  });

  it("formats dates defensively", () => {
    expect(formatDate("2026-03-10T12:00:00Z")).toBe("2026-03-10");
    expect(formatDate(undefined)).toBe("—");
    expect(formatDate("not a date")).toBe("—");
  });
});
