import { describe, expect, it } from "vitest";
import {
  decrypt,
  deriveKeys,
  encrypt,
  isValidSessionId,
  keyFor,
  newSessionId,
  randomToken,
} from "./crypto";

const SECRET = "x".repeat(40);

describe("session crypto", () => {
  const { encKey, macKey } = deriveKeys(SECRET);

  it("derives distinct 32-byte keys and refuses short secrets", () => {
    expect(encKey).toHaveLength(32);
    expect(macKey).toHaveLength(32);
    expect(encKey.equals(macKey)).toBe(false);
    expect(() => deriveKeys("short")).toThrow(/at least 32/);
  });

  it("different secrets give different keys", () => {
    expect(deriveKeys("y".repeat(40)).encKey.equals(encKey)).toBe(false);
  });

  it("round-trips and never stores plaintext", () => {
    const token = encrypt(encKey, '{"accessToken":"eyJhbGciOi"}', "k1");
    expect(token).not.toContain("eyJhbGciOi");
    expect(decrypt(encKey, token, "k1")).toBe('{"accessToken":"eyJhbGciOi"}');
  });

  it("uses a fresh IV each time", () => {
    expect(encrypt(encKey, "same", "k")).not.toBe(encrypt(encKey, "same", "k"));
  });

  it("rejects tampered ciphertext, tag and iv", () => {
    const [p, iv, tag, ct] = encrypt(encKey, "secret", "k").split(".");
    const flip = (s: string) => (s[0] === "A" ? "B" : "A") + s.slice(1);
    expect(() => decrypt(encKey, [p, iv, tag, flip(ct)].join("."), "k")).toThrow();
    expect(() => decrypt(encKey, [p, iv, flip(tag), ct].join("."), "k")).toThrow();
    expect(() => decrypt(encKey, [p, flip(iv), tag, ct].join("."), "k")).toThrow();
  });

  it("binds ciphertext to its key (values cannot be swapped between sessions)", () => {
    const token = encrypt(encKey, "secret", "bff:session:AAA");
    expect(() => decrypt(encKey, token, "bff:session:BBB")).toThrow();
  });

  it("rejects the wrong key and unknown formats", () => {
    const token = encrypt(encKey, "secret", "k");
    expect(() => decrypt(deriveKeys("z".repeat(40)).encKey, token, "k")).toThrow();
    expect(() => decrypt(encKey, "v2.a.b.c", "k")).toThrow(/unsupported/);
    expect(() => decrypt(encKey, "garbage", "k")).toThrow();
  });

  it("session ids are 256-bit, url-safe and unique", () => {
    const ids = new Set(Array.from({ length: 1000 }, newSessionId));
    expect(ids.size).toBe(1000);
    for (const id of ids) expect(isValidSessionId(id)).toBe(true);
  });

  it("validates session-id format strictly", () => {
    for (const bad of [
      "",
      "short",
      "a".repeat(42),
      "a".repeat(44),
      "a".repeat(42) + "=",
      "a".repeat(42) + "/",
      undefined,
      5,
      null,
    ]) {
      expect(isValidSessionId(bad as unknown)).toBe(false);
    }
  });

  it("redis keys are namespaced HMACs that do not reveal the id", () => {
    const sid = newSessionId();
    const k = keyFor(macKey, "session", sid);
    expect(k).toMatch(/^bff:session:[0-9a-f]{64}$/);
    expect(k).not.toContain(sid);
    expect(keyFor(macKey, "session", sid)).toBe(k);
    expect(keyFor(macKey, "login", sid)).not.toBe(k);
    expect(keyFor(deriveKeys("q".repeat(40)).macKey, "session", sid)).not.toBe(k);
  });

  it("random tokens are url-safe", () => {
    expect(randomToken()).toMatch(/^[A-Za-z0-9_-]{43}$/);
  });
});
