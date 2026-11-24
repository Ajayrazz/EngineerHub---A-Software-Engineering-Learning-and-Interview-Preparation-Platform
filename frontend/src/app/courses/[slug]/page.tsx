import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { EnrollButton } from "@/components/reader/enroll-button";
import { ProgressBar, ReaderShell, TierBadge } from "@/components/reader/ui";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

type Detail = components["schemas"]["PublicCourseDetail"];
type Progress = components["schemas"]["CourseProgress"];

export async function generateMetadata({
  params,
}: PageProps<"/courses/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  if (!SLUG.test(slug)) return {};
  const r = await readerGet<Detail>(`courses/${slug}`);
  if (!r.ok) return {};
  return {
    title: `${r.data.course?.title} · EngineerHub`,
    description: r.data.course?.description,
  };
}

export default async function CoursePage({ params }: PageProps<"/courses/[slug]">) {
  const { slug } = await params;
  if (!SLUG.test(slug)) notFound();
  const session = await readerSession();
  const detail = await readerGet<Detail>(`courses/${slug}`);
  if (!detail.ok) {
    if (detail.status === 404) notFound();
    throw new Error("Course unavailable");
  }
  const { course, sections = [], id } = detail.data;
  const progress =
    session && id
      ? await readerGet<Progress>(`me/courses/${id}/progress`, session.accessToken)
      : null;
  const p = progress?.ok ? progress.data : null;
  const done = new Set(p?.completedLessonIds ?? []);
  const firstSlug = sections[0]?.lessons?.[0]?.slug ?? null;

  return (
    <ReaderShell username={session?.username} wide>
      <header className="hero-glow relative flex flex-col gap-3 overflow-hidden rounded-2xl border p-8">
        <div className="stars" aria-hidden />
        <h1 className="relative text-4xl font-bold tracking-tight" data-testid="course-title">
          {course?.title}
        </h1>
        <p className="relative max-w-2xl text-muted">{course?.description}</p>
        <p className="relative flex flex-wrap items-center gap-2 text-xs text-muted">
          {course?.topic?.name && <span className="chip">{course.topic.name}</span>}
          <span className="chip capitalize">{course?.difficulty}</span>
          <span>{course?.lessonCount} lessons</span>
          <TierBadge tier={course?.accessTier} />
        </p>
        {p?.enrolled && (
          <div className="max-w-sm">
            <ProgressBar percent={p.percent ?? 0} label="Course progress" />
            <p className="mt-1 text-xs" data-testid="course-progress">
              {p.completedLessons}/{p.totalLessons} lessons completed
            </p>
          </div>
        )}
        {id && (
          <EnrollButton
            csrfToken={session?.csrfToken ?? null}
            courseId={id}
            courseSlug={slug}
            enrolled={p?.enrolled ?? false}
            resumeSlug={p?.resume?.lessonSlug ?? null}
            firstSlug={firstSlug}
          />
        )}
      </header>

      <nav aria-label="Course outline" className="flex flex-col gap-4" data-testid="outline">
        {sections.map((s) => (
          <section key={s.position} className="glass p-5">
            <h2 className="mb-3 text-lg font-semibold">{s.title}</h2>
            <ol className="flex flex-col gap-1 text-sm">
              {(s.lessons ?? []).map((l) => (
                <li
                  key={l.slug}
                  className="flex flex-wrap items-center gap-2 rounded-lg px-2 py-1.5 hover:bg-surface-2"
                  data-testid="outline-lesson"
                >
                  <span aria-hidden>{done.has(l.id ?? "") ? "✓" : "○"}</span>
                  <Link className="hover:text-accent-2" href={`/courses/${slug}/lessons/${l.slug}`}>
                    {l.title}
                  </Link>
                  <TierBadge tier={l.accessTier} preview={l.freePreview} />
                  {l.estReadMin ? (
                    <span className="text-xs opacity-60">{l.estReadMin} min</span>
                  ) : null}
                </li>
              ))}
            </ol>
          </section>
        ))}
        {sections.length === 0 && <p className="text-sm">No lessons are published yet.</p>}
      </nav>
    </ReaderShell>
  );
}
