"use client";

import { useEffect, useMemo, useState, type KeyboardEvent } from "react";
import { btn, input as inputClass } from "@/components/admin/ui";
import { FrameView } from "@/components/viz/frames";
import { entry, generate, inputProblem, VizInputError, type Trace } from "@/lib/viz";
import {
  SPEEDS,
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
  type PlayerState,
  type Speed,
} from "@/lib/viz/player";

type Built = { ok: true; trace: Trace } | { ok: false; error: string };

function build(algorithm: string, input: string, param: string): Built {
  try {
    return { ok: true, trace: generate(algorithm, input, param) };
  } catch (e) {
    if (e instanceof VizInputError) return { ok: false, error: e.message };
    throw e;
  }
}

/**
 * Step player for a deterministic trace (VIZ-001): play and pause, restart, step back and forward, speed and a scrubber.
 * The trace is computed in the browser from the algorithm and a small validated input; nothing is fetched. Learners may
 * try their own input within the same limits the server enforces for lessons (VIZ-003).
 */
export function VizPlayer({
  algorithm,
  input,
  param = "",
  allowCustom = false,
}: {
  algorithm: string;
  input: string;
  param?: string;
  allowCustom?: boolean;
}) {
  const [custom, setCustom] = useState<{ input: string; param: string } | null>(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState({ input, param });
  const [draftError, setDraftError] = useState<string | null>(null);
  const meta = entry(algorithm);
  const effective = custom ?? { input, param };
  const built = useMemo(
    () => build(algorithm, effective.input, effective.param),
    [algorithm, effective.input, effective.param],
  );

  if (!meta)
    return (
      <p role="alert" className="text-sm">
        This visualization is not available.
      </p>
    );
  return (
    <section
      className="flex flex-col gap-3 rounded border p-4"
      aria-label={`${meta.title} visualization`}
      data-testid="viz"
    >
      <header>
        <h3 className="text-base font-semibold">{meta.title}</h3>
        <p className="text-sm opacity-80">{meta.summary}</p>
      </header>

      {built.ok ? (
        <PlayerBody
          key={`${algorithm}|${effective.input}|${effective.param}`}
          trace={built.trace}
        />
      ) : (
        <p role="alert" className="text-sm" data-testid="viz-error">
          {built.error}
        </p>
      )}

      {allowCustom && (
        <div className="flex flex-col gap-2 border-t pt-3 text-sm">
          {!editing ? (
            <div className="flex flex-wrap items-center gap-2">
              <button
                className={btn}
                onClick={() => {
                  setDraft(effective);
                  setEditing(true);
                }}
                data-testid="viz-custom-open"
              >
                Try your own input
              </button>
              {custom && (
                <button
                  className={btn}
                  onClick={() => setCustom(null)}
                  data-testid="viz-custom-reset"
                >
                  Back to the example
                </button>
              )}
            </div>
          ) : (
            <form
              className="flex flex-wrap items-end gap-2"
              onSubmit={(e) => {
                e.preventDefault();
                const problem = inputProblem(algorithm, draft.input, draft.param);
                setDraftError(problem);
                if (!problem) {
                  setCustom({ input: draft.input.trim(), param: draft.param.trim() });
                  setEditing(false);
                }
              }}
            >
              <label className="flex flex-col gap-1">
                {meta.inputLabel}
                <input
                  className={`${inputClass} w-72`}
                  name="viz-input"
                  value={draft.input}
                  maxLength={120}
                  onChange={(e) => setDraft({ ...draft, input: e.target.value })}
                />
              </label>
              {meta.paramLabel && (
                <label className="flex flex-col gap-1">
                  {meta.paramLabel}
                  <input
                    className={`${inputClass} w-28`}
                    name="viz-param"
                    value={draft.param}
                    maxLength={10}
                    onChange={(e) => setDraft({ ...draft, param: e.target.value })}
                  />
                </label>
              )}
              <button className={btn} type="submit" data-testid="viz-custom-apply">
                Show
              </button>
              <button
                className={btn}
                type="button"
                onClick={() => {
                  setEditing(false);
                  setDraftError(null);
                }}
              >
                Cancel
              </button>
              {draftError && (
                <p role="alert" className="basis-full text-sm" data-testid="viz-custom-error">
                  {draftError}
                </p>
              )}
            </form>
          )}
        </div>
      )}
    </section>
  );
}

function PlayerBody({ trace }: { trace: Trace }) {
  const total = trace.steps.length;
  const [state, setState] = useState<PlayerState>(initial());
  const step = trace.steps[state.index];

  useEffect(() => {
    if (!state.playing) return;
    const id = setInterval(() => setState((s) => tick(s, total)), delayMs(state.speed));
    return () => clearInterval(id);
  }, [state.playing, state.speed, total]);

  function onKey(e: KeyboardEvent<HTMLDivElement>) {
    if (e.target !== e.currentTarget) return; // never steal keys from the inputs inside
    if (e.key === "ArrowRight") setState((s) => next(s, total));
    else if (e.key === "ArrowLeft") setState((s) => prev(s, total));
    else if (e.key === "Home") setState(restart);
    else if (e.key === " ") setState((s) => toggle(s, total));
    else return;
    e.preventDefault();
  }

  return (
    <div
      className="flex flex-col gap-3"
      tabIndex={0}
      onKeyDown={onKey}
      aria-label="Visualization player. Use the arrow keys to step and the space bar to play or pause."
      data-testid="viz-player"
    >
      <div className="overflow-x-auto py-1">
        <FrameView frame={step.frame} />
      </div>
      <p
        role="status"
        aria-live="polite"
        className="min-h-6 text-sm font-medium"
        data-testid="viz-note"
      >
        {step.note}
      </p>

      <div
        className="flex flex-wrap items-center gap-2"
        role="group"
        aria-label="Playback controls"
      >
        <button
          className={btn}
          onClick={() => setState(restart)}
          aria-label="Restart"
          data-testid="viz-restart"
        >
          ⏮
        </button>
        <button
          className={btn}
          onClick={() => setState((s) => prev(s, total))}
          disabled={state.index === 0}
          aria-label="Previous step"
          data-testid="viz-prev"
        >
          ◀
        </button>
        <button
          className={btn}
          onClick={() => setState((s) => toggle(s, total))}
          aria-label={state.playing ? "Pause" : atEnd(state, total) ? "Play again" : "Play"}
          data-testid="viz-play"
        >
          {state.playing ? "⏸ Pause" : atEnd(state, total) ? "↻ Replay" : "▶ Play"}
        </button>
        <button
          className={btn}
          onClick={() => setState((s) => next(s, total))}
          disabled={atEnd(state, total)}
          aria-label="Next step"
          data-testid="viz-next"
        >
          ▶|
        </button>
        <label className="flex items-center gap-1 text-sm">
          Speed
          <select
            className={inputClass}
            value={state.speed}
            onChange={(e) => setState((s) => setSpeed(s, Number(e.target.value) as Speed))}
            data-testid="viz-speed"
          >
            {SPEEDS.map((s) => (
              <option key={s} value={s}>
                {s}×
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-1 items-center gap-2 text-sm">
          <span className="whitespace-nowrap" data-testid="viz-position">
            Step {state.index + 1} of {total}
          </span>
          <input
            type="range"
            className="min-w-32 flex-1"
            min={0}
            max={total - 1}
            value={state.index}
            aria-label="Step"
            aria-valuetext={`Step ${state.index + 1} of ${total}`}
            onChange={(e) => setState((s) => scrub(s, Number(e.target.value), total))}
            data-testid="viz-scrub"
          />
        </label>
      </div>

      <div>
        <p className="mb-1 text-xs opacity-70">Pseudocode</p>
        <ol className="rounded border p-2 font-mono text-xs" data-testid="viz-pseudo">
          {trace.pseudo.map((line, i) => (
            <li
              key={i}
              aria-current={i === step.line ? "step" : undefined}
              data-active={i === step.line}
              className={`whitespace-pre px-1 ${i === step.line ? "bg-blue-500/20 font-bold" : ""}`}
            >
              {line}
            </li>
          ))}
        </ol>
      </div>
    </div>
  );
}
