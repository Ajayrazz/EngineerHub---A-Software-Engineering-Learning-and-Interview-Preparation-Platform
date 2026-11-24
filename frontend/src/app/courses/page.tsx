import type { Metadata } from "next";
import { CourseCard } from "@/components/reader/course-card";
import { PageHeader, ReaderShell } from "@/components/reader/ui";
import { Notice, btn, input } from "@/components/admin/ui";
import { query } from "@/lib/admin/policy";
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

/** Catalogue sections; a course is listed under the category of its topic. */
const ORDER = [
  "data-structures-and-algorithms",
  "system-design-fundamentals",
  "system-design-interviews",
  "low-level-design",
  "concurrency-interview",
  "sql-interview",
  "behavioral-interviews",
  "job-search",
  "ai-engineering",
  "genai-system-design",
  "ml-system-design",
  "agentic-coding",
  "operating-systems",
  "computer-networks",
  "microservices",
  "api-design",
  "apache-kafka",
  "java",
  "python",
  "cpp",
  "csharp",
  "go",
  "git",
];
const rank = (slug?: string) => {
  const i = ORDER.indexOf(slug ?? "");
  return i < 0 ? ORDER.length : i;
};
const CATEGORIES = [
  {
    title: "Interview Preparation",
    lead: "Prepare for technical interviews at top tech companies.",
    topics: [
      "arrays",
      "system-design",
      "low-level-design",
      "concurrency",
      "databases",
      "behavioral-interviews",
      "job-search",
    ],
  },
  {
    title: "AI/ML",
    lead: "Build and deploy intelligent systems using modern AI and machine learning.",
    topics: ["llm-engineering", "machine-learning", "ai-system-design", "agentic-coding"],
  },
  {
    title: "Core CS",
    lead: "A deep understanding of the computer science fundamentals.",
    topics: ["operating-systems", "networking"],
  },
  {
    title: "Backend",
    lead: "Design APIs and build the event-driven systems behind production backends.",
    topics: ["microservices", "web-apis", "kafka"],
  },
  {
    title: "Programming Languages",
    lead: "Master popular languages from basics to advanced.",
    topics: ["python", "java", "cpp", "csharp", "go"],
  },
  {
    title: "Developer Tools",
    lead: "Essential tools every developer should know.",
    topics: ["git"],
  },
];

export default async function CoursesPage({ searchParams }: PageProps<"/courses">) {
  const sp = await searchParams;
  const session = await readerSession();
  const topic = SLUG.test(pick(sp.topic)) ? pick(sp.topic) : "";
  const difficulty = DIFFICULTIES.includes(pick(sp.difficulty)) ? pick(sp.difficulty) : "";
  const tier = TIERS.includes(pick(sp.tier)) ? pick(sp.tier) : "";
  const q = pick(sp.q).slice(0, 100);

  const [courses, topics] = await Promise.all([
    readerGet<Page>(`courses${query({ topic, difficulty, tier, q, size: 100 })}`),
    readerGet<Topic[]>("topics"),
  ]);

  return (
    <ReaderShell username={session?.username} wide>
      <PageHeader
        title="Courses"
        lead="Structured learning paths to master programming concepts, ace technical interviews and become a better engineer."
      />
      <form method="get" className="glass flex flex-wrap items-end gap-3 p-4" role="search">
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
          {CATEGORIES.map((cat) => {
            const items = (courses.data.items ?? [])
              .filter((c) => cat.topics.includes(c.topic?.slug ?? ""))
              .sort((a, b) => rank(a.slug) - rank(b.slug));
            if (items.length === 0) return null;
            return (
              <section key={cat.title} aria-label={cat.title} className="flex flex-col gap-4">
                <div>
                  <h2 className="text-2xl font-bold tracking-tight">{cat.title}</h2>
                  <p className="text-muted">{cat.lead}</p>
                </div>
                <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4" data-testid="course-cards">
                  {items.map((c) => (
                    <CourseCard
                      key={c.slug}
                      slug={c.slug}
                      title={c.title}
                      description={c.description}
                      topicName={c.topic?.name}
                      difficulty={c.difficulty}
                      lessonCount={c.lessonCount}
                      tier={c.accessTier}
                    />
                  ))}
                </ul>
              </section>
            );
          })}
          {(courses.data.items ?? []).some(
            (c) => !CATEGORIES.some((cat) => cat.topics.includes(c.topic?.slug ?? "")),
          ) && (
            <section className="flex flex-col gap-4" aria-label="More courses">
              <h2 className="text-2xl font-bold tracking-tight">More courses</h2>
              <ul className="grid gap-5 sm:grid-cols-2 lg:grid-cols-4" data-testid="course-cards">
                {(courses.data.items ?? [])
                  .filter(
                    (c) => !CATEGORIES.some((cat) => cat.topics.includes(c.topic?.slug ?? "")),
                  )
                  .map((c) => (
                    <CourseCard
                      key={c.slug}
                      slug={c.slug}
                      title={c.title}
                      description={c.description}
                      topicName={c.topic?.name}
                      difficulty={c.difficulty}
                      lessonCount={c.lessonCount}
                      tier={c.accessTier}
                    />
                  ))}
              </ul>
            </section>
          )}
          {(courses.data.items ?? []).length === 0 && <p className="text-sm">No courses found.</p>}
        </>
      )}
    </ReaderShell>
  );
}
