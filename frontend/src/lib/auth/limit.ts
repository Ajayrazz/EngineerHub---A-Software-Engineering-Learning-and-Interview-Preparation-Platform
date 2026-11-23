import "server-only";
import type { NextRequest } from "next/server";
import { getEnv } from "../env";
import { checkRateLimit, clientAddress, effectiveLimit } from "../security/rate-limit";
import { getKeyValueStore } from "../session/services";
import { logEvent, problem } from "./http";

const WINDOW_SECONDS = 60;

/** Returns a 429 response when the caller exceeded `limit` requests/minute on `bucket`, else null. */
export async function limitByClient(request: NextRequest, bucket: string, limit: number) {
  const env = getEnv();
  const subject = clientAddress(request.headers.get("x-forwarded-for"), env.TRUST_PROXY_HEADERS);
  const kv = await getKeyValueStore().catch(() => null);
  if (!kv) return null; // fail open; the route's own Redis use will surface a real outage
  const d = await checkRateLimit(
    kv,
    bucket,
    subject,
    effectiveLimit(subject, limit),
    WINDOW_SECONDS,
    (e) =>
      logEvent("warn", "ratelimit_error", { message: e instanceof Error ? e.message : "unknown" }),
  );
  if (d.allowed) return null;
  logEvent("warn", "ratelimit_rejected", { bucket });
  return problem(
    429,
    "RATE_LIMITED",
    "Too many requests",
    `Too many requests. Retry in ${d.retryAfterSeconds} seconds.`,
    { "Retry-After": String(d.retryAfterSeconds) },
  );
}
