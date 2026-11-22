import { NextRequest, NextResponse } from "next/server";
import { apiUrl } from "@/lib/auth/api";
import { sessionCookie } from "@/lib/auth/cookies";
import { RefreshUnavailableError } from "@/lib/auth/refresh";
import { loadSession, withFreshToken, type Authenticated } from "@/lib/auth/current";
import { logEvent, NO_STORE, problem } from "@/lib/auth/http";
import { getEnv } from "@/lib/env";
import { randomToken } from "@/lib/session/crypto";
import { getSessionStore } from "@/lib/session/services";
import { CSRF_HEADER, isCsrfSafe, isUnsafeMethod } from "@/lib/security/csrf";
import { toApiPath } from "@/lib/security/proxy-path";

export const dynamic = "force-dynamic";

const MAX_BODY_BYTES = 1024 * 1024; // 1 MiB; code submissions are far smaller (API caps at 64 KiB)
const UPSTREAM_TIMEOUT_MS = 15_000;
const CORRELATION = /^[A-Za-z0-9._-]{8,64}$/;

// Only these request headers are forwarded. Cookies, Host, X-Forwarded-*, Authorization etc. are NOT:
// the BFF sets Authorization itself from the server-side session.
const FORWARD_REQUEST_HEADERS = ["accept", "content-type", "idempotency-key"] as const;
// Only these response headers are returned. Set-Cookie and WWW-Authenticate never cross the boundary.
const FORWARD_RESPONSE_HEADERS = [
  "content-type",
  "retry-after",
  "x-correlation-id",
  "content-language",
] as const;

type Ctx = RouteContext<"/api/proxy/[...path]">;

/** Rejects bodies over the limit while streaming (works for chunked uploads without Content-Length). */
function limitBody(body: ReadableStream<Uint8Array>, limit: number): ReadableStream<Uint8Array> {
  let seen = 0;
  return body.pipeThrough(
    new TransformStream<Uint8Array, Uint8Array>({
      transform(chunk, controller) {
        seen += chunk.byteLength;
        if (seen > limit) controller.error(new Error("payload too large"));
        else controller.enqueue(chunk);
      },
    }),
  );
}

async function forward(
  request: NextRequest,
  accessToken: string,
  path: string,
  correlationId: string,
) {
  const headers = new Headers();
  for (const name of FORWARD_REQUEST_HEADERS) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  headers.set("authorization", `Bearer ${accessToken}`);
  headers.set("x-correlation-id", correlationId);

  const hasBody = !["GET", "HEAD"].includes(request.method) && request.body !== null;
  const init: RequestInit & { duplex?: "half" } = {
    method: request.method,
    headers,
    redirect: "manual", // never follow upstream redirects (SSRF/open-redirect surface)
    cache: "no-store",
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  };
  if (hasBody) {
    init.body = limitBody(request.body!, MAX_BODY_BYTES);
    init.duplex = "half";
  }
  return fetch(apiUrl(path, request.nextUrl.search), init);
}

/** Identity provider temporarily unreachable: the session is kept; the client should retry. */
function unavailable() {
  return problem(
    503,
    "SERVICE_UNAVAILABLE",
    "Service temporarily unavailable",
    "Authentication is temporarily unavailable. Please retry shortly.",
    { "Retry-After": "5" },
  );
}

async function handle(request: NextRequest, ctx: Ctx) {
  const env = getEnv();
  const path = toApiPath((await ctx.params).path);
  if (!path) return problem(400, "MALFORMED_REQUEST", "Malformed request", "Invalid API path.");

  const cookie = sessionCookie();
  const auth = await loadSession(request.cookies.get(cookie.name)?.value);
  if (!auth) return problem(401, "UNAUTHENTICATED", "Authentication required", "Not logged in.");

  // CSRF: layered on SameSite=Lax. State-changing calls need same Origin + per-session token.
  if (isUnsafeMethod(request.method)) {
    const ok = isCsrfSafe({
      origin: request.headers.get("origin"),
      appOrigin: env.appOrigin,
      expectedToken: auth.session.csrfToken,
      providedToken: request.headers.get(CSRF_HEADER),
    });
    if (!ok) {
      logEvent("warn", "proxy_csrf_rejected", { method: request.method });
      return problem(
        403,
        "CSRF_REJECTED",
        "Request rejected",
        "Missing or invalid CSRF protection.",
      );
    }
  }

  const declaredLength = Number(request.headers.get("content-length") ?? 0);
  if (declaredLength > MAX_BODY_BYTES) {
    return problem(
      413,
      "PAYLOAD_TOO_LARGE",
      "Payload too large",
      "Request body exceeds the allowed size.",
    );
  }

  const inbound = request.headers.get("x-correlation-id");
  const correlationId = inbound && CORRELATION.test(inbound) ? inbound : randomToken(16);

  let current: Authenticated | null;
  try {
    current = await withFreshToken(auth);
  } catch (error) {
    if (error instanceof RefreshUnavailableError) return unavailable();
    throw error;
  }
  if (!current)
    return problem(
      401,
      "UNAUTHENTICATED",
      "Authentication required",
      "Session expired. Please log in again.",
    );

  let upstream: Response;
  try {
    upstream = await forward(request, current.session.accessToken, path, correlationId);
    // The API rejected a token we believed valid (clock skew / revoked). For safe methods, force one
    // refresh and retry once; bodies were streamed and cannot be replayed.
    if (upstream.status === 401 && ["GET", "HEAD"].includes(request.method)) {
      const store = await getSessionStore();
      await store.save(current.sid, { ...current.session, accessExpiresAt: 0 });
      const retried = await withFreshToken({
        sid: current.sid,
        session: { ...current.session, accessExpiresAt: 0 },
      });
      if (!retried)
        return problem(
          401,
          "UNAUTHENTICATED",
          "Authentication required",
          "Session expired. Please log in again.",
        );
      current = retried;
      upstream = await forward(request, current.session.accessToken, path, correlationId);
    }
  } catch (error) {
    const timedOut = error instanceof DOMException && error.name === "TimeoutError";
    logEvent("error", "proxy_upstream_failed", {
      path,
      timedOut,
      message: error instanceof Error ? error.message : "unknown",
    });
    return problem(
      timedOut ? 504 : 502,
      "SERVICE_UNAVAILABLE",
      "Service temporarily unavailable",
      "The API could not be reached.",
      { "Retry-After": "5" },
    );
  }

  const responseHeaders = new Headers(NO_STORE);
  for (const name of FORWARD_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) responseHeaders.set(name, value);
  }
  // Pass 3xx through as an error rather than exposing upstream Location headers.
  if (upstream.status >= 300 && upstream.status < 400) {
    return problem(502, "SERVICE_UNAVAILABLE", "Bad gateway", "Unexpected upstream response.");
  }
  return new NextResponse(upstream.body, { status: upstream.status, headers: responseHeaders });
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
