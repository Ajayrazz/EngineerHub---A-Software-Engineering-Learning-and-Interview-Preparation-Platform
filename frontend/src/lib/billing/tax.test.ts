import { describe, expect, it } from "vitest";
import { bpsToPercent, percentToBps, settingsProblems } from "./tax";

const full = {
  legalName: "Example Seller Pvt Ltd",
  address: "1 Example Road",
  state: "Example State",
  gstin: "22AAAAA0000A1Z5",
  sac: "999999",
};
const empty = { legalName: "", address: "", state: "", gstin: "", sac: "" };

describe("tax form helpers", () => {
  it("parses percentages into basis points exactly", () => {
    expect(percentToBps("18")).toBe(1800);
    expect(percentToBps("18.5")).toBe(1850);
    expect(percentToBps("0.01")).toBe(1);
    expect(percentToBps("50")).toBe(5000);
    expect(percentToBps(" 5.25 ")).toBe(525);
    for (const bad of ["", "0", "0.00", "50.01", "51", "-1", "1.234", "1,5", "abc", "100"])
      expect(percentToBps(bad), bad).toBeNull();
  });

  it("formats basis points as a percentage", () => {
    expect(bpsToPercent(1800)).toBe("18.00%");
    expect(bpsToPercent(525)).toBe("5.25%");
    expect(bpsToPercent(1)).toBe("0.01%");
  });

  it("requires nothing for UNCONFIGURED and DISABLED but still checks the shape of what is given", () => {
    expect(settingsProblems("UNCONFIGURED", empty, 0)).toEqual([]);
    expect(settingsProblems("DISABLED", empty, 0)).toEqual([]);
    expect(settingsProblems("DISABLED", { ...empty, gstin: "nope" }, 0)).toHaveLength(1);
    expect(settingsProblems("DISABLED", { ...empty, sac: "12" }, 0)).toHaveLength(1);
  });

  it("requires everything for ENABLED", () => {
    expect(settingsProblems("ENABLED", full, 1)).toEqual([]);
    expect(settingsProblems("ENABLED", empty, 1).join(" ")).toContain(
      "ENABLED needs the legal name",
    );
    expect(settingsProblems("ENABLED", full, 0).join(" ")).toContain(
      "at least one active tax rule",
    );
    expect(settingsProblems("ENABLED", { ...full, sac: "" }, 1)).toHaveLength(1);
  });
});
