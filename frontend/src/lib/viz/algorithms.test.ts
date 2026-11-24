import { describe, expect, it } from "vitest";
import { Recorder } from "./algorithms";
import {
  CATALOG,
  MAX_STEPS,
  VizInputError,
  entry,
  generate,
  inputProblem,
  shapeError,
  type ArrayFrame,
  type GraphFrame,
  type StackFrame,
  type Trace,
} from "./index";

/** A tiny seeded generator so the property tests are reproducible. */
function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
    return s / 2 ** 32;
  };
}
const ri = (r: () => number, lo: number, hi: number) => lo + Math.floor(r() * (hi - lo + 1));
const list = (r: () => number, n: number, lo = -50, hi = 99) =>
  Array.from({ length: n }, () => ri(r, lo, hi));
const csv = (a: number[]) => a.join(", ");
const arr = (t: Trace, i: number) => t.steps[i].frame as ArrayFrame;
const last = (t: Trace) => t.steps[t.steps.length - 1];
const values = (f: ArrayFrame) => f.cells.map((c) => c.value);
const sortedCopy = (a: number[]) => [...a].sort((x, y) => x - y);

describe("every trace", () => {
  it("is deterministic, bounded, and points at real pseudocode lines", () => {
    for (const e of CATALOG) {
      const a = generate(e.id, e.defaultInput, e.defaultParam);
      const b = generate(e.id, e.defaultInput, e.defaultParam);
      expect(JSON.stringify(a), e.id).toBe(JSON.stringify(b));
      expect(a.algorithm).toBe(e.id);
      expect(a.steps.length, e.id).toBeGreaterThanOrEqual(2);
      expect(a.steps.length, e.id).toBeLessThanOrEqual(MAX_STEPS);
      for (const s of a.steps) {
        expect(s.line, e.id).toBeGreaterThanOrEqual(0);
        expect(s.line, e.id).toBeLessThan(a.pseudo.length);
        expect(s.note.length, e.id).toBeGreaterThan(0);
      }
    }
  });

  it("refuses to record more steps than the ceiling, whatever produced them", () => {
    const r = new Recorder();
    const step = {
      frame: { kind: "array" as const, cells: [], pointers: [], stats: [] },
      note: "x",
      line: 0,
    };
    for (let i = 0; i < MAX_STEPS; i++) r.push(step);
    expect(() => r.push(step)).toThrow(VizInputError);
    expect(r.steps).toHaveLength(MAX_STEPS);
  });

  it("covers every catalog entry with a generator and rejects unknown ids", () => {
    expect(CATALOG.map((c) => c.id)).toHaveLength(10);
    expect(new Set(CATALOG.map((c) => c.id)).size).toBe(10);
    expect(() => generate("quick-sort", "1, 2", "")).toThrow(VizInputError);
    expect(entry("bfs")?.title).toContain("Breadth");
    expect(entry("nope")).toBeUndefined();
  });

  it("stays within the step ceiling for the worst allowed inputs", () => {
    const worst16 = csv(Array.from({ length: 16 }, (_, i) => 16 - i));
    for (const id of ["bubble-sort", "selection-sort", "insertion-sort"])
      expect(generate(id, worst16, "").steps.length, id).toBeLessThan(MAX_STEPS);
    expect(
      generate("merge-sort", csv(Array.from({ length: 12 }, (_, i) => 12 - i)), "").steps.length,
    ).toBeLessThan(MAX_STEPS);
  });
});

describe.each(["bubble-sort", "selection-sort", "insertion-sort", "merge-sort"])("%s", (id) => {
  const maxN = id === "merge-sort" ? 12 : 16;

  it("sorts every random input correctly and only ever rearranges the same numbers", () => {
    const r = rng(id.length * 7919);
    for (let t = 0; t < 120; t++) {
      const input = list(r, ri(r, 2, maxN));
      const trace = generate(id, csv(input), "");
      const final = arr(trace, trace.steps.length - 1);
      expect(values(final), csv(input)).toEqual(sortedCopy(input));
      expect(final.cells.every((c) => c.mark === "sorted")).toBe(true);
      for (let i = 0; i < trace.steps.length; i++) {
        const f = arr(trace, i);
        // the multiset of values never changes (merge sort works in a buffer until it copies back)
        expect(sortedCopy(values(f) as number[])).toEqual(sortedCopy(input));
      }
    }
  });

  it("handles equal numbers, a sorted list, a reversed list and two items", () => {
    for (const input of [
      [5, 5, 5, 5],
      [1, 2, 3, 4, 5],
      [5, 4, 3, 2, 1],
      [2, 1],
      [1, 2],
      [-3, 0, -3],
    ]) {
      const trace = generate(id, csv(input), "");
      expect(values(arr(trace, trace.steps.length - 1))).toEqual(sortedCopy(input));
    }
  });
});

