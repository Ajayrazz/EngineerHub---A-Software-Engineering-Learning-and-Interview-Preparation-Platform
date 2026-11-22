import { NextRequest, NextResponse } from "next/server";
import { sessionCookie } from "@/lib/auth/cookies";
import { loadSession } from "@/lib/auth/current";
import { logEvent, NO_STORE } from "@/lib/auth/http";
import { endSessionUrl } from "@/lib/auth/oidc";
import { getEnv } from "@/lib/env";
import { getSessionStore } from "@/lib/session/services";
import { isCsrfSafe } from "@/lib/security/csrf";

export const dynamic = "force-dynamic";

/**
 * RP-initiated logout. POST-only and CSRF-protected (a GET logout link could be triggered by any
 * site). Destroys the server-side session first, so a stolen/replayed cookie is dead immediately,
 * then ends the Keycloak SSO session.
 */
export async function POST(request: NextRequest) {
  const env = getEnv();
  const cookie = sessionCookie();
  const sid = request.cookies.get(cookie.name)?.value;
  const auth = await loadSession(sid);
  const home = new URL("/", env.APP_URL);

  if (!auth) {
    const response = NextResponse.redirect(home, { status: 303, headers: NO_STORE });
    response.cookies.delete(cookie.name);
    return response;
  }

  const form = await request.formData().catch(() => null);
  const provided = typeof form?.get("csrf") === "string" ? (form.get("csrf") as string) : null;
  if (
    !isCsrfSafe({
      origin: request.headers.get("origin"),
      appOrigin: env.appOrigin,
      expectedToken: auth.session.csrfToken,
      providedToken: provided,
    })
  ) {
    logEvent("warn", "logout_csrf_rejected");
    return NextResponse.json({ code: "CSRF_REJECTED" }, { status: 403, headers: NO_STORE });
  }

  await (await getSessionStore()).destroy(auth.sid);
  let target = home;
  try {
    target = await endSessionUrl(auth.session.idToken);
  } catch (error) {
    logEvent("warn", "end_session_url_failed", {
      message: error instanceof Error ? error.message : "unknown",
    });
  }
  const response = NextResponse.redirect(target, { status: 303, headers: NO_STORE });
  response.cookies.delete(cookie.name);
  logEvent("info", "logout", { sub: auth.session.sub });
  return response;
}
