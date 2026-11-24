"use client";

import Link from "next/link";
import { ChevronDown, Lock, Search } from "lucide-react";
import { useState } from "react";

export type SidebarLesson = {
  id: string;
  slug: string;
  title: string;
  premium: boolean;
  preview: boolean;
};
export type SidebarSection = { title: string; lessons: SidebarLesson[] };

/** Course outline: progress, chapter search and collapsible chapters with done/total counts. */
export function CourseSidebar({
  courseSlug,
  courseTitle,
  sections,
  doneIds,
  currentSlug,
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
  enrolled: boolean;
  percent: number;
  completed: number;
  total: number;
}) {
  const [q, setQ] = useState("");
  const done = new Set(doneIds);
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
        const lessons = needle
          ? s.lessons.filter((l) => l.title.toLowerCase().includes(needle))
          : s.lessons;
        if (needle && lessons.length === 0 && !s.title.toLowerCase().includes(needle)) return null;
        const shown = needle && lessons.length === 0 ? s.lessons : lessons;
        const doneCount = s.lessons.filter((l) => done.has(l.id)).length;
        const hasCurrent = s.lessons.some((l) => l.slug === currentSlug);
        return (
          <details key={s.title} open={hasCurrent || Boolean(needle)} className="group">
            <summary className="flex cursor-pointer list-none items-center gap-2 rounded-lg px-1 py-1.5 font-semibold hover:bg-surface-2">
              <ChevronDown
                size={14}
                aria-hidden
                className="shrink-0 text-muted transition group-open:rotate-180"
              />
              <span className="flex-1">{s.title}</span>
              <span className="text-xs font-normal text-muted">
                {doneCount}/{s.lessons.length}
              </span>
            </summary>
            <ol className="ml-3 mt-1 flex flex-col gap-0.5 border-l pl-2">
              {shown.map((l) => {
                const current = l.slug === currentSlug;
                return (
                  <li
                    key={l.slug}
                    className={`flex items-center gap-2 rounded-lg px-2 py-1.5 ${current ? "bg-accent/15" : "hover:bg-surface-2"}`}
                    data-testid="sidebar-lesson"
                  >
                    <span aria-hidden className={done.has(l.id) ? "text-accent-2" : "text-muted"}>
                      {done.has(l.id) ? "✓" : "○"}
                    </span>
                    <Link
                      href={`/courses/${courseSlug}/lessons/${l.slug}`}
                      aria-current={current ? "page" : undefined}
                      className={
                        current
                          ? "flex-1 font-semibold text-accent-2"
                          : "flex-1 text-foreground/85 hover:text-foreground"
                      }
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
