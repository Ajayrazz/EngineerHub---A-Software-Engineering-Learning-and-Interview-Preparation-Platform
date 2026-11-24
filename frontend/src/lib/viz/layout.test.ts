import { describe, expect, it } from "vitest";
import { GRAPH_SIZE, circleLayout } from "./layout";

describe("circle layout", () => {
  it("is deterministic and keeps every node inside the drawing", () => {
    const a = circleLayout([0, 1, 2, 3, 4, 5, 6, 7]);
    const b = circleLayout([0, 1, 2, 3, 4, 5, 6, 7]);
    expect([...a]).toEqual([...b]);
    for (const { x, y } of a.values()) {
      expect(x).toBeGreaterThan(0);
      expect(x).toBeLessThan(GRAPH_SIZE);
      expect(y).toBeGreaterThan(0);
      expect(y).toBeLessThan(GRAPH_SIZE);
    }
  });

  it("puts the first node at the top and gives every node its own place", () => {
    const l = circleLayout([3, 5, 7]);
    expect(l.get(3)).toEqual({ x: GRAPH_SIZE / 2, y: 28 });
    expect(new Set([...l.values()].map((p) => `${p.x},${p.y}`)).size).toBe(3);
  });

  it("copes with one node and none", () => {
    expect(circleLayout([4]).size).toBe(1);
    expect(circleLayout([]).size).toBe(0);
  });
});
