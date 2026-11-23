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

/** Subject used when the real client address cannot be trusted. */
export const UNATTRIBUTED = "shared";

/**
 * Everyone without a trustworthy address lands in ONE bucket, so a per-client limit would throttle all
 * users together (self-inflicted outage). That bucket gets a much higher ceiling: it only guards against
 * floods, while precise per-client limits need TRUST_PROXY_HEADERS behind a known proxy.
 */
export const UNATTRIBUTED_MULTIPLIER = 30;
export function effectiveLimit(subject: string, perClientLimit: number): number {
  return subject === UNATTRIBUTED ? perClientLimit * UNATTRIBUTED_MULTIPLIER : perClientLimit;
}

/**
 * Client address for anonymous limiting. X-Forwarded-For is attacker-controlled unless a trusted proxy
 * overwrites it, so it is only honoured when TRUST_PROXY_HEADERS=true (set it once the hosting proxy is
 * known; Pending Decision H-*). The right-most entry is the one appended by the nearest proxy. Otherwise
 * everyone shares one (high-ceiling) bucket, a deliberately coarse but spoof-proof fallback.
 */
export function clientAddress(forwardedFor: string | null, trustProxy: boolean): string {
  if (!trustProxy || !forwardedFor) return UNATTRIBUTED;
  const last = forwardedFor.split(",").pop()?.trim() ?? "";
  return /^[0-9a-fA-F:.]{3,45}$/.test(last) ? last : UNATTRIBUTED;
}
