/** Places graph nodes evenly on a circle: deterministic, so a trace always draws the same picture. */
export const GRAPH_SIZE = 220;

export function circleLayout(ids: readonly number[]): Map<number, { x: number; y: number }> {
  const c = GRAPH_SIZE / 2;
  const radius = c - 28;
  const out = new Map<number, { x: number; y: number }>();
  ids.forEach((id, i) => {
    const angle = (2 * Math.PI * i) / Math.max(ids.length, 1) - Math.PI / 2;
    out.set(id, { x: round(c + radius * Math.cos(angle)), y: round(c + radius * Math.sin(angle)) });
  });
  return out;
}

const round = (v: number) => Math.round(v * 10) / 10;
