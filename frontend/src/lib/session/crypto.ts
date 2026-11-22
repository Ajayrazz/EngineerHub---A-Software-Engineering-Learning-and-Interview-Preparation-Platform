/**
 * Cryptographic helpers for the BFF session store. Pure Node `crypto`, no framework imports, so they
 * are unit-testable and reusable by E2E tests.
 *
 * Threat model: the browser only ever holds an opaque random session id. Tokens live in Redis,
 * encrypted with AES-256-GCM (so a Redis dump/backup/replica leak does not reveal usable tokens), and
 * the Redis key is an HMAC of the session id (so a leaked key list cannot be turned into valid cookies).
 */
import { createCipheriv, createDecipheriv, createHmac, hkdfSync, randomBytes } from "node:crypto";

export type SessionKeys = { encKey: Buffer; macKey: Buffer };

const ENC_PREFIX = "v1";

/** Derives independent encryption and MAC keys from the master secret (HKDF-SHA256, domain-separated). */
export function deriveKeys(secret: string): SessionKeys {
  if (secret.length < 32) {
    throw new Error("SESSION_SECRET must be at least 32 characters");
  }
  const derive = (info: string) =>
    Buffer.from(hkdfSync("sha256", secret, "engineerhub-bff-v1", info, 32));
  return { encKey: derive("session-encryption"), macKey: derive("session-key-mac") };
}

/** 256-bit random session id, base64url (43 chars). */
export function newSessionId(): string {
  return randomBytes(32).toString("base64url");
}

/** Cheap format check so malformed cookie values never reach Redis. */
export function isValidSessionId(value: unknown): value is string {
  return typeof value === "string" && /^[A-Za-z0-9_-]{43}$/.test(value);
}

/** Random opaque token (CSRF tokens, login state, lock owners). */
export function randomToken(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

/** Redis key for a value identified by a secret id: HMAC so ids are never stored. */
export function keyFor(macKey: Buffer, namespace: string, id: string): string {
  return `bff:${namespace}:${createHmac("sha256", macKey).update(id).digest("hex")}`;
}

/** AES-256-GCM. `aad` binds the ciphertext to its Redis key so values can't be swapped between keys. */
export function encrypt(encKey: Buffer, plaintext: string, aad: string): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", encKey, iv);
  cipher.setAAD(Buffer.from(aad));
  const ct = Buffer.concat([cipher.update(plaintext, "utf8"), cipher.final()]);
  return [
    ENC_PREFIX,
    iv.toString("base64url"),
    cipher.getAuthTag().toString("base64url"),
    ct.toString("base64url"),
  ].join(".");
}

/** Throws if the value is malformed, was tampered with, or belongs to a different key. */
export function decrypt(encKey: Buffer, token: string, aad: string): string {
  const [prefix, iv, tag, ct] = token.split(".");
  if (prefix !== ENC_PREFIX || !iv || !tag || !ct) {
    throw new Error("unsupported ciphertext format");
  }
  const decipher = createDecipheriv("aes-256-gcm", encKey, Buffer.from(iv, "base64url"));
  decipher.setAAD(Buffer.from(aad));
  decipher.setAuthTag(Buffer.from(tag, "base64url"));
  return Buffer.concat([decipher.update(Buffer.from(ct, "base64url")), decipher.final()]).toString(
    "utf8",
  );
}
