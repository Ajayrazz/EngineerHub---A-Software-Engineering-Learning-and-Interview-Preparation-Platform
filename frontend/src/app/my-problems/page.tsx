import type { Metadata } from "next";
import Link from "next/link";
import { redirect } from "next/navigation";
import { Notice } from "@/components/admin/ui";
import { ProblemTable } from "@/components/reader/problem-bits";
import { ReaderShell } from "@/components/reader/ui";
import type { components } from "@/lib/api/schema";
import { readerGet, readerSession } from "@/lib/reader/server";

type Item = components["schemas"]["PublicProblemItem"];
type States = components["schemas"]["ProblemStates"];
type State = components["schemas"]["ProblemState"];

export const metadata: Metadata = { title: "My problems · EngineerHub" };

export default async function MyProblems() {
  const session = await readerSession();
  if (!session) redirect("/auth/login?returnTo=/my-problems");
  const [bookmarks, states] = await Promise.all([
    readerGet<Item[]>("me/bookmarks", session.accessToken),
    readerGet<States>("me/problems/states", session.accessToken),
  ]);
  const stateMap = new Map<string, State>(
    (states.ok ? (states.data.states ?? []) : []).map((s) => [s.slug ?? "", s]),
  );
  return (
    <ReaderShell username={session.username} wide>
      <h1 className="text-2xl font-semibold">My problems</h1>
      {!bookmarks.ok ? (
        <Notice kind="error">Your bookmarks could not be loaded. Please try again shortly.</Notice>
      ) : bookmarks.data.length === 0 ? (
        <p className="text-sm" data-testid="no-bookmarks">
          You have no bookmarked problems.{" "}
          <Link className="underline" href="/problems">
            Browse problems
          </Link>
        </p>
      ) : (
        <ProblemTable items={bookmarks.data} states={stateMap} signedIn />
      )}
    </ReaderShell>
  );
}
