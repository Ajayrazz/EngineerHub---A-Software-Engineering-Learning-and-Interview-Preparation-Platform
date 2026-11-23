/**
 * Pure layout of the activity grid (no React, no time zone logic): the server tells us "today" in the user's
 * own time zone, and all date arithmetic here is done on calendar days in UTC so the browser's zone and DST
 * can never shift a cell.
 */
export type Cell = { day: string; count: number; level: 0 | 1 | 2 | 3 | 4; future: boolean };

const DAY_MS = 86_400_000;

const parse = (day: string) => {
  const [y, m, d] = day.split("-").map(Number);
  return Date.UTC(y, m - 1, d);
};
const format = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export function level(count: number): 0 | 1 | 2 | 3 | 4 {
  if (count <= 0) return 0;
  if (count === 1) return 1;
  if (count === 2) return 2;
  if (count <= 4) return 3;
  return 4;
}

/**
 * `weeks` columns of 7 cells (Sunday first), the last column being the week of `today`. Cells after today are
 * marked `future` so they render empty.
 */
export function buildGrid(
  activity: readonly { day?: string; lessonsCompleted?: number }[],
  today: string,
  weeks = 16,
): Cell[][] {
  const counts = new Map<string, number>();
  for (const a of activity)
    if (a.day) counts.set(a.day, (counts.get(a.day) ?? 0) + (a.lessonsCompleted ?? 0));
  const todayMs = parse(today);
  const dow = new Date(todayMs).getUTCDay(); // 0 = Sunday
  const lastColumnStart = todayMs - dow * DAY_MS;
  const columns: Cell[][] = [];
  for (let w = weeks - 1; w >= 0; w--) {
    const start = lastColumnStart - w * 7 * DAY_MS;
    const column: Cell[] = [];
    for (let d = 0; d < 7; d++) {
      const ms = start + d * DAY_MS;
      const day = format(ms);
      const count = counts.get(day) ?? 0;
      column.push({ day, count, level: level(count), future: ms > todayMs });
    }
    columns.push(column);
  }
  return columns;
}

export function plural(n: number, one: string, many = `${one}s`): string {
  return `${n} ${n === 1 ? one : many}`;
}
