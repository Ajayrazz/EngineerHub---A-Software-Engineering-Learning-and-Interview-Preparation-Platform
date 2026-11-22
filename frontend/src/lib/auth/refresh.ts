import type { SessionStore } from "../session/store";
import type { Session } from "../session/types";
import { randomToken } from "../session/crypto";

export type TokenSet = {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in?: number;
};

/** The IdP definitively refused the refresh token (revoked, expired, reused): the session is over. */
export class RefreshRejectedError extends Error {
  constructor(message = "refresh token rejected") {
    super(message);
    this.name = "RefreshRejectedError";
  }
}

/** The IdP could not be reached or failed transiently: the session is still valid, try again later. */
export class RefreshUnavailableError extends Error {
  constructor(options?: { cause?: unknown }) {
    super("identity provider unavailable", options);
    this.name = "RefreshUnavailableError";
  }
}

export type RefreshDeps = {
  store: SessionStore;
  /**
   * Exchanges a refresh token for new tokens at the IdP. Must throw {@link RefreshRejectedError} for a
   * permanent refusal (e.g. invalid_grant); any other error is treated as transient.
   */
  refresh: (refreshToken: string) => Promise<TokenSet>;
  now?: () => number; // epoch seconds
  sleep?: (ms: number) => Promise<void>;
  /** Refresh when the access token has fewer than this many seconds left. */
  skewSeconds?: number;
  /** Max time to wait for another request that holds the refresh lock. */
  waitMs?: number;
};

/**
 * Returns the session with a usable access token, refreshing it if needed, or `null` if the session is
 * gone or can no longer be refreshed (caller must then require a fresh login). Throws
 * {@link RefreshUnavailableError} when the IdP is temporarily unreachable: the session is KEPT, so an
 * identity-provider outage does not log everyone out.
 *
 * Keycloak rotates refresh tokens and treats re-use of an old one as theft (revoking the whole
 * session), so concurrent refreshes would log the user out. A per-session lock serialises them: one
 * request refreshes, the others wait and then read the refreshed session.
 */
export async function ensureFreshSession(
  sid: string,
  session: Session,
  deps: RefreshDeps,
): Promise<Session | null> {
  const now = deps.now ?? (() => Math.floor(Date.now() / 1000));
  const sleep = deps.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const skew = deps.skewSeconds ?? 30;
  const deadline = Date.now() + (deps.waitMs ?? 5000);
  const needsRefresh = (s: Session) => s.accessExpiresAt - now() <= skew;

  let current: Session = session;
  while (needsRefresh(current)) {
    const owner = randomToken(16);
    if (await deps.store.acquireRefreshLock(sid, owner)) {
      try {
        // Re-read: another request may have refreshed between our load and acquiring the lock.
        const latest = await deps.store.load(sid);
        if (!latest) return null;
        if (!needsRefresh(latest)) return latest;
        let tokens: TokenSet;
        try {
          tokens = await deps.refresh(latest.refreshToken);
        } catch (error) {
          if (error instanceof RefreshRejectedError) {
            await deps.store.destroy(sid); // refresh token rejected/expired: session is over
            return null;
          }
          throw new RefreshUnavailableError({ cause: error }); // transient: keep the session
        }
        const updated: Session = {
          ...latest,
          accessToken: tokens.access_token,
          accessExpiresAt: now() + (tokens.expires_in ?? 60),
          refreshToken: tokens.refresh_token ?? latest.refreshToken,
          idToken: tokens.id_token ?? latest.idToken,
          refreshedAt: now(),
        };
        await deps.store.save(sid, updated);
        return updated;
      } finally {
        await deps.store.releaseRefreshLock(sid, owner);
      }
    }
    // Someone else is refreshing: wait for them, then re-check with their result.
    if (Date.now() >= deadline) throw new RefreshUnavailableError(); // another refresh is stuck; do not log out
    await sleep(50);
    const reloaded = await deps.store.load(sid);
    if (!reloaded) return null;
    current = reloaded;
  }
  return current;
}
