import { beforeEach, describe, expect, it } from "vitest";
import { MemoryKv } from "../testing/memory-kv";
import { deriveKeys } from "../session/crypto";
import { SessionStore } from "../session/store";
import type { Session } from "../session/types";
import {
  ensureFreshSession,
  RefreshRejectedError,
  RefreshUnavailableError,
  type RefreshDeps,
  type TokenSet,
} from "./refresh";

let clock = 1_800_000_000;
const now = () => clock;
const keys = deriveKeys("r".repeat(40));

let kv: MemoryKv;
let store: SessionStore;
let sid: string;

const session = (over: Partial<Session> = {}): Session => ({
  sub: "u",
  username: "u",
  name: "U",
  csrfToken: "c",
  accessToken: "AT-1",
  accessExpiresAt: clock + 300,
  refreshToken: "RT-1",
  idToken: "ID",
  createdAt: clock,
  refreshedAt: 0,
  ...over,
});

beforeEach(async () => {
  clock = 1_800_000_000;
  kv = new MemoryKv(now);
  store = new SessionStore(kv, keys, { idleTtlSeconds: 10_000, absoluteTtlSeconds: 100_000, now });
});

/** A fake IdP with Keycloak's rotation rule: each refresh token works exactly once. */
function fakeIdp() {
  let n = 1;
  const used = new Set<string>();
  const calls: string[] = [];
  const refresh = async (rt: string): Promise<TokenSet> => {
    calls.push(rt);
    await new Promise((r) => setTimeout(r, 20)); // network latency => real concurrency
    if (used.has(rt) || !rt.startsWith("RT-")) throw new RefreshRejectedError("invalid_grant");
    used.add(rt);
    n += 1;
    return { access_token: `AT-${n}`, refresh_token: `RT-${n}`, expires_in: 300 };
  };
  return { refresh, calls };
}

const deps = (idp: ReturnType<typeof fakeIdp>, extra: Partial<RefreshDeps> = {}): RefreshDeps => ({
  store,
  refresh: idp.refresh,
  now,
  sleep: (ms) => new Promise((r) => setTimeout(r, Math.min(ms, 5))),
  ...extra,
});

describe("ensureFreshSession", () => {
  it("returns the session untouched while the access token is fresh", async () => {
    sid = await store.create(session());
    const idp = fakeIdp();
    const result = await ensureFreshSession(sid, (await store.load(sid))!, deps(idp));
    expect(result?.accessToken).toBe("AT-1");
    expect(idp.calls).toHaveLength(0);
  });

  it("refreshes an expiring token, rotates the refresh token and persists it", async () => {
    sid = await store.create(session({ accessExpiresAt: clock + 10 })); // inside the 30s skew
    const idp = fakeIdp();
    const result = await ensureFreshSession(sid, (await store.load(sid))!, deps(idp));
    expect(result).toMatchObject({ accessToken: "AT-2", refreshToken: "RT-2", refreshedAt: clock });
    expect(result!.accessExpiresAt).toBe(clock + 300);
    expect((await store.load(sid))?.refreshToken).toBe("RT-2");
  });

  it("concurrent requests trigger exactly ONE refresh (no refresh-token reuse => no forced logout)", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    const idp = fakeIdp();
    const stale = (await store.load(sid))!;
    const results = await Promise.all(
      Array.from({ length: 12 }, () => ensureFreshSession(sid, stale, deps(idp))),
    );
    expect(idp.calls).toEqual(["RT-1"]);
    for (const r of results) expect(r?.accessToken).toBe("AT-2");
    expect(await store.load(sid)).not.toBeNull(); // session survived
  });

  it("destroys the session when the IdP rejects the refresh token", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5, refreshToken: "revoked" }));
    const idp = fakeIdp();
    const result = await ensureFreshSession(sid, (await store.load(sid))!, deps(idp));
    expect(result).toBeNull();
    expect(await store.load(sid)).toBeNull();
  });

  it("returns null if the session vanished (e.g. logout in another tab) while waiting", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    const stale = (await store.load(sid))!;
    await store.destroy(sid);
    const idp = fakeIdp();
    expect(await ensureFreshSession(sid, stale, deps(idp))).toBeNull();
    expect(idp.calls).toHaveLength(0);
  });

  it("gives up (unavailable, session kept) instead of hanging if another holder never finishes", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    await store.acquireRefreshLock(sid, "stuck-owner", 60);
    const idp = fakeIdp();
    await expect(
      ensureFreshSession(sid, (await store.load(sid))!, deps(idp, { waitMs: 30 })),
    ).rejects.toBeInstanceOf(RefreshUnavailableError);
    expect(idp.calls).toHaveLength(0);
    expect(await store.load(sid)).not.toBeNull(); // the user stays logged in
  });

  it("releases the lock even when the refresh fails", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5, refreshToken: "bad" }));
    await ensureFreshSession(sid, (await store.load(sid))!, deps(fakeIdp()));
    expect(await store.acquireRefreshLock(sid, "next", 10)).toBe(true);
  });

  it("an identity-provider OUTAGE keeps the session (503 later), it never logs the user out", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    for (const failure of [
      new TypeError("fetch failed"),
      new Error("ECONNREFUSED"),
      new Error("HTTP 503"),
    ]) {
      await expect(
        ensureFreshSession(sid, (await store.load(sid))!, {
          store,
          now,
          refresh: async () => {
            throw failure;
          },
        }),
      ).rejects.toBeInstanceOf(RefreshUnavailableError);
      expect(await store.load(sid)).not.toBeNull(); // session intact
      expect(await store.acquireRefreshLock(sid, "x", 1)).toBe(true); // lock released
      await store.releaseRefreshLock(sid, "x");
    }
  });

  it("recovers by itself once the identity provider is back", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    let up = false;
    const refresh = async (): Promise<TokenSet> => {
      if (!up) throw new TypeError("fetch failed");
      return { access_token: "AT-OK", refresh_token: "RT-OK", expires_in: 300 };
    };
    await expect(
      ensureFreshSession(sid, (await store.load(sid))!, { store, now, refresh }),
    ).rejects.toBeInstanceOf(RefreshUnavailableError);
    up = true;
    const result = await ensureFreshSession(sid, (await store.load(sid))!, { store, now, refresh });
    expect(result?.accessToken).toBe("AT-OK");
  });

  it("keeps the old refresh token if the IdP does not rotate it", async () => {
    sid = await store.create(session({ accessExpiresAt: clock - 5 }));
    const result = await ensureFreshSession(sid, (await store.load(sid))!, {
      store,
      now,
      refresh: async () => ({ access_token: "AT-X", expires_in: 100 }),
    });
    expect(result).toMatchObject({ accessToken: "AT-X", refreshToken: "RT-1" });
  });
});
