import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { LessonActions } from "@/components/reader/lesson-actions";
import { LessonBlocks } from "@/components/reader/lesson-blocks";
import { CourseSidebar } from "@/components/reader/course-sidebar";
import { RoadmapAnimation } from "@/components/reader/roadmap-animation";
import { LanguageHero } from "@/components/reader/language-hero";
import outline from "@/lib/reader/practice-outline.json";
import { ReadingProgress } from "@/components/reader/reading-progress";
import { ReaderShell } from "@/components/reader/ui";
import { extractHeadings } from "@/lib/cms/toc";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

type Detail = components["schemas"]["PublicCourseDetail"];
type Lesson = components["schemas"]["PublicLesson"];
type Progress = components["schemas"]["CourseProgress"];

export async function generateMetadata({
  params,
}: PageProps<"/courses/[slug]/lessons/[lessonSlug]">): Promise<Metadata> {
  const { slug, lessonSlug } = await params;
  if (!SLUG.test(slug) || !SLUG.test(lessonSlug)) return {};
  // only the public outline is used: a premium lesson's title is public, its body never is
  const d = await readerGet<Detail>(`courses/${slug}`);
  if (!d.ok) return {};
  const l = (d.data.sections ?? [])
    .flatMap((s) => s.lessons ?? [])
    .find((x) => x.slug === lessonSlug);
  return l ? { title: `${l.title} · ${d.data.course?.title} · EngineerHub` } : {};
}

// Static classes: inline style attributes are blocked by our CSP.
const INDENT = ["pl-0", "pl-3", "pl-6", "pl-9"];

