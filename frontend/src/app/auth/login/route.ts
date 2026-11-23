import { NextRequest, NextResponse } from "next/server";
import { loginCookie } from "@/lib/auth/cookies";
import { limitByClient } from "@/lib/auth/limit";
import { logEvent, NO_STORE } from "@/lib/auth/http";
import { startAuthorization } from "@/lib/auth/oidc";
import { getEnv } from "@/lib/env";
import { getSessionStore } from "@/lib/session/services";
import { safeReturnTo } from "@/lib/security/return-to";

export const dynamic = "force-dynamic";

/** Starts Authorization Code + PKCE: stores verifier/nonce server-side and redirects to Keycloak. */
export async function GET(request: NextRequest) {
  const limited = await limitByClient(request, "login", 20);
  if (limited) return limited;
  try {
    const returnTo = safeReturnTo(request.nextUrl.searchParams.get("returnTo"));
    const { url, state, transaction } = await startAuthorization(returnTo);
    await (await getSessionStore()).putLoginTransaction(state, transaction);

    const response = NextResponse.redirect(url, { status: 302, headers: NO_STORE });
    const cookie = loginCookie();
    // The cookie carries the state so only this browser can complete this login (login-CSRF defence).
    response.cookies.set(cookie.name, state, cookie.options);
    return response;
  } catch (error) {
    logEvent("error", "login_start_failed", {
      message: error instanceof Error ? error.message : "unknown",
    });
    return NextResponse.redirect(new URL("/?auth_error=login_failed", getEnv().APP_URL), {
      status: 302,
      headers: NO_STORE,
    });
  }
}
