import { describe, expect, it } from "vitest";
import { buildGrid, level, plural } from "./heatmap";

describe("heatmap", () => {
  it("maps counts to five levels", () => {
    expect([0, 1, 2, 3, 4, 5, 20].map(level)).toEqual([0, 1, 2, 3, 3, 4, 4]);
    expect(level(-1)).toBe(0);
  });

  it("lays out Sunday-first weeks ending with the week of today", () => {
    // 2026-03-11 is a Wednesday
    const grid = buildGrid([], "2026-03-11", 3);
    expect(grid).toHaveLength(3);
    expect(grid.every((c) => c.length === 7)).toBe(true);
    const last = grid[2];
    expect(last[0].day).toBe("2026-03-08"); // Sunday of that week
    expect(last[3].day).toBe("2026-03-11");
    expect(last[3].future).toBe(false);
    expect(last[4].future).toBe(true);
    expect(grid[0][0].day).toBe("2026-02-22");
  });

  it("places activity on its day and sums duplicates", () => {
    const grid = buildGrid(
      [
        { day: "2026-03-10", lessonsCompleted: 2 },
        { day: "2026-03-10", lessonsCompleted: 1 },
        { day: "2026-03-11", lessonsCompleted: 1 },
        { day: "1999-01-01", lessonsCompleted: 9 },
      ],
      "2026-03-11",
      2,
    );
    const flat = grid.flat();
    expect(flat.find((c) => c.day === "2026-03-10")).toMatchObject({ count: 3, level: 3 });
    expect(flat.find((c) => c.day === "2026-03-11")).toMatchObject({ count: 1, level: 1 });
    expect(flat.some((c) => c.day === "1999-01-01")).toBe(false);
  });

  it("is not affected by month and year boundaries", () => {
    const grid = buildGrid([{ day: "2026-01-01", lessonsCompleted: 1 }], "2026-01-02", 2).flat();
    expect(grid.find((c) => c.day === "2026-01-01")?.count).toBe(1);
    expect(grid.map((c) => c.day)).toContain("2025-12-28");
  });

  it("pluralises", () => {
    expect(plural(1, "lesson")).toBe("1 lesson");
    expect(plural(0, "lesson")).toBe("0 lessons");
    expect(plural(2, "day")).toBe("2 days");
  });
});
