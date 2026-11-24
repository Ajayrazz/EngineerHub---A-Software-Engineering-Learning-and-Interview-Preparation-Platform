import { GRAPH_SIZE, circleLayout } from "@/lib/viz/layout";
import type {
  ArrayFrame,
  Cell,
  Frame,
  GraphFrame,
  Mark,
  NodeState,
  StackFrame,
} from "@/lib/viz/types";

const MARK_CLASS: Record<Mark, string> = {
  none: "border-black/30 dark:border-white/30",
  active: "border-blue-600 bg-blue-500/10",
  compare: "border-amber-500 bg-amber-400/20 border-dashed",
  swap: "border-red-600 bg-red-500/20 border-4",
  sorted: "border-green-600 bg-green-500/15",
  found: "border-green-700 bg-green-500/40 border-4",
  window: "border-blue-600 bg-blue-500/15",
  dim: "border-black/10 opacity-40 dark:border-white/10",
};

const MARK_WORD: Record<Mark, string> = {
  none: "",
  active: "in range",
  compare: "comparing",
  swap: "swapping",
  sorted: "sorted",
  found: "found",
  window: "in window",
  dim: "out of range",
};

function CellRow({
  cells,
  label,
  pointers,
}: {
  cells: Cell[];
  label?: string;
  pointers?: ArrayFrame["pointers"];
}) {
  return (
    <div>
      {label && <p className="mb-1 text-xs opacity-70">{label}</p>}
      <ul className="flex flex-wrap gap-1" aria-label={label ?? "Values"}>
        {cells.map((c, i) => {
          const here = (pointers ?? []).filter((p) => p.index === i).map((p) => p.label);
          return (
            <li key={i} className="flex flex-col items-center">
              <span
                className={`flex h-10 min-w-10 items-center justify-center rounded border-2 px-1 text-sm font-medium ${MARK_CLASS[c.mark]}`}
                data-mark={c.mark}
                title={MARK_WORD[c.mark] || undefined}
              >
                {c.value === null ? "·" : c.value}
              </span>
              <span className="h-4 text-[10px] opacity-70" aria-hidden={here.length === 0}>
                {here.join(" ")}
              </span>
              <span className="sr-only">
                {c.value === null ? "empty" : c.value}
                {MARK_WORD[c.mark] ? `, ${MARK_WORD[c.mark]}` : ""}
                {here.length ? `, pointer ${here.join(" and ")}` : ""}
              </span>
            </li>
          );
        })}
      </ul>
    </div>
  );
}

export function ArrayView({ frame }: { frame: ArrayFrame }) {
  return (
    <div className="flex flex-col gap-3" data-testid="viz-array">
      <CellRow cells={frame.cells} pointers={frame.pointers} />
      {frame.buffer && <CellRow cells={frame.buffer.cells} label={frame.buffer.label} />}
      {frame.stats.length > 0 && (
        <dl className="flex flex-wrap gap-x-6 gap-y-1 text-sm">
          {frame.stats.map((s) => (
            <div key={s.label} className="flex gap-1">
              <dt className="opacity-70">{s.label}:</dt>
              <dd className="font-medium">{s.value}</dd>
            </div>
          ))}
        </dl>
      )}
    </div>
  );
}

