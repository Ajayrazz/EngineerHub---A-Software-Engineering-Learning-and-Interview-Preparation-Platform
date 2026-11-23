import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { ProgressBar, ReaderShell } from "@/components/reader/ui";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Enrollment = components["schemas"]["EnrollmentView"];

export const metadata: Metadata = { title: "My courses · EngineerHub" };

export default async function MyCourses() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/my-courses");
  const r = await readerGet<Enrollment[]>("me/enrollments", session.accessToken);
  return (
    <ReaderShell username={session.username}>
      <h1 className="text-2xl font-semibold">My courses</h1>
      {!r.ok ? (
        <Notice kind="error">Your courses could not be loaded. Please try again shortly.</Notice>
      ) : r.data.length === 0 ? (
        <p className="text-sm" data-testid="no-enrollments">
          You are not enrolled in any course yet.{" "}
          <Link className="underline" href="/courses">
            Browse courses
          </Link>
        </p>
      ) : (
        <ul className="flex flex-col gap-4" data-testid="enrollments">
          {r.data.map((e) => (
            <li
              key={e.courseId}
              className="flex flex-col gap-2 rounded border p-4"
              data-testid="enrollment"
            >
              <Link className="text-lg font-medium underline" href={`/courses/${e.courseSlug}`}>
                {e.courseTitle}
              </Link>
              <ProgressBar percent={e.percent ?? 0} label={`Progress in ${e.courseTitle}`} />
              <p className="text-xs">
                {e.completedLessons}/{e.totalLessons} lessons
                {e.completed ? " · completed" : ""}
              </p>
            </li>
          ))}
        </ul>
      )}
    </ReaderShell>
  );
}
