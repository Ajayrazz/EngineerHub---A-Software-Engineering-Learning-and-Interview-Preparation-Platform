import "server-only";
import { cookies } from "next/headers";
import { getSessionStore } from "../session/services";
import type { Session } from "../session/types";
import { sessionCookie } from "./cookies";
import { ensureFreshSession } from "./refresh";
import { refreshTokens } from "./oidc";

export type Authenticated = { sid: string; session: Session };

/** Loads the session for a raw cookie value (null if absent/invalid/expired). */
export async function loadSession(cookieValue: string | undefined): Promise<Authenticated | null> {
  if (!cookieValue) return null;
  const store = await getSessionStore();
  const session = await store.load(cookieValue);
  return session ? { sid: cookieValue, session } : null;
}

/** For Server Components / Server Functions. */
export async function currentSession(): Promise<Authenticated | null> {
  const jar = await cookies();
  return loadSession(jar.get(sessionCookie().name)?.value);
}

/** Returns the session with a non-expired access token, refreshing under a lock if needed. */
export async function withFreshToken(auth: Authenticated): Promise<Authenticated | null> {
  const store = await getSessionStore();
  const fresh = await ensureFreshSession(auth.sid, auth.session, { store, refresh: refreshTokens });
  return fresh ? { sid: auth.sid, session: fresh } : null;
}
