import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { LessonActions } from "@/components/reader/lesson-actions";
import { LessonBlocks } from "@/components/reader/lesson-blocks";
import { ProgressBar, ReaderShell, TierBadge } from "@/components/reader/ui";
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
        <aside
          aria-label="Course outline"
          className="glass sticky top-20 flex max-h-[calc(100vh-6rem)] flex-col gap-4 self-start overflow-y-auto p-4 text-sm"
          data-testid="sidebar"
        >
          <Link href={`/courses/${slug}`} className="font-semibold hover:text-accent-2">
            {detail.data.course?.title}
          </Link>
          {p?.enrolled && <ProgressBar percent={p.percent ?? 0} label="Course progress" />}
          {sections.map((s) => (
            <div key={s.position}>
              <h2 className="mb-2 text-[11px] font-semibold uppercase tracking-widest text-muted">
                {s.title}
              </h2>
              <ol className="flex flex-col gap-1">
                {(s.lessons ?? []).map((l) => {
                  const current = l.slug === lessonSlug;
                  return (
                    <li
                      key={l.slug}
                      className={`flex items-center gap-2 rounded-lg px-2 py-1.5 ${current ? "bg-accent/15" : "hover:bg-surface-2"}`}
                      data-testid="sidebar-lesson"
                    >
                      <span
                        aria-hidden
                        className={done.has(l.id ?? "") ? "text-accent-2" : "text-muted"}
                      >
                        {done.has(l.id ?? "") ? "✓" : "○"}
                      </span>
                      <Link
                        href={`/courses/${slug}/lessons/${l.slug}`}
                        aria-current={current ? "page" : undefined}
                        className={
                          current
                            ? "font-semibold text-accent-2"
                            : "text-foreground/85 hover:text-foreground"
                        }
                      >
                        {l.title}
                      </Link>
                      <TierBadge tier={l.accessTier} preview={l.freePreview} />
                    </li>
                  );
                })}
              </ol>
            </div>
          ))}
        </aside>

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
          {toc.length > 0 && (
            <nav className="sticky top-6 flex flex-col gap-1" data-testid="toc">
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
        </aside>
      </div>
    </ReaderShell>
  );
}
