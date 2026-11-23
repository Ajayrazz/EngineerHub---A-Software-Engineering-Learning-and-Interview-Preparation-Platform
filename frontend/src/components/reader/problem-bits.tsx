import type { components } from "@/lib/api/schema";

type Item = components["schemas"]["PublicProblemItem"];
type State = components["schemas"]["ProblemState"];

export function DifficultyBadge({ level }: { level?: string }) {
  return (
    <span className="rounded border px-1.5 py-0.5 text-xs" data-testid="difficulty">
      {level}
    </span>
  );
}

export function statusLabel(s?: State): string {
  if (s?.status === "solved") return "✓ solved";
  if (s?.status === "attempted") return "attempted";
  return "—";
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
    <table className="w-full text-left text-sm" data-testid="problem-table">
      <thead>
        <tr className="border-b opacity-70">
          {signedIn && <th className="py-2">Status</th>}
          <th className="py-2">Title</th>
          <th>Level</th>
          <th>Topics</th>
          <th>Access</th>
          {signedIn && <th>★</th>}
        </tr>
      </thead>
      <tbody>
        {items.map((p) => (
          <tr key={p.slug} className="border-b" data-testid="problem-row">
            {signedIn && <td className="py-2">{statusLabel(states.get(p.slug ?? ""))}</td>}
            <td className="py-2">
              <a className="underline" href={`/problems/${p.slug}`}>
                {p.title}
              </a>
            </td>
            <td>
              <DifficultyBadge level={p.difficulty} />
            </td>
            <td>{(p.topics ?? []).map((t) => t.name).join(", ")}</td>
            <td>{p.accessTier === "premium" ? "premium" : "free"}</td>
            {signedIn && <td>{states.get(p.slug ?? "")?.bookmarked ? "★" : ""}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
