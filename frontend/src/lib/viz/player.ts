/** Pure state machine of the step player (VIZ-001). No timers here: the component calls `tick` on an interval. */
export const SPEEDS = [0.5, 1, 2] as const;
export type Speed = (typeof SPEEDS)[number];

export type PlayerState = { index: number; playing: boolean; speed: Speed };

export const BASE_DELAY_MS = 1000;

export function initial(): PlayerState {
  return { index: 0, playing: false, speed: 1 };
}

const clamp = (i: number, total: number) => Math.max(0, Math.min(total - 1, Math.trunc(i)));

export function next(s: PlayerState, total: number): PlayerState {
  const index = clamp(s.index + 1, total);
  return { ...s, index, playing: s.playing && index < total - 1 };
}

export function prev(s: PlayerState, total: number): PlayerState {
  return { ...s, index: clamp(s.index - 1, total), playing: false };
}

export function restart(s: PlayerState): PlayerState {
  return { ...s, index: 0, playing: false };
}

/** Moving the scrubber pauses playback. */
export function scrub(s: PlayerState, to: number, total: number): PlayerState {
  return { ...s, index: clamp(to, total), playing: false };
}

/** Play from the current step; pressing play on the last step starts again from the first. */
export function toggle(s: PlayerState, total: number): PlayerState {
  if (total <= 1) return { ...s, playing: false };
  if (s.playing) return { ...s, playing: false };
  return s.index >= total - 1 ? { ...s, index: 0, playing: true } : { ...s, playing: true };
}

/** One timer tick while playing: advance, and stop on the last step. */
export function tick(s: PlayerState, total: number): PlayerState {
  if (!s.playing) return s;
  return next(s, total);
}

export function setSpeed(s: PlayerState, speed: Speed): PlayerState {
  return { ...s, speed };
}

export function delayMs(speed: Speed): number {
  return Math.round(BASE_DELAY_MS / speed);
}

export function atEnd(s: PlayerState, total: number): boolean {
  return s.index >= total - 1;
}
