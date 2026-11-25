"use client";

import Link from "next/link";
import { CheckCircle2, ChevronDown, Code2, Lock, Search } from "lucide-react";
import { useState } from "react";

export type SidebarLesson = {
  id: string;
  slug: string;
  title: string;
  premium: boolean;
  preview: boolean;
};
export type SidebarSection = { title: string; lessons: SidebarLesson[] };
export type PracticeItem = { slug: string; title: string; difficulty: string };

const DIFF_COLOR: Record<string, string> = {
  easy: "text-emerald-400",
  medium: "text-amber-400",
  hard: "text-rose-400",
};

/** Course outline: progress, chapter search and collapsible chapters with done/total counts. */
export function CourseSidebar({
  courseSlug,
  courseTitle,
  sections,
  doneIds,
  currentSlug,
  practice = {},
  solved = [],
  enrolled,
  percent,
  completed,
  total,
}: {
  courseSlug: string;
  courseTitle: string;
  sections: SidebarSection[];
  doneIds: string[];
  currentSlug: string;
  /** lesson slug -> the practice problems linked from that lesson (derived from the content) */
  practice?: Record<string, PracticeItem[]>;
  solved?: string[];
  enrolled: boolean;
  percent: number;
  completed: number;
  total: number;
}) {
  const [q, setQ] = useState("");
  const done = new Set(doneIds);
  const solvedSet = new Set(solved);
  const needle = q.trim().toLowerCase();
  return (
    <aside
      aria-label="Course outline"
      className="glass sticky top-20 flex max-h-[calc(100vh-6rem)] flex-col gap-3 self-start overflow-y-auto p-4 text-sm"
      data-testid="sidebar"
    >
      <Link href="/courses" className="text-xs text-muted hover:text-foreground">
        ← All courses
      </Link>
      <Link href={`/courses/${courseSlug}`} className="font-semibold hover:text-accent-2">
        {courseTitle}
      </Link>
      <div>
        <div className="flex items-baseline justify-between">
          <span className="text-2xl font-bold" data-testid="sidebar-percent">
            {enrolled ? percent : 0}
            <span className="text-sm text-muted">%</span>
          </span>
          <span className="text-xs text-muted" data-testid="sidebar-count">
            {enrolled ? completed : 0}/{total} lessons
          </span>
        </div>
        <progress
          className="meter mt-1"
          value={enrolled ? percent : 0}
          max={100}
          aria-label="Course progress"
        />
      </div>
      <label className="relative block">
        <Search
          size={14}
          aria-hidden
          className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted"
        />
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder="Search chapters"
          aria-label="Search chapters"
          maxLength={60}
          className="w-full rounded-lg border bg-surface py-1.5 pl-8 pr-2"
        />
      </label>
      {sections.map((s) => {
        const items = s.lessons.map((l) => ({ l, probs: practice[l.slug] ?? [] }));
        const probTotal = new Set(items.flatMap((x) => x.probs.map((p) => p.slug))).size;
        const probSolved = new Set(
          items.flatMap((x) => x.probs.filter((p) => solvedSet.has(p.slug)).map((p) => p.slug)),
        ).size;
        const hasCurrent = s.lessons.some((l) => l.slug === currentSlug);
        const visible = items
          .map(({ l, probs }) => {
            if (!needle) return { l, probs };
            const lessonHit =
              l.title.toLowerCase().includes(needle) || s.title.toLowerCase().includes(needle);
            const hits = probs.filter((p) => p.title.toLowerCase().includes(needle));
            return lessonHit ? { l, probs } : hits.length ? { l, probs: hits } : null;
          })
          .filter((x): x is { l: SidebarLesson; probs: PracticeItem[] } => x !== null);
        if (needle && visible.length === 0) return null;
        const countText =
          probTotal > 0
            ? `${probSolved}/${probTotal}`
            : `${s.lessons.filter((l) => done.has(l.id)).length}/${s.lessons.length}`;
        return (
          <details key={s.title} open={hasCurrent || Boolean(needle)} className="group">
            <summary className="flex cursor-pointer list-none items-center gap-2 rounded-lg px-1 py-1.5 font-semibold hover:bg-surface-2">
              <ChevronDown
                size={14}
                aria-hidden
                className="shrink-0 text-muted transition group-open:rotate-180"
              />
              <span className="flex-1">{s.title}</span>
              <span className="text-xs font-normal text-muted" data-testid="sidebar-section-count">
                {countText}
              </span>
            </summary>
            <ol className="ml-3 mt-1 flex flex-col gap-0.5 border-l pl-2">
              {visible.map(({ l, probs }) => {
                const current = l.slug === currentSlug;
                const grouped = probs.length > 0;
                return (
                  <li key={l.slug} data-testid="sidebar-lesson" className="flex flex-col">
                    <div
                      className={`flex items-center gap-2 rounded-lg px-2 py-1.5 ${current ? "bg-accent/15" : "hover:bg-surface-2"}`}
                    >
                      <span aria-hidden className={done.has(l.id) ? "text-accent-2" : "text-muted"}>
                        {done.has(l.id) ? "✓" : "○"}
                      </span>
                      <Link
                        href={`/courses/${courseSlug}/lessons/${l.slug}`}
                        aria-current={current ? "page" : undefined}
                        className={`flex-1 ${grouped ? "font-semibold text-accent-2" : current ? "font-semibold text-accent-2" : "text-foreground/85 hover:text-foreground"}`}
                      >
                        {l.title}
                      </Link>
                      {l.premium && (
                        <span
                          className="chip py-0 text-[10px] text-warn"
                          title={l.preview ? "Free preview" : "Premium"}
                        >
                          {l.preview ? "preview" : <Lock size={10} aria-label="premium" />}
                        </span>
                      )}
                    </div>
                    {grouped && (
                      <ul className="ml-2 flex flex-col gap-0.5 border-l pl-2">
                        {probs.map((p) => (
                          <li key={p.slug} data-testid="sidebar-problem">
                            <Link
                              href={`/problems/${p.slug}`}
                              className="flex items-center gap-2 rounded-lg px-2 py-1.5 text-foreground/80 hover:bg-surface-2 hover:text-foreground"
                            >
                              {solvedSet.has(p.slug) ? (
                                <CheckCircle2
                                  size={16}
                                  aria-label="solved"
                                  className="shrink-0 text-accent-2"
                                />
                              ) : (
                                <Code2
                                  size={16}
                                  aria-hidden
                                  className={`shrink-0 ${DIFF_COLOR[p.difficulty] ?? "text-muted"}`}
                                />
                              )}
                              <span className="flex-1">{p.title}</span>
                            </Link>
                          </li>
                        ))}
                      </ul>
                    )}
                  </li>
                );
              })}
            </ol>
          </details>
        );
      })}
    </aside>
  );
}
