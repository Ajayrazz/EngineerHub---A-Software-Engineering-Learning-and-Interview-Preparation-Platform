import { describe, expect, it } from "vitest";
import { counts, fromApi, LIMITS, move, newRow, sameTests, toApi, validateTests } from "./tests";

describe("test rows", () => {
  it("round-trips and keeps explanations on samples only", () => {
    const rows = fromApi([
      { input: "1 2", expected: "3", sample: true, explanation: "1 + 2" },
      { input: "9", expected: "9", sample: false, explanation: null },
    ]);
    expect(toApi(rows)).toEqual([
      { input: "1 2", expected: "3", sample: true, explanation: "1 + 2" },
      { input: "9", expected: "9", sample: false },
    ]);
    rows[1].explanation = "should be dropped";
    expect(toApi(rows)[1]).toEqual({ input: "9", expected: "9", sample: false });
    rows[0].explanation = "   ";
    expect(toApi(rows)[0]).toEqual({ input: "1 2", expected: "3", sample: true });
  });

  it("creates unique keys and counts samples vs hidden", () => {
    const a = newRow(true);
    const b = newRow();
    expect(a.key).not.toBe(b.key);
    expect(counts([a, b, newRow()])).toEqual({ sample: 1, hidden: 2 });
  });

  it("detects unsaved changes", () => {
    const a = { ...newRow(), input: "1", expected: "1" };
    expect(sameTests([a], [{ ...a, key: "other" }])).toBe(true);
    expect(sameTests([a], [{ ...a, expected: "2", key: "x" }])).toBe(false);
  });

  it("moves immutably and ignores out-of-range moves", () => {
    expect(move([1, 2, 3], 0, 1)).toEqual([2, 1, 3]);
    expect(move([1, 2, 3], 0, -1)).toEqual([1, 2, 3]);
    expect(move([1, 2, 3], 2, 1)).toEqual([1, 2, 3]);
  });

  it("validates like the server", () => {
    const ok = { ...newRow(), input: "1", expected: "1" };
    expect(validateTests([ok])).toEqual([]);
    expect(validateTests([{ ...ok, expected: "  " }])[0]).toMatch(/expected output is empty/);
    expect(validateTests([{ ...ok, input: "a\u0000" }])[0]).toMatch(/control character/);
    expect(validateTests([{ ...ok, expected: "x".repeat(LIMITS.chars + 1) }])[0]).toMatch(
      /too long/,
    );
    expect(validateTests([{ ...ok, input: "", sample: true, explanation: "n" }])).toEqual([]); // empty input is fine
    const samples = Array.from({ length: LIMITS.samples + 1 }, () => ({
      ...ok,
      key: Math.random().toString(),
      sample: true,
    }));
    expect(validateTests(samples).join()).toMatch(/sample tests/);
    const many = Array.from({ length: LIMITS.tests + 1 }, () => ({
      ...ok,
      key: Math.random().toString(),
    }));
    expect(validateTests(many).join()).toMatch(/At most 200/);
  });
});
