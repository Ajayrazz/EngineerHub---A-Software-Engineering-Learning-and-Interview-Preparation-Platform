import { CheckCircle2, Circle, CircleDot, Lock } from "lucide-react";
import type { components } from "@/lib/api/schema";

type Item = components["schemas"]["PublicProblemItem"];
type State = components["schemas"]["ProblemState"];

export function DifficultyBadge({ level }: { level?: string }) {
  return (
    <span
      className={`rounded-full border px-2.5 py-0.5 text-xs font-medium ${
        level === "easy"
          ? "border-emerald-400/40 bg-emerald-400/10 text-emerald-300"
          : level === "medium"
            ? "border-amber-400/40 bg-amber-400/10 text-amber-300"
            : "border-rose-400/40 bg-rose-400/10 text-rose-300"
      }`}
      data-testid="difficulty"
    >
      {level}
    </span>
  );
}

export function statusLabel(s?: State): string {
  if (s?.status === "solved") return "✓ solved";
  if (s?.status === "attempted") return "attempted";
  return "—";
}

function StatusIcon({ s }: { s?: State }) {
  if (s?.status === "solved")
    return <CheckCircle2 size={16} aria-hidden className="text-emerald-400" />;
  if (s?.status === "attempted")
    return <CircleDot size={16} aria-hidden className="text-amber-300" />;
  return <Circle size={16} aria-hidden className="text-muted" />;
}

export function ProblemTable({
  items,
  states,
  signedIn,
}: {
  items: Item[];
  states: Map<string, State>;
  signedIn: boolean;
}) {
  return (
    <div className="overflow-x-auto rounded-xl border bg-surface">
      <table className="w-full text-left text-sm" data-testid="problem-table">
        <thead>
          <tr className="border-b text-xs uppercase tracking-wider text-muted">
            {signedIn && <th className="px-4 py-3 font-medium">Status</th>}
            <th className="px-4 py-3 font-medium">Title</th>
            <th className="font-medium">Level</th>
            <th className="font-medium">Topics</th>
            <th className="font-medium">Access</th>
            {signedIn && <th className="px-4 font-medium">★</th>}
          </tr>
        </thead>
        <tbody>
          {items.map((p) => {
            const st = states.get(p.slug ?? "");
            return (
              <tr
                key={p.slug}
                className="border-b last:border-0 transition hover:bg-surface-2"
                data-testid="problem-row"
              >
                {signedIn && (
                  <td className="px-4 py-3">
                    <span className="flex items-center gap-2 text-muted">
                      <StatusIcon s={st} />
                      {statusLabel(st)}
                    </span>
                  </td>
                )}
                <td className="px-4 py-3">
                  <a className="font-medium text-info hover:underline" href={`/problems/${p.slug}`}>
                    {p.title}
                  </a>
                </td>
                <td>
                  <DifficultyBadge level={p.difficulty} />
                </td>
                <td className="text-muted">{(p.topics ?? []).map((t) => t.name).join(", ")}</td>
                <td>
                  {p.accessTier === "premium" ? (
                    <span className="inline-flex items-center gap-1 text-warn">
                      <Lock size={12} aria-hidden />
                      premium
                    </span>
                  ) : (
                    <span className="text-muted">free</span>
                  )}
                </td>
                {signedIn && <td className="px-4 text-warn">{st?.bookmarked ? "★" : ""}</td>}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
