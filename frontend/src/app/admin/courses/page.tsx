import Link from "next/link";
import { CreateCourse } from "@/components/admin/create-course";
import { AdminShell, Notice, when } from "@/components/admin/ui";
import { adminContext, apiGet } from "@/lib/admin/server";
import type { components } from "@/lib/api/schema";

type CourseListItem = components["schemas"]["CourseListItem"];
type TopicView = components["schemas"]["TopicView"];

export default async function CoursesPage() {
  const ctx = await adminContext("/admin/courses", "course.edit");
  const canList = ctx.global.includes("course.edit");

  if (!canList) {
    // Instructors: only the courses they were assigned (scoped grants).
    const mine = ctx.grants.filter(
      (g) => g.permission === "course.edit" && g.resourceType === "course",
    );
    return (
      <AdminShell title="My courses" active="courses" global={ctx.global} scoped={ctx.scoped}>
        {mine.length === 0 ? (
          <p className="text-sm">You have not been assigned any courses yet.</p>
        ) : (
          <ul className="list-disc space-y-1 pl-6 text-sm" data-testid="assigned-courses">
            {mine.map((g) => (
              <li key={g.resourceId}>
                <Link className="underline" href={`/admin/courses/${g.resourceId}`}>
                  Course {g.resourceId}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </AdminShell>
    );
  }

  const [courses, topics] = await Promise.all([
    apiGet<CourseListItem[]>(ctx, "admin/courses"),
    apiGet<TopicView[]>(ctx, "admin/topics"),
  ]);
  return (
    <AdminShell title="Courses" active="courses" global={ctx.global} scoped={ctx.scoped}>
      <CreateCourse csrfToken={ctx.csrfToken} topics={topics.ok ? topics.data : []} />
      {!courses.ok ? (
        <Notice kind="error">{courses.detail}</Notice>
      ) : (
        <table className="w-full text-left text-sm" data-testid="courses-table">
          <thead>
            <tr className="border-b opacity-70">
              <th className="py-2">Title</th>
              <th>Slug</th>
              <th>Status</th>
              <th>Access</th>
              <th>Updated</th>
            </tr>
          </thead>
          <tbody>
            {courses.data.map((c) => (
              <tr key={c.id} className="border-b" data-testid="course-row">
                <td className="py-2">
                  <Link className="underline" href={`/admin/courses/${c.id}`}>
                    {c.title}
                  </Link>
                </td>
                <td>{c.slug}</td>
                <td>{c.status}</td>
                <td>{c.accessTier}</td>
                <td>{when(c.updatedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </AdminShell>
  );
}
