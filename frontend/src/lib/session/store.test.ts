import { beforeEach, describe, expect, it } from "vitest";
import { MemoryKv } from "../testing/memory-kv";
import { deriveKeys, newSessionId } from "./crypto";
import { SessionStore } from "./store";
import type { Session } from "./types";

const keys = deriveKeys("k".repeat(40));
let clock = 1_800_000_000;
const now = () => clock;

const sample = (over: Partial<Session> = {}): Session => ({
  sub: "u-1",
  username: "learner",
  name: "Dev Learner",
  csrfToken: "csrf",
  accessToken: "AT.secret.value",
  accessExpiresAt: clock + 300,
  refreshToken: "RT-secret",
  idToken: "IDT",
  createdAt: clock,
  refreshedAt: 0,
  ...over,
});

let kv: MemoryKv;
let store: SessionStore;
beforeEach(() => {
  clock = 1_800_000_000;
  kv = new MemoryKv(now);
  store = new SessionStore(kv, keys, { idleTtlSeconds: 1000, absoluteTtlSeconds: 5000, now });
});

describe("SessionStore", () => {
  it("creates under a fresh random id and loads it back", async () => {
    const sid = await store.create(sample());
    expect(sid).toMatch(/^[A-Za-z0-9_-]{43}$/);
    expect(await store.load(sid)).toMatchObject({ sub: "u-1", username: "learner" });
  });

  it("every login gets a new session id (no session fixation)", async () => {
    const a = await store.create(sample());
    const b = await store.create(sample());
    expect(a).not.toBe(b);
  });

  it("never stores tokens or the raw session id in Redis", async () => {
    const sid = await store.create(sample());
    const dump = JSON.stringify([...kv.data.entries()]);
    expect(dump).not.toContain("AT.secret.value");
    expect(dump).not.toContain("RT-secret");
    expect(dump).not.toContain(sid);
  });

  it("returns null for unknown, malformed and missing ids", async () => {
    expect(await store.load(newSessionId())).toBeNull();
    expect(await store.load("not-a-valid-id")).toBeNull();
    expect(await store.load(undefined)).toBeNull();
  });

  it("slides the idle timeout on every load", async () => {
    const sid = await store.create(sample());
    clock += 900;
    expect(await store.load(sid)).not.toBeNull(); // would have expired at +1000 without sliding
    clock += 900;
    expect(await store.load(sid)).not.toBeNull();
  });

  it("expires after the idle timeout", async () => {
    const sid = await store.create(sample());
    clock += 1001;
    expect(await store.load(sid)).toBeNull();
  });

  it("enforces the absolute lifetime even for an active session", async () => {
    const sid = await store.create(sample());
    for (let i = 0; i < 5; i++) {
      clock += 900;
      if (i < 4) expect(await store.load(sid)).not.toBeNull();
    }
    clock += 900; // > 5000s since creation
    expect(await store.load(sid)).toBeNull();
  });

  it("caps the Redis TTL to the remaining absolute lifetime", async () => {
    const sid = await store.create(sample());
    for (let i = 0; i < 5; i++) {
      clock += 900; // stay active so the idle timeout never fires
      expect(await store.load(sid)).not.toBeNull();
    }
    // 4500s elapsed: only 500s of absolute life remain, although the idle window alone would allow 1000
    const key = [...kv.data.keys()][0];
    expect(kv.ttl(key)).toBe(500);
  });

  it("destroy removes the session (server-side logout)", async () => {
    const sid = await store.create(sample());
    await store.destroy(sid);
    expect(await store.load(sid)).toBeNull();
  });

  it("treats a tampered entry as no session and deletes it", async () => {
    const sid = await store.create(sample());
    const key = [...kv.data.keys()][0];
    const entry = kv.data.get(key)!;
    entry.value = entry.value.slice(0, -2) + (entry.value.endsWith("AA") ? "BB" : "AA");
    expect(await store.load(sid)).toBeNull();
    expect(kv.data.has(key)).toBe(false);
  });

  it("rejects a value copied from another session's key (AAD binding)", async () => {
    const victim = await store.create(sample({ sub: "victim" }));
    const attacker = await store.create(sample({ sub: "attacker" }));
    const [kVictim, kAttacker] = [...kv.data.keys()];
    // attacker with Redis write access moves the victim's ciphertext under their own key
    kv.data.get(kAttacker)!.value = kv.data.get(kVictim)!.value;
    expect(await store.load(attacker)).toBeNull();
    expect(await store.load(victim)).not.toBeNull();
  });

  it("save() keeps the id and absolute deadline; destroys when past it", async () => {
    const sid = await store.create(sample());
    clock += 100;
    await store.save(sid, { ...sample(), accessToken: "NEW", createdAt: 1_800_000_000 });
    expect((await store.load(sid))?.accessToken).toBe("NEW");
    clock += 5000;
    await store.save(sid, sample({ createdAt: 1_800_000_000 }));
    expect(await store.load(sid)).toBeNull();
  });
});

describe("login transactions", () => {
  const txn = { codeVerifier: "v", nonce: "n", returnTo: "/account", createdAt: 1 };
  const state = "s".repeat(43);

  it("are single use (replay protection)", async () => {
    await store.putLoginTransaction(state, txn);
    expect(await store.takeLoginTransaction(state)).toEqual(txn);
    expect(await store.takeLoginTransaction(state)).toBeNull();
  });

  it("expire after 5 minutes", async () => {
    await store.putLoginTransaction(state, txn);
    clock += 301;
    expect(await store.takeLoginTransaction(state)).toBeNull();
  });

  it("reject malformed state values without touching Redis", async () => {
    expect(await store.takeLoginTransaction("short")).toBeNull();
    expect(await store.takeLoginTransaction("a b".repeat(10))).toBeNull();
  });

  it("are not readable as sessions (separate namespaces)", async () => {
    await store.putLoginTransaction(state, txn);
    expect([...kv.data.keys()].every((k) => k.startsWith("bff:login:"))).toBe(true);
  });
});

describe("refresh lock", () => {
  it("is exclusive and only the owner can release it", async () => {
    const sid = newSessionId();
    expect(await store.acquireRefreshLock(sid, "a")).toBe(true);
    expect(await store.acquireRefreshLock(sid, "b")).toBe(false);
    expect(await store.releaseRefreshLock(sid, "b")).toBe(false); // not the owner
    expect(await store.acquireRefreshLock(sid, "b")).toBe(false);
    expect(await store.releaseRefreshLock(sid, "a")).toBe(true);
    expect(await store.acquireRefreshLock(sid, "b")).toBe(true);
  });

  it("expires by itself so a crashed refresher cannot block a session forever", async () => {
    const sid = newSessionId();
    await store.acquireRefreshLock(sid, "a", 10);
    clock += 11;
    expect(await store.acquireRefreshLock(sid, "b")).toBe(true);
  });
});
