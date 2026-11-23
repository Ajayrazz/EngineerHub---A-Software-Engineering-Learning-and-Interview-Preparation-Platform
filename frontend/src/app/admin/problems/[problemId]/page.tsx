import { notFound } from "next/navigation";
import { ProblemEditor } from "@/components/admin/problem-editor";
import { AdminShell, Notice } from "@/components/admin/ui";
import { isUuid } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";
import { readerGet } from "@/lib/reader/server";

type ProblemView = components["schemas"]["ProblemView"];
type TestsView = components["schemas"]["TestsView"];
type ValidationView = components["schemas"]["ValidationView"];
type TagView = components["schemas"]["TagView"];
type TopicView = components["schemas"]["TopicView"];

export default async function ProblemPage({ params }: PageProps<"/admin/problems/[problemId]">) {
  const { problemId } = await params;
  if (!isUuid(problemId)) notFound();
  const ctx = await adminContext(`/admin/problems/${problemId}`, "problem.edit");
  const canListPatterns = ctx.global.includes("problem.edit");
  const [problem, tests, validation, topics, patterns] = await Promise.all([
    apiGet<ProblemView>(ctx, `admin/problems/${problemId}`),
    apiGet<TestsView>(ctx, `admin/problems/${problemId}/tests`),
    apiGet<ValidationView>(ctx, `admin/problems/${problemId}/validation`),
    readerGet<TopicView[]>("topics"), // public taxonomy: also available to assigned setters
    canListPatterns
      ? apiGet<TagView[]>(ctx, "admin/patterns")
      : Promise.resolve({ ok: true as const, data: [] as TagView[] }),
  ]);
  if (!problem.ok || !tests.ok || !validation.ok) {
    const failed = !problem.ok ? problem : !tests.ok ? tests : validation;
    if (!failed.ok && (failed.status === 404 || failed.status === 403)) notFound();
    return (
      <AdminShell title="Problem" active="problems" global={ctx.global} scoped={ctx.scoped}>
        <Notice kind="error">{failed.ok ? "Unavailable." : failed.detail}</Notice>
      </AdminShell>
    );
  }
  return (
    <AdminShell
      title={problem.data.title ?? "Problem"}
      active="problems"
      global={ctx.global}
      scoped={ctx.scoped}
    >
      <ProblemEditor
        csrfToken={ctx.csrfToken}
        canPublish={ctx.global.includes("problem.publish")}
        canCreatePatterns={canListPatterns}
        topics={topics.ok ? topics.data : []}
        patterns={patterns.ok ? patterns.data : []}
        initial={problem.data}
        initialTests={tests.data}
        initialValidation={validation.data}
      />
    </AdminShell>
  );
}
