import type { KeyValueStore } from "../session/store";

export type RateDecision = { allowed: boolean; retryAfterSeconds: number };

/**
 * Fixed-window limiter on the shared Redis. Fails OPEN: if Redis is unavailable the request is allowed
 * (login itself needs Redis anyway; the limiter must never be the reason the site is down).
 */
export async function checkRateLimit(
  kv: KeyValueStore,
  bucket: string,
  subject: string,
  limit: number,
  windowSeconds: number,
  onError: (e: unknown) => void = () => {},
): Promise<RateDecision> {
  try {
    const { count, ttl } = await kv.incr(`rl:${bucket}:${subject}`, windowSeconds);
    return count <= limit
      ? { allowed: true, retryAfterSeconds: 0 }
      : { allowed: false, retryAfterSeconds: ttl };
  } catch (e) {
    onError(e);
    return { allowed: true, retryAfterSeconds: 0 };
  }
}

/**
 * Client address for anonymous limiting. X-Forwarded-For is attacker-controlled unless a trusted proxy
 * overwrites it, so it is only honoured when TRUST_PROXY_HEADERS=true (set it once the hosting proxy is
 * known; Pending Decision H-*). The right-most entry is the one appended by the nearest proxy. Otherwise
 * everyone shares one bucket, a deliberately coarse but spoof-proof fallback.
 */
export function clientAddress(forwardedFor: string | null, trustProxy: boolean): string {
  if (!trustProxy || !forwardedFor) return "shared";
  const last = forwardedFor.split(",").pop()?.trim() ?? "";
  return /^[0-9a-fA-F:.]{3,45}$/.test(last) ? last : "shared";
}
