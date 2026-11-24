import type { Metadata } from "next";
import Link from "next/link";
import { CheckCircle2, Flame, GraduationCap } from "lucide-react";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { ProgressBar, PageHeader, ReaderShell } from "@/components/reader/ui";
import { buildGrid, plural } from "@/lib/dashboard/heatmap";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Dashboard = components["schemas"]["DashboardResponse"];
type Enrollment = components["schemas"]["EnrollmentView"];

export const metadata: Metadata = { title: "Dashboard · EngineerHub" };

const SHADES = ["bg-surface-2", "bg-blue-900", "bg-blue-700", "bg-blue-500", "bg-sky-300"];

export default async function DashboardPage() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/dashboard");
  const [dash, enrollments] = await Promise.all([
    readerGet<Dashboard>("me/dashboard", session.accessToken),
    readerGet<Enrollment[]>("me/enrollments", session.accessToken),
  ]);

  return (
    <ReaderShell username={session.username} wide>
      <PageHeader title="Dashboard" lead="Your streak, progress and what to do next." />
      {!dash.ok ? (
        <Notice kind="error">Your activity could not be loaded. Please try again shortly.</Notice>
      ) : (
        <>
          <section aria-label="Summary" className="grid gap-4 sm:grid-cols-3">
            <div className="glass p-5" data-testid="streak-card">
              <p className="flex items-center gap-2 text-sm text-muted">
                <Flame size={16} aria-hidden className="text-warn" />
                Current streak
              </p>
              <p className="mt-1 text-4xl font-bold tracking-tight" data-testid="streak-current">
                {dash.data.streak?.current ?? 0}
              </p>
              <p className="text-xs opacity-70" data-testid="streak-note">
                {dash.data.streak?.activeToday
                  ? "You studied today. Come back tomorrow to keep it going."
                  : (dash.data.streak?.current ?? 0) > 0
                    ? "Complete a lesson today to keep your streak."
                    : "Complete a lesson to start a streak."}
              </p>
              <p className="mt-2 text-xs opacity-70">
                Longest: {plural(dash.data.streak?.longest ?? 0, "day")}
              </p>
            </div>
            <div className="glass p-5">
              <p className="flex items-center gap-2 text-sm text-muted">
                <CheckCircle2 size={16} aria-hidden className="text-accent-2" />
                Lessons completed
              </p>
              <p className="mt-1 text-4xl font-bold tracking-tight" data-testid="lessons-total">
                {dash.data.lessonsCompleted ?? 0}
              </p>
            </div>
            <div className="glass p-5">
              <p className="flex items-center gap-2 text-sm text-muted">
                <GraduationCap size={16} aria-hidden className="text-accent-2" />
                Courses completed
              </p>
              <p className="mt-1 text-4xl font-bold tracking-tight" data-testid="courses-total">
                {dash.data.coursesCompleted ?? 0}
              </p>
            </div>
          </section>

          <section aria-label="Activity" className="flex flex-col gap-2">
            <h2 className="text-xl font-semibold">Activity, last 16 weeks</h2>
            <div
              className="flex gap-1 overflow-x-auto"
              data-testid="activity-grid"
              role="img"
              aria-label="Lessons completed per day"
            >
              {buildGrid(
                dash.data.activity ?? [],
                dash.data.today ?? new Date().toISOString().slice(0, 10),
              ).map((week, i) => (
                <div key={i} className="flex flex-col gap-1">
                  {week.map((c) => (
                    <div
                      key={c.day}
                      title={c.future ? "" : `${c.day}: ${plural(c.count, "lesson")}`}
                      data-testid={c.future ? undefined : "activity-cell"}
                      data-day={c.day}
                      data-count={c.count}
                      className={`h-3.5 w-3.5 rounded-sm ${c.future ? "opacity-0" : SHADES[c.level]}`}
                    />
                  ))}
                </div>
              ))}
            </div>
            <p className="text-xs opacity-70">
              Days follow your time zone ({dash.data.timezone}). Change it in your preferences if
              this is wrong.
            </p>
          </section>
        </>
      )}

      <section aria-label="Continue learning" className="flex flex-col gap-3">
        <h2 className="text-xl font-semibold">Continue learning</h2>
        {!enrollments.ok ? (
          <Notice kind="error">Your courses could not be loaded.</Notice>
        ) : enrollments.data.filter((e) => !e.completed).length === 0 ? (
          <p className="text-sm" data-testid="nothing-to-continue">
            Nothing in progress.{" "}
            <Link className="underline" href="/courses">
              Browse courses
            </Link>
          </p>
        ) : (
          <ul className="flex flex-col gap-3" data-testid="continue-list">
            {enrollments.data
              .filter((e) => !e.completed)
              .map((e) => (
                <li key={e.courseId} className="glass card-hover flex flex-col gap-2 p-4">
                  <Link
                    className="font-semibold hover:text-accent-2"
                    href={`/courses/${e.courseSlug}`}
                  >
                    {e.courseTitle}
                  </Link>
                  <ProgressBar percent={e.percent ?? 0} label={`Progress in ${e.courseTitle}`} />
                  <p className="text-xs">
                    {e.completedLessons}/{e.totalLessons} lessons
                  </p>
                </li>
              ))}
          </ul>
        )}
      </section>

      <p
        className="rounded-xl border bg-surface/70 p-3 text-sm opacity-80"
        data-testid="problems-note"
      >
        Problem-solving statistics (solved counts, accepted submissions) arrive with the online
        judge.
      </p>
    </ReaderShell>
  );
}