describe("simple sorts", () => {
  it("a swap step marks exactly two cells, and a sorted input needs no swaps", () => {
    for (const id of ["bubble-sort", "selection-sort", "insertion-sort"]) {
      const r = rng(11);
      for (let t = 0; t < 40; t++) {
        const trace = generate(id, csv(list(r, ri(r, 2, 10))), "");
        for (const s of trace.steps) {
          const swaps = (s.frame as ArrayFrame).cells.filter((c) => c.mark === "swap").length;
          expect([0, 2]).toContain(swaps);
        }
      }
      const sorted = generate(id, "1, 2, 3, 4, 5", "");
      expect(
        sorted.steps.some((s) => (s.frame as ArrayFrame).cells.some((c) => c.mark === "swap")),
        id,
      ).toBe(false);
    }
  });

  it("bubble sort stops early when a pass makes no swaps", () => {
    const t = generate("bubble-sort", "1, 2, 3, 4, 5, 6, 7, 8", "");
    expect(last(t).note).toContain("sorted");
    expect(t.steps.length).toBeLessThan(30); // one pass of 7 comparisons, not seven passes
  });
});

describe("binary-search", () => {
  const reference = (a: number[], t: number) => a.indexOf(t);

  it("finds the target when it is there and says so when it is not, in at most log2(n)+1 looks", () => {
    const r = rng(42);
    for (let t = 0; t < 150; t++) {
      const a = sortedCopy(list(r, ri(r, 2, 16)));
      const target = r() < 0.5 ? a[ri(r, 0, a.length - 1)] : ri(r, -60, 110);
      const trace = generate("binary-search", csv(a), String(target));
      const looks = trace.steps.filter((s) => s.line === 2).length;
      expect(looks).toBeLessThanOrEqual(Math.floor(Math.log2(a.length)) + 1);
      const f = arr(trace, trace.steps.length - 1);
      const found = f.cells.findIndex((c) => c.mark === "found");
      if (reference(a, target) >= 0) {
        expect(found).toBeGreaterThanOrEqual(0);
        expect(a[found]).toBe(target);
      } else {
        expect(found).toBe(-1);
        expect(last(trace).note).toContain("not in the list");
      }
    }
  });

  it("refuses an unsorted list and a missing or non-numeric target", () => {
    expect(inputProblem("binary-search", "3, 1, 2", "2")).toContain("sorted");
    expect(inputProblem("binary-search", "1, 2, 3", "")).not.toBeNull();
    expect(inputProblem("binary-search", "1, 2, 3", "x")).not.toBeNull();
    expect(inputProblem("binary-search", "1, 2, 3", "2")).toBeNull();
    expect(inputProblem("binary-search", "2, 2, 2", "2")).toBeNull(); // equal neighbours are still sorted
  });
});

describe("two-pointers", () => {
  it("finds a pair exactly when one exists", () => {
    const r = rng(7);
    for (let t = 0; t < 150; t++) {
      const a = sortedCopy(list(r, ri(r, 2, 14)));
      const target = ri(r, -30, 150);
      const exists = a.some((x, i) => a.slice(i + 1).some((y) => x + y === target));
      const trace = generate("two-pointers", csv(a), String(target));
      const f = arr(trace, trace.steps.length - 1);
      const marked = f.cells.map((c, i) => (c.mark === "found" ? i : -1)).filter((i) => i >= 0);
      if (exists) {
        expect(marked).toHaveLength(2);
        expect(a[marked[0]] + a[marked[1]]).toBe(target);
      } else {
        expect(marked).toHaveLength(0);
        expect(last(trace).note).toContain("no two items");
      }
    }
  });

  it("refuses an unsorted list", () => {
    expect(inputProblem("two-pointers", "5, 1, 3", "4")).toContain("sorted");
  });
});

