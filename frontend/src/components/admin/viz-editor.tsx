"use client";

import { VizPlayer } from "@/components/viz/viz-player";
import { CATALOG, entry, inputProblem } from "@/lib/viz";
import { input as inputClass } from "./ui";

/**
 * Authoring of one visualization block: pick an algorithm and give it a small input. The learner sees exactly what the
 * preview shows (the trace is computed from these two values, nothing else is stored).
 */
export function VizEditor({
  data,
  index,
  onChange,
}: {
  data: Record<string, string>;
  index: number;
  onChange: (data: Record<string, string>) => void;
}) {
  const e = entry(data.algorithm);
  const problem = e
    ? inputProblem(data.algorithm, data.input ?? "", data.param ?? "")
    : "Choose an algorithm.";
  return (
    <div className="flex flex-col gap-3" data-testid="viz-editor">
      <div className="flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          Algorithm
          <select
            className={inputClass}
            aria-label={`Block ${index + 1} algorithm`}
            value={data.algorithm ?? ""}
            onChange={(ev) => {
              const next = entry(ev.target.value);
              if (next)
                onChange({
                  algorithm: next.id,
                  input: next.defaultInput,
                  param: next.defaultParam,
                });
            }}
          >
            {CATALOG.map((c) => (
              <option key={c.id} value={c.id}>
                {c.title}
              </option>
            ))}
          </select>
        </label>
        {e && (
          <label className="flex flex-col gap-1 text-sm">
            {e.inputLabel}
            <input
              className={`${inputClass} w-80`}
              aria-label={`Block ${index + 1} visualization input`}
              value={data.input ?? ""}
              maxLength={120}
              onChange={(ev) => onChange({ ...data, input: ev.target.value })}
            />
          </label>
        )}
        {e?.paramLabel && (
          <label className="flex flex-col gap-1 text-sm">
            {e.paramLabel}
            <input
              className={`${inputClass} w-28`}
              aria-label={`Block ${index + 1} visualization value`}
              value={data.param ?? ""}
              maxLength={10}
              onChange={(ev) => onChange({ ...data, param: ev.target.value })}
            />
          </label>
        )}
      </div>
      {problem ? (
        <p
          role="alert"
          className="text-sm text-red-600 dark:text-red-400"
          data-testid="viz-editor-problem"
        >
          {problem}
        </p>
      ) : (
        <VizPlayer algorithm={data.algorithm} input={data.input} param={data.param ?? ""} />
      )}
    </div>
  );
}
