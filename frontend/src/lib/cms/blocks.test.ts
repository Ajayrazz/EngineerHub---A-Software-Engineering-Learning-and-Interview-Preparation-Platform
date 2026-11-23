import { describe, expect, it } from "vitest";
import {
  fromApi,
  LIMITS,
  move,
  newBlock,
  remove,
  sameBlocks,
  toApi,
  validateBlocks,
} from "./blocks";

describe("blocks", () => {
  it("creates defaults for each type with unique keys", () => {
    const a = newBlock("markdown");
    const b = newBlock("markdown");
    expect(a.key).not.toBe(b.key);
    expect(newBlock("code").data.language).toBe("java");
    expect(newBlock("callout").data.kind).toBe("info");
  });

  it("round-trips through the API shape and drops empty captions and unknown keys", () => {
    const api = [
      { type: "markdown", data: { text: "hi", extra: "x" } },
      { type: "code", data: { language: "python", code: "print(1)", caption: "" } },
      { type: "callout", data: { kind: "tip", text: "t" } },
      { type: "unknown", data: {} },
    ];
    const blocks = fromApi(api);
    expect(blocks).toHaveLength(3);
    expect(toApi(blocks)).toEqual([
      { type: "markdown", data: { text: "hi" } },
      { type: "code", data: { language: "python", code: "print(1)" } },
      { type: "callout", data: { kind: "tip", text: "t" } },
    ]);
  });

  it("moves and removes immutably, ignoring out-of-range moves", () => {
    const items = [1, 2, 3];
    expect(move(items, 0, 1)).toEqual([2, 1, 3]);
    expect(move(items, 0, -1)).toEqual([1, 2, 3]);
    expect(move(items, 2, 1)).toEqual([1, 2, 3]);
    expect(remove(items, 1)).toEqual([1, 3]);
    expect(items).toEqual([1, 2, 3]);
  });

  it("validates like the server", () => {
    const md = newBlock("markdown");
    expect(validateBlocks([md])[0]).toMatch(/empty/);
    md.data.text = "ok";
    expect(validateBlocks([md])).toEqual([]);
    md.data.text = "x".repeat(LIMITS.markdown + 1);
    expect(validateBlocks([md])[0]).toMatch(/too long/);
    md.data.text = "a\u0000b";
    expect(validateBlocks([md])[0]).toMatch(/control/);
    const code = newBlock("code");
    code.data.code = "int x;";
    code.data.language = "ruby";
    expect(validateBlocks([code])[0]).toMatch(/language/);
    const callout = newBlock("callout");
    callout.data.text = "t";
    callout.data.kind = "danger";
    expect(validateBlocks([callout])[0]).toMatch(/kind/);
  });

  it("accepts newlines and tabs but caps the block count", () => {
    const md = newBlock("markdown");
    md.data.text = "line1\n\tline2\r\n";
    expect(validateBlocks([md])).toEqual([]);
    const many = Array.from({ length: LIMITS.blocks + 1 }, () => ({
      ...md,
      key: Math.random().toString(),
    }));
    expect(validateBlocks(many).join()).toMatch(/At most/);
  });

  it("detects unsaved changes", () => {
    const a = newBlock("markdown");
    a.data.text = "x";
    const b = { ...a, key: "other", data: { text: "x" } };
    expect(sameBlocks([a], [b])).toBe(true);
    expect(sameBlocks([a], [{ ...b, data: { text: "y" } }])).toBe(false);
  });
});
