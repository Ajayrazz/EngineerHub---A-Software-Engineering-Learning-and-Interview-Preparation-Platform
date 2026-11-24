import { renderToStaticMarkup } from "react-dom/server";
import { describe, expect, it } from "vitest";
import { generate } from "@/lib/viz";
import { FrameView } from "./frames";

const render = (id: string, input: string, param: string, step: number) => {
  const t = generate(id, input, param);
  return renderToStaticMarkup(
    <FrameView frame={t.steps[Math.min(step, t.steps.length - 1)].frame} />,
  );
};

describe("visualization frames", () => {
  it("draws every array value with its mark and pointer, in a readable order", () => {
    const html = render("binary-search", "1, 3, 5, 7, 9", "7", 1);
    expect(html).toContain('data-mark="compare"'); // looking at the middle item
    expect(html).not.toContain('data-mark="dim"'); // nothing discarded yet
    expect(render("binary-search", "1, 3, 5, 7, 9", "7", 2)).toContain('data-mark="dim"'); // left half discarded
    expect(html).toContain("target:");
    for (const v of ["1", "3", "5", "7", "9"]) expect(html).toContain(`>${v}</span>`);
    expect(html).toContain("mid"); // pointer label
    expect(html).toContain("sr-only"); // a text equivalent exists for every cell
  });

  it("shows empty DP cells as a dot", () => {
    const html = render("fibonacci-dp", "5", "", 0);
    expect(html).toContain(">·</span>");
    expect(html).toContain('data-mark="dim"');
  });

  it("draws the merge buffer under the array", () => {
    const html = render("merge-sort", "4, 3, 2, 1", "", 4);
    expect(html).toContain("merge buffer");
  });

  it("draws the stack top first with a verdict at the end", () => {
    const first = render("valid-parentheses", "([", "", 2);
    expect(first).toContain("Stack (top first)");
    const end = render("valid-parentheses", "()", "", 99);
    expect(end).toContain("Balanced");
    expect(render("valid-parentheses", "(]", "", 99)).toContain("Not balanced");
  });

  it("draws the graph with node states, the queue and the visit order", () => {
    const html = render("bfs", "0-1, 0-2, 1-3", "0", 99);
    expect(html).toContain('data-state="done"');
    expect(html).toContain("<svg");
    expect(html).toContain("Visited, in order");
    expect(html).toContain("0, 1, 2, 3");
    expect(render("bfs", "0-1, 0-2", "0", 0)).toContain('data-state="queued"');
  });

  it("never emits markup other than the fixed elements, whatever the numbers", () => {
    const html = render("bubble-sort", "-999, 999, 0", "", 3);
    expect(html).not.toMatch(/<script|onerror|javascript:/i);
  });
});
