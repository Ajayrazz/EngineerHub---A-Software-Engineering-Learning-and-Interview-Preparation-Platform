import { notFound } from "next/navigation";
import { CourseEditor } from "@/components/admin/course-editor";
import { AdminShell, Notice } from "@/components/admin/ui";
import { isUuid } from "@/lib/admin/policy";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type CourseView = components["schemas"]["CourseView"];
type TopicView = components["schemas"]["TopicView"];

export default async function CoursePage({ params }: PageProps<"/admin/courses/[courseId]">) {
  const { courseId } = await params;
  if (!isUuid(courseId)) notFound();
  const ctx = await adminContext(`/admin/courses/${courseId}`, "course.edit");
  const [course, topics] = await Promise.all([
    apiGet<CourseView>(ctx, `admin/courses/${courseId}`),
    ctx.global.includes("course.edit")
      ? apiGet<TopicView[]>(ctx, "admin/topics")
      : Promise.resolve({ ok: true as const, data: [] as TopicView[] }),
  ]);
  if (!course.ok) {
    // 403 for a course the caller may not edit is shown as not found: no hint that it exists
    if (course.status === 404 || course.status === 403) notFound();
    return (
      <AdminShell title="Course" active="courses" global={ctx.global} scoped={ctx.scoped}>
        <Notice kind="error">{course.detail}</Notice>
      </AdminShell>
    );
  }
  return (
    <AdminShell
      title={course.data.title ?? "Course"}
      active="courses"
      global={ctx.global}
      scoped={ctx.scoped}
    >
      <CourseEditor
        csrfToken={ctx.csrfToken}
        canPublish={ctx.global.includes("course.publish")}
        topics={topics.ok ? topics.data : []}
        initial={course.data}
      />
    </AdminShell>
  );
}
