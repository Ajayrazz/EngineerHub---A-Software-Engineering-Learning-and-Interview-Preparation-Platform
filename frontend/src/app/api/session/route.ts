import { NextRequest, NextResponse } from "next/server";
import { sessionCookie } from "@/lib/auth/cookies";
import { loadSession } from "@/lib/auth/current";
import { NO_STORE } from "@/lib/auth/http";

export const dynamic = "force-dynamic";

/** What the browser UI may know about the session: identity + CSRF token, never any token. */
export async function GET(request: NextRequest) {
  const auth = await loadSession(request.cookies.get(sessionCookie().name)?.value);
  if (!auth) return NextResponse.json({ authenticated: false }, { headers: NO_STORE });
  const { sub, username, name, csrfToken } = auth.session;
  return NextResponse.json(
    { authenticated: true, user: { sub, username, name }, csrfToken },
    { headers: NO_STORE },
  );
}
