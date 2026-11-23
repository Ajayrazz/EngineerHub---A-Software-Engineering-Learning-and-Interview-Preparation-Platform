import type { Metadata } from "next";
import Link from "next/link";
import { ReaderShell, TierBadge } from "@/components/reader/ui";
import { Notice, btn, input } from "@/components/admin/ui";
import { pageParam, query } from "@/lib/admin/policy";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

type Page = components["schemas"]["PublicCoursePage"];
type Topic = components["schemas"]["TopicView"];

export const metadata: Metadata = {
  title: "Courses · EngineerHub",
  description:
    "Software engineering courses: data structures, algorithms and interview preparation.",
};

const DIFFICULTIES = ["beginner", "intermediate", "advanced"];
const TIERS = ["free", "premium"];
const pick = (v: string | string[] | undefined) => (typeof v === "string" ? v : "");

export default async function CoursesPage({ searchParams }: PageProps<"/courses">) {
  const sp = await searchParams;
  const session = await readerSession();
  const topic = SLUG.test(pick(sp.topic)) ? pick(sp.topic) : "";
  const difficulty = DIFFICULTIES.includes(pick(sp.difficulty)) ? pick(sp.difficulty) : "";
  const tier = TIERS.includes(pick(sp.tier)) ? pick(sp.tier) : "";
  const q = pick(sp.q).slice(0, 100);
  const page = pageParam(sp.page);

  const [courses, topics] = await Promise.all([
    readerGet<Page>(`courses${query({ topic, difficulty, tier, q, page, size: 12 })}`),
    readerGet<Topic[]>("topics"),
  ]);
  const link = (p: number) => `/courses${query({ topic, difficulty, tier, q, page: p })}`;

  return (
    <ReaderShell username={session?.username}>
      <h1 className="text-2xl font-semibold">Courses</h1>
      <form method="get" className="flex flex-wrap items-end gap-2" role="search">
        <label className="flex flex-col gap-1 text-sm">
          Search
          <input name="q" defaultValue={q} maxLength={100} className={input} />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Topic
          <select name="topic" defaultValue={topic} className={input}>
            <option value="">any</option>
            {(topics.ok ? topics.data : []).map((t) => (
              <option key={t.slug} value={t.slug}>
                {t.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Level
          <select name="difficulty" defaultValue={difficulty} className={input}>
            <option value="">any</option>
            {DIFFICULTIES.map((d) => (
              <option key={d}>{d}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-sm">
          Access
          <select name="tier" defaultValue={tier} className={input}>
            <option value="">any</option>
            {TIERS.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <button className={btn} type="submit">
          Search
        </button>
      </form>

      {!courses.ok ? (
        <Notice kind="error">Courses could not be loaded. Please try again shortly.</Notice>
      ) : (
        <>
          <ul className="grid gap-4 sm:grid-cols-2" data-testid="course-cards">
            {(courses.data.items ?? []).map((c) => (
              <li
                key={c.slug}
                className="flex flex-col gap-2 rounded border p-4"
                data-testid="course-card"
              >
                <Link href={`/courses/${c.slug}`} className="text-lg font-medium underline">
                  {c.title}
                </Link>
                <p className="text-sm opacity-80">{c.description}</p>
                <p className="flex flex-wrap items-center gap-2 text-xs opacity-80">
                  {c.topic?.name && <span>{c.topic.name}</span>}
                  <span>{c.difficulty}</span>
                  <span>{c.lessonCount} lessons</span>
                  <TierBadge tier={c.accessTier} />
                </p>
              </li>
            ))}
          </ul>
          {(courses.data.items ?? []).length === 0 && <p className="text-sm">No courses found.</p>}
          <div className="flex gap-3 text-sm">
            {page > 0 && (
              <Link className="underline" href={link(page - 1)}>
                Previous
              </Link>
            )}
            {(page + 1) * 12 < (courses.data.total ?? 0) && (
              <Link className="underline" href={link(page + 1)}>
                Next
              </Link>
            )}
          </div>
        </>
      )}
    </ReaderShell>
  );
}
