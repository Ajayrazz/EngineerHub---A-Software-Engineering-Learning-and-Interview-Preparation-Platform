import { describe, expect, it } from "vitest";
import {
  atEnd,
  delayMs,
  initial,
  next,
  prev,
  restart,
  scrub,
  setSpeed,
  tick,
  toggle,
} from "./player";

describe("step player", () => {
  it("starts paused on the first step at normal speed", () => {
    expect(initial()).toEqual({ index: 0, playing: false, speed: 1 });
  });

  it("steps forward and back without leaving the trace", () => {
    let s = initial();
    s = next(s, 3);
    s = next(s, 3);
    s = next(s, 3);
    expect(s.index).toBe(2);
    s = prev(prev(prev(s, 3), 3), 3);
    expect(s.index).toBe(0);
  });

  it("stepping by hand pauses playback going back, and finishing stops it going forward", () => {
    let s = { ...initial(), playing: true, index: 1 };
    expect(prev(s, 4).playing).toBe(false);
    s = next(s, 3); // reaches the last step
    expect(s.index).toBe(2);
    expect(s.playing).toBe(false);
  });

  it("plays, ticks forward, and stops by itself on the last step", () => {
    let s = toggle(initial(), 3);
    expect(s.playing).toBe(true);
    s = tick(s, 3);
    expect(s.index).toBe(1);
    expect(s.playing).toBe(true);
    s = tick(s, 3);
    expect(s).toMatchObject({ index: 2, playing: false });
    expect(tick(s, 3)).toBe(s); // ticks while paused do nothing
  });

  it("pressing play on the last step starts over", () => {
    const s = toggle({ ...initial(), index: 2 }, 3);
    expect(s).toMatchObject({ index: 0, playing: true });
    expect(atEnd({ ...initial(), index: 2 }, 3)).toBe(true);
    expect(atEnd(initial(), 3)).toBe(false);
  });

  it("pauses when play is pressed again, and never plays a one-step trace", () => {
    expect(toggle({ ...initial(), playing: true }, 5).playing).toBe(false);
    expect(toggle(initial(), 1).playing).toBe(false);
  });

  it("scrubs to any step, clamps, and pauses", () => {
    const playing = { ...initial(), playing: true };
    expect(scrub(playing, 5, 10)).toMatchObject({ index: 5, playing: false });
    expect(scrub(playing, -4, 10).index).toBe(0);
    expect(scrub(playing, 99, 10).index).toBe(9);
    expect(scrub(playing, 2.9, 10).index).toBe(2);
  });

  it("restarts at the first step, paused, keeping the speed", () => {
    const s = restart({ index: 7, playing: true, speed: 2 });
    expect(s).toEqual({ index: 0, playing: false, speed: 2 });
  });

  it("maps speed to the delay between steps", () => {
    expect(delayMs(0.5)).toBe(2000);
    expect(delayMs(1)).toBe(1000);
    expect(delayMs(2)).toBe(500);
    expect(setSpeed(initial(), 2).speed).toBe(2);
  });
});
