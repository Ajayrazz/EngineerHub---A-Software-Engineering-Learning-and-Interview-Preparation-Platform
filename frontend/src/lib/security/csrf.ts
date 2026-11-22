import { timingSafeEqual } from "node:crypto";

export const CSRF_HEADER = "x-csrf-token";

/** Constant-time string comparison (length is not secret here, content is). */
export function safeEqual(a: string | null | undefined, b: string | null | undefined): boolean {
  if (typeof a !== "string" || typeof b !== "string") return false;
  const ab = Buffer.from(a);
  const bb = Buffer.from(b);
  return ab.length === bb.length && timingSafeEqual(ab, bb);
}

/**
 * CSRF defence for state-changing BFF requests (layered on top of SameSite=Lax cookies):
 *  1. the `Origin` header must be exactly our own origin (a missing Origin is rejected), and
 *  2. the per-session CSRF token must be echoed back (`X-CSRF-Token` header or `csrf` form field).
 */
export function isCsrfSafe(opts: {
  origin: string | null;
  appOrigin: string;
  expectedToken: string;
  providedToken: string | null | undefined;
}): boolean {
  if (opts.origin !== opts.appOrigin) return false;
  return safeEqual(opts.expectedToken, opts.providedToken);
}

export function isUnsafeMethod(method: string): boolean {
  return !["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase());
}
