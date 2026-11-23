import { NextRequest, NextResponse } from "next/server";
import { loginCookie, sessionCookie } from "@/lib/auth/cookies";
import { limitByClient } from "@/lib/auth/limit";
import { logEvent, NO_STORE } from "@/lib/auth/http";
import { callApi } from "@/lib/auth/api";
import { completeAuthorization } from "@/lib/auth/oidc";
import { getEnv } from "@/lib/env";
import { randomToken } from "@/lib/session/crypto";
import { getSessionStore } from "@/lib/session/services";
import { safeEqual } from "@/lib/security/csrf";
import { safeReturnTo } from "@/lib/security/return-to";

export const dynamic = "force-dynamic";

function failure(code: "login_failed" | "access_denied") {
  const response = NextResponse.redirect(new URL(`/?auth_error=${code}`, getEnv().APP_URL), {
    status: 302,
    headers: NO_STORE,
  });
  response.cookies.delete(loginCookie().name);
  return response;
}

export async function GET(request: NextRequest) {
  const limited = await limitByClient(request, "callback", 30);
  if (limited) return limited;
  const params = request.nextUrl.searchParams;
  const state = params.get("state") ?? "";

  // Keycloak reported an error (e.g. user denied). Only a fixed code is shown; never reflect the input.
  if (params.has("error")) {
    logEvent("warn", "login_denied", { error: params.get("error")?.slice(0, 40) });
    return failure(params.get("error") === "access_denied" ? "access_denied" : "login_failed");
  }

  try {
    const bound = request.cookies.get(loginCookie().name)?.value;
    // Login CSRF / mix-up defence: the state in the URL must match the one bound to this browser.
    if (!state || !safeEqual(bound, state)) {
      logEvent("warn", "login_state_cookie_mismatch");
      return failure("login_failed");
    }
    const store = await getSessionStore();
    const txn = await store.takeLoginTransaction(state); // single use
    if (!txn) {
      logEvent("warn", "login_transaction_missing_or_replayed");
      return failure("login_failed");
    }

    const { tokens, claims } = await completeAuthorization(request.nextUrl.search, {
      state,
      nonce: txn.nonce,
      codeVerifier: txn.codeVerifier,
    });
    if (!claims || !tokens.refresh_token || !tokens.id_token)
      throw new Error("incomplete token response");

    const now = Math.floor(Date.now() / 1000);
    // A brand-new session id on every login (never reuse a client-supplied one): no session fixation.
    const sid = await store.create({
      sub: String(claims.sub),
      username: String(claims.preferred_username ?? claims.sub),
      name: String(claims.name ?? claims.preferred_username ?? ""),
      csrfToken: randomToken(),
      accessToken: tokens.access_token,
      accessExpiresAt: now + (tokens.expires_in ?? 60),
      refreshToken: tokens.refresh_token,
      idToken: tokens.id_token,
      createdAt: now,
      refreshedAt: 0,
    });

    // Provision the local user record (idempotent). Best effort: a failure here must not break the login,
    // the next authenticated call provisions it anyway.
    await callApi(tokens.access_token, "me", { signal: AbortSignal.timeout(3000) }).catch((error) =>
      logEvent("warn", "user_provisioning_deferred", {
        message: error instanceof Error ? error.message : "unknown",
      }),
    );

    const response = NextResponse.redirect(new URL(safeReturnTo(txn.returnTo), getEnv().APP_URL), {
      status: 302,
      headers: NO_STORE,
    });
    const session = sessionCookie();
    response.cookies.set(session.name, sid, session.options);
    response.cookies.delete(loginCookie().name);
    logEvent("info", "login_succeeded", { sub: claims.sub });
    return response;
  } catch (error) {
    logEvent("error", "login_callback_failed", {
      message: error instanceof Error ? error.message : "unknown",
    });
    return failure("login_failed");
  }
}
