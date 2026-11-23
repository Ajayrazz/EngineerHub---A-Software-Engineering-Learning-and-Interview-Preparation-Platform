import { describe, expect, it } from "vitest";
import { MemoryKv } from "../testing/memory-kv";
import { checkRateLimit, clientAddress } from "./rate-limit";

describe("checkRateLimit", () => {
  it("allows up to the limit, then blocks with the remaining window", async () => {
    let now = 1000;
    const kv = new MemoryKv(() => now);
    for (let i = 0; i < 3; i++)
      expect((await checkRateLimit(kv, "login", "a", 3, 60)).allowed).toBe(true);
    now += 10;
    const blocked = await checkRateLimit(kv, "login", "a", 3, 60);
    expect(blocked).toEqual({ allowed: false, retryAfterSeconds: 50 });
  });

  it("is per subject and per bucket", async () => {
    const kv = new MemoryKv();
    await checkRateLimit(kv, "login", "a", 1, 60);
    expect((await checkRateLimit(kv, "login", "a", 1, 60)).allowed).toBe(false);
    expect((await checkRateLimit(kv, "login", "b", 1, 60)).allowed).toBe(true);
    expect((await checkRateLimit(kv, "callback", "a", 1, 60)).allowed).toBe(true);
  });

  it("starts a fresh window after expiry", async () => {
    let now = 1000;
    const kv = new MemoryKv(() => now);
    await checkRateLimit(kv, "x", "a", 1, 60);
    expect((await checkRateLimit(kv, "x", "a", 1, 60)).allowed).toBe(false);
    now += 61;
    expect((await checkRateLimit(kv, "x", "a", 1, 60)).allowed).toBe(true);
  });

  it("fails open and reports when the store breaks", async () => {
    const kv = new MemoryKv();
    kv.incr = async () => {
      throw new Error("redis down");
    };
    let reported = 0;
    const d = await checkRateLimit(kv, "x", "a", 1, 60, () => reported++);
    expect(d.allowed).toBe(true);
    expect(reported).toBe(1);
  });
});

describe("clientAddress", () => {
  it("ignores X-Forwarded-For unless the proxy is trusted", () => {
    expect(clientAddress("6.6.6.6", false)).toBe("shared");
  });
  it("uses the right-most entry (appended by the trusted proxy), not the spoofable left one", () => {
    expect(clientAddress("6.6.6.6, 203.0.113.9", true)).toBe("203.0.113.9");
  });
  it("rejects garbage values", () => {
    expect(clientAddress("<script>", true)).toBe("shared");
    expect(clientAddress(null, true)).toBe("shared");
  });
});