export default async function LessonPage({
  params,
}: PageProps<"/courses/[slug]/lessons/[lessonSlug]">) {
  const { slug, lessonSlug } = await params;
  if (!SLUG.test(slug) || !SLUG.test(lessonSlug)) notFound();
  const session = await readerSession();
  const [detail, lesson] = await Promise.all([
    readerGet<Detail>(`courses/${slug}`),
    readerGet<Lesson>(`courses/${slug}/lessons/${lessonSlug}`, session?.accessToken),
  ]);
  if (!detail.ok) {
    if (detail.status === 404) notFound();
    throw new Error("Course unavailable");
  }
  const locked = !lesson.ok && lesson.status === 403 && lesson.code === "ENTITLEMENT_REQUIRED";
  if (!lesson.ok && !locked) {
    if (lesson.status === 404) notFound();
    throw new Error("Lesson unavailable");
  }

  const courseId = detail.data.id;
  const problemStates = session
    ? await readerGet<components["schemas"]["ProblemStates"]>(
        "me/problems/states",
        session.accessToken,
      )
    : null;
  const solvedSlugs = (problemStates?.ok ? (problemStates.data.states ?? []) : [])
    .filter((st) => st.status === "solved")
    .map((st) => st.slug ?? "");
  const progress =
    session && courseId
      ? await readerGet<Progress>(`me/courses/${courseId}/progress`, session.accessToken)
      : null;
  const p = progress?.ok ? progress.data : null;
  const done = new Set(p?.completedLessonIds ?? []);
  const sections = detail.data.sections ?? [];
  const outlineLesson = sections.flatMap((s) => s.lessons ?? []).find((x) => x.slug === lessonSlug);
  const body = lesson.ok ? lesson.data : null;
  const toc = body
    ? (body.blocks ?? []).flatMap((b, i) =>
        b.type === "markdown" ? extractHeadings(String(b.data?.text ?? ""), `b${i}`, 3) : [],
      )
    : [];
  const loginHref = `/auth/login?returnTo=${encodeURIComponent(`/courses/${slug}/lessons/${lessonSlug}`)}`;

  return (
    <ReaderShell username={session?.username} wide>
      <div className="grid gap-8 lg:grid-cols-[17rem_minmax(0,1fr)_13rem]">
        <CourseSidebar
          courseSlug={slug}
          courseTitle={detail.data.course?.title ?? ""}
          currentSlug={lessonSlug}
          practice={Object.fromEntries(
            Object.entries(
              outline as Record<string, { slug: string; title: string; difficulty: string }[]>,
            )
              .filter(([k]) => k.startsWith(`${slug}/`))
              .map(([k, v]) => [k.slice(slug.length + 1), v]),
          )}
          solved={solvedSlugs}
          enrolled={Boolean(p?.enrolled)}
          percent={p?.percent ?? 0}
          completed={p?.completedLessons ?? 0}
          total={p?.totalLessons ?? sections.reduce((n, s) => n + (s.lessons ?? []).length, 0)}
          doneIds={[...done]}
          sections={sections.map((s) => ({
            title: s.title ?? "",
            lessons: (s.lessons ?? []).map((l) => ({
              id: l.id ?? "",
              slug: l.slug ?? "",
              title: l.title ?? "",
              premium: l.accessTier === "premium",
              preview: Boolean(l.freePreview),
            })),
          }))}
        />

        <article className="flex min-w-0 flex-col gap-6">
          <h1 className="text-3xl font-bold tracking-tight" data-testid="lesson-title">
            {body?.title ?? outlineLesson?.title}
          </h1>
          {locked ? (
            <section
              className="glass flex flex-col gap-3 border-accent/50 p-6"
              data-testid="locked"
              aria-label="Premium content"
            >
              <p className="font-medium">This lesson is part of the premium content.</p>
              <p className="text-sm opacity-80">
                Unlock it with a pass or subscription on the{" "}
                <Link className="underline" href="/pricing">
                  pricing page
                </Link>
                . Free lessons and previews stay open to everyone.
              </p>
              {!session && (
                <p className="text-sm">
                  <a className="underline" href={loginHref}>
                    Log in
                  </a>{" "}
                  if you already have access.
                </p>
              )}
            </section>
          ) : (
            body && (
              <>
                {slug === "data-structures-and-algorithms" && <LanguageHero slug={lessonSlug} />}
                {lessonSlug === "course-roadmap" && slug === "data-structures-and-algorithms" && (
                  <RoadmapAnimation
                    chapters={sections.length}
                    lessons={sections.reduce((n, s) => n + (s.lessons ?? []).length, 0)}
                  />
                )}
                <LessonBlocks
                  blocks={body.blocks ?? []}
                  lessonId={body.id ?? ""}
                  csrfToken={session?.csrfToken ?? null}
                  loginHref={loginHref}
                />
                <LessonActions
                  csrfToken={session?.csrfToken ?? null}
                  lessonId={body.id ?? ""}
                  completed={done.has(body.id ?? "")}
                  courseSlug={slug}
                  lessonSlug={lessonSlug}
                />
              </>
            )
          )}
          <nav
            aria-label="Lesson navigation"
            className="flex justify-between border-t pt-4 text-sm"
          >
            {body?.previous ? (
              <Link
                className="underline"
                href={`/courses/${slug}/lessons/${body.previous.slug}`}
                data-testid="prev"
              >
                ← {body.previous.title}
              </Link>
            ) : (
              <span />
            )}
            {body?.next ? (
              <Link
                className="underline"
                href={`/courses/${slug}/lessons/${body.next.slug}`}
                data-testid="next"
              >
                {body.next.title} →
              </Link>
            ) : (
              <span />
            )}
          </nav>
        </article>

        <aside aria-label="On this page" className="hidden text-sm lg:block">
          <div className="sticky top-20">
            <ReadingProgress />
            {toc.length > 0 && (
              <nav className="flex flex-col gap-1" data-testid="toc">
                <h2 className="mb-1 font-medium opacity-80">On this page</h2>
                {toc.map((t) => (
                  <a
                    key={t.id}
                    href={`#${t.id}`}
                    className={`hover:underline ${INDENT[Math.min(Math.max(t.level - 1, 0), 3)]}`}
                  >
                    {t.text}
                  </a>
                ))}
              </nav>
            )}
          </div>
        </aside>
      </div>
    </ReaderShell>
  );
}
