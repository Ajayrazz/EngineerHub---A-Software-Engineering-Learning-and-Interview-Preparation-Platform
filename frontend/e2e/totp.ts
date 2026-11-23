import { createHmac } from "node:crypto";

/** Fake seed of the dev realm's staff users (infra/keycloak/dev/overlay.json). Never a real secret. */
export const DEV_TOTP_SEED = "dev-only-totp-seed-01";

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 s). Keycloak uses the stored secret string's bytes as the key. */
export function totp(seed = DEV_TOTP_SEED, nowMs = Date.now(), stepOffset = 0): string {
  const counter = Math.floor(nowMs / 1000 / 30) + stepOffset;
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));
  const h = createHmac("sha1", Buffer.from(seed, "utf8")).update(msg).digest();
  const o = h[h.length - 1] & 0x0f;
  const bin = ((h[o] & 0x7f) << 24) | (h[o + 1] << 16) | (h[o + 2] << 8) | h[o + 3];
  return String(bin % 1_000_000).padStart(6, "0");
}
