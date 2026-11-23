import Link from "next/link";
import { CreateProblem } from "@/components/admin/create-problem";
import { AdminShell, Notice, when } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type ProblemListItem = components["schemas"]["ProblemListItem"];

export default async function ProblemsPage() {
  const ctx = await adminContext("/admin/problems", "problem.edit");

  if (!ctx.global.includes("problem.edit")) {
    // Problem setters: only the problems they were assigned (scoped grants).
    const mine = ctx.grants.filter(
      (g) => g.permission === "problem.edit" && g.resourceType === "problem",
    );
    return (
      <AdminShell title="My problems" active="problems" global={ctx.global} scoped={ctx.scoped}>
        {mine.length === 0 ? (
          <p className="text-sm">You have not been assigned any problems yet.</p>
        ) : (
          <ul className="list-disc space-y-1 pl-6 text-sm" data-testid="assigned-problems">
            {mine.map((g) => (
              <li key={g.resourceId}>
                <Link className="underline" href={`/admin/problems/${g.resourceId}`}>
                  Problem {g.resourceId}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </AdminShell>
    );
  }

  const problems = await apiGet<ProblemListItem[]>(ctx, "admin/problems");
  return (
    <AdminShell title="Problems" active="problems" global={ctx.global} scoped={ctx.scoped}>
      <CreateProblem csrfToken={ctx.csrfToken} />
      {!problems.ok ? (
        <Notice kind="error">{problems.detail}</Notice>
      ) : (
        <table className="w-full text-left text-sm" data-testid="problems-table">
          <thead>
            <tr className="border-b opacity-70">
              <th className="py-2">Title</th>
              <th>Slug</th>
              <th>Status</th>
              <th>Level</th>
              <th>Tests</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {problems.data.map((p) => (
              <tr key={p.id} className="border-b" data-testid="problem-row">
                <td className="py-2">
                  <Link className="underline" href={`/admin/problems/${p.id}`}>
                    {p.title}
                  </Link>
                </td>
                <td>{p.slug}</td>
                <td>{p.status}</td>
                <td>{p.difficulty}</td>
                <td>{p.validationStatus === "verified" ? "verified" : "not verified"}</td>
                <td>{when(p.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </AdminShell>
  );
}
