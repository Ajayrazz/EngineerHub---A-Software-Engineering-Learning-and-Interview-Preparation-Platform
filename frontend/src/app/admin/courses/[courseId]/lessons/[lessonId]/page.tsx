import Link from "next/link";
import { notFound } from "next/navigation";
import { LessonEditor } from "@/components/admin/lesson-editor";
import { AdminShell, Notice } from "@/components/admin/ui";
import { isUuid } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type CourseView = components["schemas"]["CourseView"];
type LessonView = components["schemas"]["LessonView"];

export default async function LessonPage({
  params,
}: PageProps<"/admin/courses/[courseId]/lessons/[lessonId]">) {
  const { courseId, lessonId } = await params;
  if (!isUuid(courseId) || !isUuid(lessonId)) notFound();
  const ctx = await adminContext(`/admin/courses/${courseId}/lessons/${lessonId}`, "course.edit");
  const [course, lesson] = await Promise.all([
    apiGet<CourseView>(ctx, `admin/courses/${courseId}`),
    apiGet<LessonView>(ctx, `admin/courses/${courseId}/lessons/${lessonId}`),
  ]);
  if (!course.ok || !lesson.ok) {
    const failed = !course.ok ? course : lesson;
    if (!failed.ok && (failed.status === 404 || failed.status === 403)) notFound();
    return (
      <AdminShell title="Lesson" active="courses" global={ctx.global} scoped={ctx.scoped}>
        <Notice kind="error">{failed.ok ? "Unavailable." : failed.detail}</Notice>
      </AdminShell>
    );
  }
  return (
    <AdminShell
      title={lesson.data.title ?? "Lesson"}
      active="courses"
      global={ctx.global}
      scoped={ctx.scoped}
    >
      <p className="text-sm">
        <Link className="underline" href={`/admin/courses/${courseId}`}>
          ← {course.data.title}
        </Link>
      </p>
      <LessonEditor
        csrfToken={ctx.csrfToken}
        canPublish={ctx.global.includes("course.publish")}
        courseId={courseId}
        courseSlug={course.data.slug ?? ""}
        sections={(course.data.sections ?? []).map((s) => ({
          id: s.id ?? "",
          title: s.title ?? "",
        }))}
        initial={lesson.data}
      />
    </AdminShell>
  );
}