describe("sliding-window", () => {
  it("reports the largest sum of k items in a row", () => {
    const r = rng(99);
    for (let t = 0; t < 150; t++) {
      const a = list(r, ri(r, 2, 16));
      const k = ri(r, 1, a.length);
      let best = -Infinity;
      for (let i = 0; i + k <= a.length; i++)
        best = Math.max(
          best,
          a.slice(i, i + k).reduce((x, y) => x + y, 0),
        );
      const trace = generate("sliding-window", csv(a), String(k));
      const f = arr(trace, trace.steps.length - 1);
      expect(f.stats.find((s) => s.label === "best")?.value).toBe(String(best));
      const marked = f.cells.map((c, i) => (c.mark === "found" ? i : -1)).filter((i) => i >= 0);
      expect(marked).toHaveLength(k);
      expect(marked.map((i) => a[i]).reduce((x, y) => x + y, 0)).toBe(best);
      expect(marked[marked.length - 1] - marked[0]).toBe(k - 1); // consecutive
    }
  });

  it("keeps the running sum honest at every step", () => {
    const a = [2, 1, 5, 1, 3, 2, 8, 1];
    const trace = generate("sliding-window", csv(a), "3");
    for (const s of trace.steps.slice(0, -1)) {
      const f = s.frame as ArrayFrame;
      const win = f.cells
        .map((c, i) => (c.mark === "window" || c.mark === "active" ? i : -1))
        .filter((i) => i >= 0);
      const sum = Number(f.stats.find((x) => x.label === "window sum")!.value);
      const inWindow = f.cells.map((c, i) =>
        c.mark === "window" || c.mark === "active" ? a[i] : 0,
      );
      expect(inWindow.reduce((x, y) => x + y, 0)).toBe(sum);
      expect(win.length).toBe(3);
    }
  });

  it("rejects a window size outside 1..n", () => {
    expect(inputProblem("sliding-window", "1, 2, 3", "0")).not.toBeNull();
    expect(inputProblem("sliding-window", "1, 2, 3", "4")).toContain("1 to 3");
    expect(inputProblem("sliding-window", "1, 2, 3", "3")).toBeNull();
    expect(inputProblem("sliding-window", "1, 2, 3", "1")).toBeNull();
  });
});

describe("valid-parentheses", () => {
  const reference = (s: string) => {
    const st: string[] = [];
    const pair: Record<string, string> = { ")": "(", "]": "[", "}": "{" };
    for (const c of s) {
      if ("([{".includes(c)) st.push(c);
      else if (st.pop() !== pair[c]) return false;
    }
    return st.length === 0;
  };

  it("agrees with a reference checker on random strings", () => {
    const r = rng(5);
    const alphabet = "()[]{}";
    for (let t = 0; t < 300; t++) {
      const s = Array.from({ length: ri(r, 1, 12) }, () => alphabet[ri(r, 0, 5)]).join("");
      const f = last(generate("valid-parentheses", s, "")).frame as StackFrame;
      expect(f.verdict, s).toBe(reference(s) ? "balanced" : "unbalanced");
    }
  });

  it("handles the classic cases", () => {
    for (const [s, ok] of [
      ["()", true],
      ["()[]{}", true],
      ["{[()]}", true],
      ["(]", false],
      ["(", false],
      [")", false],
      ["([)]", false],
    ] as const) {
      expect((last(generate("valid-parentheses", s, "")).frame as StackFrame).verdict, s).toBe(
        ok ? "balanced" : "unbalanced",
      );
    }
  });

  it("stops at the first mismatch and shows the stack as it was", () => {
    const t = generate("valid-parentheses", "(]()", "");
    const f = last(t).frame as StackFrame;
    expect(f.verdict).toBe("unbalanced");
    expect(f.stack).toEqual(["("]);
    expect(f.at).toBe(1);
  });

  it("rejects anything but brackets", () => {
    expect(shapeError("valid-parentheses", "(a)", "")).not.toBeNull();
    expect(shapeError("valid-parentheses", "", "")).not.toBeNull();
    expect(shapeError("valid-parentheses", "(".repeat(25), "")).not.toBeNull();
    expect(shapeError("valid-parentheses", "(".repeat(24), "")).toBeNull();
  });
});

