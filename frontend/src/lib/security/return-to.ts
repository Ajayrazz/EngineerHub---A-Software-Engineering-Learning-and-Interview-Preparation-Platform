/**
 * Validates the post-login `returnTo` target. Only same-site absolute *paths* are accepted, which
 * prevents open redirects (`https://evil`, `//evil`, `/\evil`, `javascript:`...). Anything else becomes "/".
 */
export function safeReturnTo(candidate: string | null | undefined): string {
  const fallback = "/";
  if (!candidate || candidate.length > 200) return fallback;
  // must be a rooted path, not protocol-relative ("//host") and not using backslashes
  if (!candidate.startsWith("/") || candidate.startsWith("//") || candidate.includes("\\"))
    return fallback;
  // no control characters / whitespace tricks
  if (/[\u0000-\u001f\u007f\s]/.test(candidate)) return fallback;
  try {
    const parsed = new URL(candidate, "http://placeholder.invalid");
    if (parsed.origin !== "http://placeholder.invalid") return fallback;
    // never bounce back into the auth endpoints (login loops)
    if (parsed.pathname.startsWith("/auth/")) return fallback;
    return parsed.pathname + parsed.search + parsed.hash;
  } catch {
    return fallback;
  }
}
