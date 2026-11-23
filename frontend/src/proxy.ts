import { NextRequest, NextResponse } from "next/server";
import { buildCsp, isCheckoutPath } from "@/lib/security/csp";

/**
 * Per-request Content-Security-Policy with a nonce (Next.js applies the nonce to its own scripts/styles).
 * `strict-dynamic` lets nonce'd scripts load their chunks while blocking any injected inline script.
 * `form-action` includes the identity provider because logout is a form POST that is redirected there.
 */
export function proxy(request: NextRequest) {
  const nonce = Buffer.from(crypto.randomUUID()).toString("base64");
  const isDev = process.env.NODE_ENV === "development";
  const issuerOrigin = safeOrigin(process.env.KEYCLOAK_ISSUER_URI);
  const secure = process.env.APP_URL?.startsWith("https:") ?? false;

  const csp = buildCsp({
    nonce,
    isDev,
    issuerOrigin,
    secure,
    checkout: isCheckoutPath(request.nextUrl.pathname),
  });

  const requestHeaders = new Headers(request.headers);
  requestHeaders.set("x-nonce", nonce);
  requestHeaders.set("Content-Security-Policy", csp);

  const response = NextResponse.next({ request: { headers: requestHeaders } });
  response.headers.set("Content-Security-Policy", csp);
  return response;
}

function safeOrigin(value: string | undefined): string | null {
  try {
    return value ? new URL(value).origin : null;
  } catch {
    return null;
  }
}

export const config = {
  // Pages only: static assets and the BFF JSON endpoints do not need a document CSP.
  matcher: [{ source: "/((?!api|_next/static|_next/image|favicon.ico).*)" }],
};
