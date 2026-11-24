import type { Metadata } from "next";
import Link from "next/link";
import { Notice, btn, input } from "@/components/admin/ui";
import { ProblemTable } from "@/components/reader/problem-bits";
import { PageHeader, ReaderShell } from "@/components/reader/ui";
import { pageParam, query } from "@/lib/admin/policy";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

type Page = components["schemas"]["PublicProblemPage"];
type Topic = components["schemas"]["TopicView"];
type Pattern = components["schemas"]["PublicPattern"];
type States = components["schemas"]["ProblemStates"];
type State = components["schemas"]["ProblemState"];

export const metadata: Metadata = {
  title: "Problems · EngineerHub",
  description: "Practice problems by topic, pattern and difficulty.",
};

const DIFFICULTIES = ["easy", "medium", "hard"];
const TIERS = ["free", "premium"];
const SORTS = ["newest", "title", "difficulty"];
const pick = (v: string | string[] | undefined) => (typeof v === "string" ? v : "");

export default async function ProblemsPage({ searchParams }: PageProps<"/problems">) {
  const sp = await searchParams;
  const session = await readerSession();
  const topic = SLUG.test(pick(sp.topic)) ? pick(sp.topic) : "";
  const pattern = SLUG.test(pick(sp.pattern)) ? pick(sp.pattern) : "";
  const difficulty = DIFFICULTIES.includes(pick(sp.difficulty)) ? pick(sp.difficulty) : "";
  const tier = TIERS.includes(pick(sp.tier)) ? pick(sp.tier) : "";
  const sort = SORTS.includes(pick(sp.sort)) ? pick(sp.sort) : "";
  const q = pick(sp.q).slice(0, 100);
  const page = pageParam(sp.page);
  const noMatch = pick(sp.nomatch) === "1";

  const [problems, topics, patterns, states] = await Promise.all([
    readerGet<Page>(
      `problems${query({ topic, pattern, difficulty, tier, sort, q, page, size: 20 })}`,
    ),
    readerGet<Topic[]>("topics"),
    readerGet<Pattern[]>("patterns"),
    session ? readerGet<States>("me/problems/states", session.accessToken) : Promise.resolve(null),
  ]);
  const stateMap = new Map<string, State>(
    (states?.ok ? (states.data.states ?? []) : []).map((s) => [s.slug ?? "", s]),
  );
  const link = (p: number) =>
    `/problems${query({ topic, pattern, difficulty, tier, sort, q, page: p })}`;
  const randomHref = `/problems/random${query({ topic, pattern, difficulty, tier })}`;

  return (
    <ReaderShell username={session?.username} wide>
      <PageHeader
        title="Problems"
        lead="Original practice problems by pattern and difficulty, with hints when you are stuck."
      />
      {noMatch && <Notice kind="error">No problem matches those filters.</Notice>}
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
          Pattern
          <select name="pattern" defaultValue={pattern} className={input}>
            <option value="">any</option>
            {(patterns.ok ? patterns.data : []).map((t) => (
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
        <label className="flex flex-col gap-1 text-sm">
          Sort
          <select name="sort" defaultValue={sort} className={input}>
            <option value="">newest</option>
            {SORTS.slice(1).map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <button className={btn} type="submit">
          Search
        </button>
        <a className={btn} href={randomHref} data-testid="random">
          Random
        </a>
      </form>

      {!problems.ok ? (
        <Notice kind="error">Problems could not be loaded. Please try again shortly.</Notice>
      ) : (
        <>
          <ProblemTable items={problems.data.items ?? []} states={stateMap} signedIn={!!session} />
          {(problems.data.items ?? []).length === 0 && (
            <p className="text-sm">No problems found.</p>
          )}
          <div className="flex items-center gap-3 text-sm">
            <span>{problems.data.total ?? 0} problem(s)</span>
            {page > 0 && (
              <Link className="underline" href={link(page - 1)}>
                Previous
              </Link>
            )}
            {(page + 1) * 20 < (problems.data.total ?? 0) && (
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
