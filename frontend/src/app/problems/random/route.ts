import { NextRequest, NextResponse } from "next/server";
import { NO_STORE } from "@/lib/auth/http";
import { query } from "@/lib/admin/policy";
import { getEnv } from "@/lib/env";
import { readerGet, readerSession, SLUG } from "@/lib/reader/server";

export const dynamic = "force-dynamic";

/** Picks a random problem for the given filters (validated by the API) and redirects to it. */
export async function GET(request: NextRequest) {
  const p = request.nextUrl.searchParams;
  const allowed = (name: string, ok: (v: string) => boolean) => {
    const v = p.get(name) ?? "";
    return ok(v) ? v : "";
  };
  const filters = {
    topic: allowed("topic", (v) => SLUG.test(v)),
    pattern: allowed("pattern", (v) => SLUG.test(v)),
    difficulty: allowed("difficulty", (v) => ["easy", "medium", "hard"].includes(v)),
    tier: allowed("tier", (v) => ["free", "premium"].includes(v)),
  };
  const session = await readerSession();
  const r = await readerGet<{ slug?: string }>(
    `problems/random${query({ ...filters, unsolved: session ? "true" : "" })}`,
    session?.accessToken,
  );
  const base = getEnv().APP_URL;
  const target =
    r.ok && r.data.slug && SLUG.test(r.data.slug)
      ? `/problems/${r.data.slug}`
      : `/problems${query({ ...filters, nomatch: 1 })}`;
  return NextResponse.redirect(new URL(target, base), { status: 302, headers: NO_STORE });
}
