import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Markdown } from "@/components/markdown";
import { DifficultyBadge, statusLabel } from "@/components/reader/problem-bits";
import { BookmarkButton } from "@/components/reader/bookmark-button";
import { Hints } from "@/components/reader/hints";
import { ReaderShell } from "@/components/reader/ui";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

type Problem = components["schemas"]["PublicProblem"];
type States = components["schemas"]["ProblemStates"];

export async function generateMetadata({
  params,
}: PageProps<"/problems/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  if (!SLUG.test(slug)) return {};
  const r = await readerGet<Problem>(`problems/${slug}`);
  return r.ok ? { title: `${r.data.title} · EngineerHub` } : { title: "Problem · EngineerHub" };
}

export default async function ProblemPage({ params }: PageProps<"/problems/[slug]">) {
  const { slug } = await params;
  if (!SLUG.test(slug)) notFound();
  const session = await readerSession();
  const r = await readerGet<Problem>(`problems/${slug}`, session?.accessToken);
  const locked = !r.ok && r.status === 403 && r.code === "ENTITLEMENT_REQUIRED";
  if (!r.ok && !locked) {
    if (r.status === 404) notFound();
    throw new Error("Problem unavailable");
  }
  const states = session
    ? await readerGet<States>("me/problems/states", session.accessToken)
    : null;
  const state = states?.ok ? (states.data.states ?? []).find((s) => s.slug === slug) : undefined;
  const p = r.ok ? r.data : null;
  const loginHref = `/auth/login?returnTo=${encodeURIComponent(`/problems/${slug}`)}`;

  return (
    <ReaderShell username={session?.username}>
      <p className="text-sm">
        <Link className="underline" href="/problems">
          ← All problems
        </Link>
      </p>
      {locked || !p ? (
        <section
          className="flex flex-col gap-3 rounded border p-6"
          data-testid="locked"
          aria-label="Premium content"
        >
          <h1 className="text-2xl font-semibold">Premium problem</h1>
          <p className="font-medium">This problem is part of the premium content.</p>
          <p className="text-sm opacity-80">
            Premium plans are not available yet. Free problems stay open to everyone.
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
        <article className="flex flex-col gap-6">
          <header className="flex flex-col gap-2">
            <h1 className="text-3xl font-semibold" data-testid="problem-title">
              {p.title}
            </h1>
            <p className="flex flex-wrap items-center gap-2 text-xs opacity-80">
              <DifficultyBadge level={p.difficulty} />
              <span>{p.timeLimitMs} ms</span>
              <span>{p.memoryLimitMb} MB</span>
              {(p.topics ?? []).map((t) => (
                <Link key={t.slug} className="underline" href={`/problems?topic=${t.slug}`}>
                  {t.name}
                </Link>
              ))}
              {(p.patterns ?? []).map((t) => (
                <Link key={t.slug} className="underline" href={`/problems?pattern=${t.slug}`}>
                  {t.name}
                </Link>
              ))}
              {session && <span data-testid="problem-state">{statusLabel(state)}</span>}
            </p>
            <div>
              <BookmarkButton
                csrfToken={session?.csrfToken ?? null}
                slug={slug}
                initial={state?.bookmarked ?? false}
              />
            </div>
          </header>

          <div data-testid="statement">
            <Markdown text={p.statement ?? ""} />
          </div>
          {p.inputFormat && (
            <section>
              <h2 className="text-lg font-medium">Input</h2>
              <Markdown text={p.inputFormat} />
            </section>
          )}
          {p.outputFormat && (
            <section>
              <h2 className="text-lg font-medium">Output</h2>
              <Markdown text={p.outputFormat} />
            </section>
          )}
          {p.constraints && (
            <section>
              <h2 className="text-lg font-medium">Constraints</h2>
              <Markdown text={p.constraints} />
            </section>
          )}
          <section aria-label="Examples" className="flex flex-col gap-3">
            <h2 className="text-lg font-medium">Examples</h2>
            {(p.samples ?? []).map((s, i) => (
              <div key={i} data-testid="sample">
                <h3 className="text-sm font-medium">Example {i + 1}</h3>
                <pre className="overflow-x-auto rounded bg-black/10 p-3 text-xs dark:bg-white/15">{`Input:\n${s.input}\nOutput:\n${s.expected}`}</pre>
                {s.explanation && <p className="text-sm">{s.explanation}</p>}
              </div>
            ))}
          </section>
          <Hints hints={p.hints ?? []} />
          <p className="rounded border p-3 text-sm opacity-80" data-testid="no-judge">
            Writing and running code arrives with the online judge. For now you can read the problem
            and bookmark it.
          </p>
        </article>
      )}
    </ReaderShell>
  );
}