describe("bfs", () => {
  const reference = (edges: [number, number][], start: number) => {
    const adj = new Map<number, number[]>();
    for (const [a, b] of edges) {
      adj.set(a, [...(adj.get(a) ?? []), b]);
      adj.set(b, [...(adj.get(b) ?? []), a]);
    }
    const seen = new Set([start]);
    const q = [start];
    const order: number[] = [];
    while (q.length) {
      const n = q.shift()!;
      order.push(n);
      for (const m of [...(adj.get(n) ?? [])].sort((x, y) => x - y))
        if (!seen.has(m)) {
          seen.add(m);
          q.push(m);
        }
    }
    return order;
  };

  it("visits nodes in the same order as a reference BFS on random graphs", () => {
    const r = rng(2024);
    for (let t = 0; t < 150; t++) {
      const edges: [number, number][] = [];
      for (let i = 0; i < ri(r, 1, 12); i++) edges.push([ri(r, 0, 7), ri(r, 0, 7)]);
      const real = edges.filter(([a, b]) => a !== b);
      if (real.length === 0) continue;
      const start = real[ri(r, 0, real.length - 1)][0];
      const input = real.map(([a, b]) => `${a}-${b}`).join(", ");
      const g = last(generate("bfs", input, String(start))).frame as GraphFrame;
      expect(g.order, `${input} from ${start}`).toEqual(reference(real, start));
      expect(g.queue).toEqual([]);
      for (const n of g.nodes) expect(n.state === "done" || n.state === "unseen").toBe(true);
      expect(
        g.nodes
          .filter((n) => n.state === "done")
          .map((n) => n.id)
          .sort(),
      ).toEqual([...g.order].sort());
    }
  });

  it("never queues a node twice and keeps the queue in first-come order", () => {
    const t = generate("bfs", "0-1, 0-2, 1-3, 2-3, 3-4", "0");
    const everQueued: number[] = [];
    let prevQueue: number[] = [];
    for (const s of t.steps) {
      const g = s.frame as GraphFrame;
      for (const id of g.queue)
        if (!prevQueue.includes(id) && !g.order.includes(id)) everQueued.push(id);
      prevQueue = g.queue;
    }
    expect(new Set(everQueued).size).toBe(everQueued.length);
  });

  it("only reaches the connected part of the graph and ignores duplicate and self edges", () => {
    const g = last(generate("bfs", "0-1, 1-0, 2-2, 3-4", "0")).frame as GraphFrame;
    expect(g.order).toEqual([0, 1]);
    expect(g.edges).toEqual([
      [0, 1],
      [3, 4],
    ]);
  });

  it("validates the graph and the start node", () => {
    expect(inputProblem("bfs", "0-1", "1")).toBeNull();
    expect(inputProblem("bfs", "0-8", "0")).not.toBeNull();
    expect(inputProblem("bfs", "0-1", "8")).not.toBeNull();
    expect(inputProblem("bfs", "0-1", "5")).toContain("part of at least one edge");
    expect(inputProblem("bfs", "2-2", "2")).not.toBeNull(); // only a self edge
    expect(inputProblem("bfs", "0-1,", "0")).not.toBeNull();
  });
});

describe("fibonacci-dp", () => {
  it("fills the table with the Fibonacci numbers for every n from 0 to 30", () => {
    const fib = [0, 1];
    for (let i = 2; i <= 30; i++) fib.push(fib[i - 1] + fib[i - 2]);
    for (let n = 0; n <= 30; n++) {
      const f = arr(
        generate("fibonacci-dp", String(n), ""),
        generate("fibonacci-dp", String(n), "").steps.length - 1,
      );
      expect(values(f)).toEqual(fib.slice(0, n + 1));
      expect(f.cells[n].mark).toBe("found");
    }
  });

  it("shows unfilled cells as empty until they are computed", () => {
    const t = generate("fibonacci-dp", "5", "");
    expect(values(arr(t, 0))).toEqual([0, 1, null, null, null, null]);
    expect(values(arr(t, 1))).toEqual([0, 1, 1, null, null, null]);
  });

  it("rejects out-of-range input", () => {
    expect(inputProblem("fibonacci-dp", "31", "")).not.toBeNull();
    expect(inputProblem("fibonacci-dp", "-1", "")).not.toBeNull();
    expect(inputProblem("fibonacci-dp", "abc", "")).not.toBeNull();
    expect(inputProblem("fibonacci-dp", "30", "")).toBeNull();
  });
});

describe("input shape (what the server also enforces)", () => {
  it("rejects too few, too many and non-numeric values", () => {
    expect(shapeError("bubble-sort", "5", "")).not.toBeNull();
    expect(
      shapeError("bubble-sort", csv(Array.from({ length: 17 }, (_, i) => i)), ""),
    ).not.toBeNull();
    expect(shapeError("bubble-sort", csv(Array.from({ length: 16 }, (_, i) => i)), "")).toBeNull();
    expect(
      shapeError("merge-sort", csv(Array.from({ length: 13 }, (_, i) => i)), ""),
    ).not.toBeNull();
    expect(shapeError("bubble-sort", "1, two, 3", "")).not.toBeNull();
    expect(shapeError("bubble-sort", "1, 2.5, 3", "")).not.toBeNull();
    expect(shapeError("bubble-sort", "1, 2, 3, ", "")).not.toBeNull();
    expect(shapeError("bubble-sort", "1000, 2", "")).not.toBeNull(); // more than 3 digits
    expect(shapeError("bubble-sort", "<script>", "")).not.toBeNull();
  });

  it("rejects a value for an algorithm that takes none, and requires one where it is needed", () => {
    expect(shapeError("bubble-sort", "2, 1", "5")).not.toBeNull();
    expect(shapeError("binary-search", "1, 2", "")).not.toBeNull();
    expect(shapeError("nope", "1, 2", "")).not.toBeNull();
  });
});
