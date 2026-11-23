import { describe, expect, it } from "vitest";
import { extractHeadings, headingSlug, idMaker, plainHeading } from "./toc";

describe("toc", () => {
  it("slugs headings predictably", () => {
    expect(headingSlug("Using `map` & filter!")).toBe("using-map-filter");
    expect(headingSlug("???")).toBe("section");
  });

  it("strips inline markdown from heading text", () => {
    expect(plainHeading("Using `map` and **filter**")).toBe("Using map and filter");
    expect(plainHeading("See [the docs](https://x.example) ##")).toBe("See the docs");
  });

  it("lists h1-h3 with prefixed, de-duplicated ids and ignores code fences", () => {
    const md =
      "# Title\n\n## Setup\n\ntext\n\n```md\n## not a heading\n```\n\n### Setup\n\n#### deep\n\n## Setup";
    expect(extractHeadings(md, "b0")).toEqual([
      { level: 1, id: "b0-title", text: "Title" },
      { level: 2, id: "b0-setup", text: "Setup" },
      { level: 3, id: "b0-setup-1", text: "Setup" },
      { level: 2, id: "b0-setup-2", text: "Setup" },
    ]);
  });

  it("uses the same id sequence as the renderer's generator", () => {
    const make = idMaker("b1");
    expect([make("A"), make("A"), make("B")]).toEqual(["b1-a", "b1-a-1", "b1-b"]);
  });
});