export function StackView({ frame }: { frame: StackFrame }) {
  return (
    <div className="flex flex-wrap items-start gap-8" data-testid="viz-stack">
      <div>
        <p className="mb-1 text-xs opacity-70">Characters</p>
        <ul className="flex gap-1" aria-label="Characters">
          {frame.chars.map((c, i) => (
            <li
              key={i}
              data-current={i === frame.at}
              className={`flex h-10 w-9 items-center justify-center rounded border-2 font-mono text-base ${i === frame.at ? "border-blue-600 bg-blue-500/10" : i < frame.at ? "border-black/10 opacity-50" : "border-black/30 dark:border-white/30"}`}
            >
              {c}
            </li>
          ))}
        </ul>
      </div>
      <div>
        <p className="mb-1 text-xs opacity-70">Stack (top first)</p>
        <ul
          className="flex min-h-10 min-w-16 flex-col gap-1 rounded border border-dashed p-1"
          aria-label="Stack, top first"
          data-testid="viz-stack-items"
        >
          {frame.stack.length === 0 ? (
            <li className="text-xs opacity-60">empty</li>
          ) : (
            [...frame.stack].reverse().map((c, i) => (
              <li
                key={i}
                className="rounded border-2 border-black/30 px-2 text-center font-mono dark:border-white/30"
              >
                {c}
              </li>
            ))
          )}
        </ul>
      </div>
      {frame.verdict && (
        <p
          className={`self-center text-sm font-semibold ${frame.verdict === "balanced" ? "text-green-700 dark:text-green-400" : "text-red-700 dark:text-red-400"}`}
          data-testid="viz-verdict"
        >
          {frame.verdict === "balanced" ? "Balanced" : "Not balanced"}
        </p>
      )}
    </div>
  );
}

const NODE_FILL: Record<NodeState, string> = {
  unseen: "fill-transparent stroke-black/40 dark:stroke-white/40",
  queued: "fill-amber-400/30 stroke-amber-600",
  visiting: "fill-blue-500/40 stroke-blue-700",
  done: "fill-green-500/30 stroke-green-700",
};

export function GraphView({ frame }: { frame: GraphFrame }) {
  const pos = circleLayout(frame.nodes.map((n) => n.id));
  return (
    <div className="flex flex-wrap items-start gap-6" data-testid="viz-graph">
      <svg
        viewBox={`0 0 ${GRAPH_SIZE} ${GRAPH_SIZE}`}
        width={GRAPH_SIZE}
        height={GRAPH_SIZE}
        role="img"
        aria-label={`Graph with ${frame.nodes.length} nodes: ${frame.nodes.map((n) => `${n.id} ${n.state}`).join(", ")}`}
      >
        {frame.edges.map(([a, b]) => {
          const p = pos.get(a)!;
          const q = pos.get(b)!;
          return (
            <line
              key={`${a}-${b}`}
              x1={p.x}
              y1={p.y}
              x2={q.x}
              y2={q.y}
              className="stroke-black/40 dark:stroke-white/40"
              strokeWidth={1.5}
            />
          );
        })}
        {frame.nodes.map((n) => {
          const p = pos.get(n.id)!;
          return (
            <g key={n.id} data-node={n.id} data-state={n.state}>
              <circle
                cx={p.x}
                cy={p.y}
                r={16}
                strokeWidth={n.state === "visiting" ? 4 : 2}
                className={NODE_FILL[n.state]}
              />
              <text
                x={p.x}
                y={p.y + 5}
                textAnchor="middle"
                className="fill-current text-sm font-medium"
              >
                {n.id}
              </text>
            </g>
          );
        })}
      </svg>
      <dl className="flex flex-col gap-2 text-sm">
        <div>
          <dt className="text-xs opacity-70">Queue (front first)</dt>
          <dd data-testid="viz-queue">{frame.queue.length ? frame.queue.join(", ") : "empty"}</dd>
        </div>
        <div>
          <dt className="text-xs opacity-70">Visited, in order</dt>
          <dd data-testid="viz-order">
            {frame.order.length ? frame.order.join(", ") : "none yet"}
          </dd>
        </div>
        <div className="text-xs opacity-70">
          Hollow: unseen · amber: queued · blue (thick): being visited · green: done
        </div>
      </dl>
    </div>
  );
}

export function FrameView({ frame }: { frame: Frame }) {
  return frame.kind === "array" ? (
    <ArrayView frame={frame} />
  ) : frame.kind === "stack" ? (
    <StackView frame={frame} />
  ) : (
    <GraphView frame={frame} />
  );
}
