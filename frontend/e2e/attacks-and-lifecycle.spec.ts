import { expect, test } from "@playwright/test";
import {
  APP_ORIGIN,
  csrfToken,
  LOGIN_COOKIE,
  login,
  openStore,
  SESSION_COOKIE,
  sessionCookieValue,
} from "./helpers";

const APP = new URL(APP_ORIGIN);
const cookieFor = (name: string, value: string) => ({
  name,
  value,
  domain: APP.hostname,
  path: "/",
});

test.describe("attacks on the login flow", () => {
  test("open redirect: a hostile returnTo is ignored and the user stays on our origin", async ({
    page,
  }) => {
    for (const evil of [
      "https://evil.example/phish",
      "//evil.example",
      "javascript:alert(1)",
      "/\\evil.example",
    ]) {
      await login(page, "learner", evil);
      expect(new URL(page.url()).origin).toBe(APP_ORIGIN);
      expect(new URL(page.url()).pathname).toBe("/");
      await page.context().clearCookies();
    }
  });

  test("callback without the browser-bound state cookie is rejected (login CSRF)", async ({
    request,
  }) => {
    const response = await request.get(
      "/auth/callback?code=stolen-code&state=attackerstate".padEnd(80, "x"),
      {
        maxRedirects: 0,
      },
    );
    expect(response.status()).toBe(302);
    expect(response.headers()["location"]).toBe(`${APP_ORIGIN}/?auth_error=login_failed`);
    expect(response.headers()["set-cookie"] ?? "").not.toContain(SESSION_COOKIE + "=");
  });

  test("login CSRF: an attacker's VALID callback URL does not log the victim into the attacker's account", async ({
    browser,
  }) => {
    // 1. The attacker starts a login in their own browser and authenticates, but we stop the browser
    //    right before it hits our callback, keeping the (valid, unused) code+state.
    const attacker = await browser.newContext({ baseURL: APP_ORIGIN });
    const attackerPage = await attacker.newPage();
    let stolenCallback = "";
    // Keycloak answers the credential POST with a 302 to our callback. Read that Location without
    // following it, so the code stays unused.
    await attackerPage.route(/\/login-actions\/authenticate/, async (route) => {
      const response = await route.fetch({ maxRedirects: 0 });
      const location = response.headers()["location"] ?? "";
      if (location.includes("/auth/callback")) {
        stolenCallback = location;
        return route.fulfill({ status: 200, body: "stopped before the callback" });
      }
      return route.fulfill({ response });
    });
    await attackerPage.goto("/auth/login");
    await attackerPage.locator("#username").fill("learner");
    await attackerPage.locator("#password").fill("Dev-Only-Pass-1!");
    await attackerPage.locator("#kc-login").click();
    await expect.poll(() => stolenCallback, { timeout: 20_000 }).not.toBe("");

    // 2. The victim's browser (no login cookie from this flow) is tricked into loading that URL.
    const victim = await browser.newContext({ baseURL: APP_ORIGIN });
    const forced = await victim.request.get(stolenCallback, { maxRedirects: 0 });
    expect(forced.status()).toBe(302);
    expect(forced.headers()["location"]).toContain("auth_error=login_failed");
    expect(await sessionCookieValue(victim), "victim must NOT receive a session").toBeUndefined();

    // 3. Control: the same URL IS valid for the attacker's own browser (so step 2 failed for the right reason).
    const legit = await attacker.request.get(stolenCallback, { maxRedirects: 0 });
    expect(legit.status()).toBe(302);
    expect(legit.headers()["location"]).not.toContain("auth_error");
    expect(await sessionCookieValue(attacker)).toBeDefined();
    await attacker.close();
    await victim.close();
  });

  test("callback with a forged state (matching cookie but no server-side transaction) is rejected", async ({
    page,
    request,
  }) => {
    const state = "forged-state-".padEnd(43, "z");
    await page.context().addCookies([cookieFor(LOGIN_COOKIE, state)]);
    const response = await page.request.get(`/auth/callback?code=whatever&state=${state}`, {
      maxRedirects: 0,
    });
    expect(response.status()).toBe(302);
    expect(response.headers()["location"]).toContain("auth_error=login_failed");
    expect(await sessionCookieValue(page.context())).toBeUndefined();
    void request;
  });

  test("a captured callback URL cannot be replayed (single-use transaction + one-time code)", async ({
    page,
  }) => {
    let callbackUrl = "";
    let state = "";
    page.on("request", (req) => {
      if (req.url().startsWith(`${APP_ORIGIN}/auth/callback`)) {
        callbackUrl = req.url();
        state = new URL(req.url()).searchParams.get("state") ?? "";
      }
    });
    await login(page, "learner");
    expect(callbackUrl).not.toBe("");

    // Attacker replays the callback in a fresh browser holding a matching state cookie.
    const attacker = await page.context().browser()!.newContext({ baseURL: APP_ORIGIN });
    await attacker.addCookies([cookieFor(LOGIN_COOKIE, state)]);
    const replay = await attacker.request.get(callbackUrl, { maxRedirects: 0 });
    expect(replay.status()).toBe(302);
    expect(replay.headers()["location"]).toContain("auth_error=login_failed");
    expect(await sessionCookieValue(attacker)).toBeUndefined();
    await attacker.close();
  });

  test("session fixation: an attacker-chosen session cookie is never adopted", async ({
    page,
    context,
  }) => {
    const planted = "A".repeat(43);
    await context.addCookies([cookieFor(SESSION_COOKIE, planted)]);
    expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(false);

    await login(page, "learner");
    const after = await sessionCookieValue(context);
    expect(after).toBeDefined();
    expect(after).not.toBe(planted); // a new random id was issued at login

    // the attacker's planted value maps to nothing
    const attacker = await page.context().browser()!.newContext({ baseURL: APP_ORIGIN });
    await attacker.addCookies([cookieFor(SESSION_COOKIE, planted)]);
    expect((await (await attacker.request.get("/api/session")).json()).authenticated).toBe(false);
    await attacker.close();
  });

  test("forged, malformed and empty session cookies are anonymous", async ({ page, context }) => {
    for (const value of ["B".repeat(43), "short", "x".repeat(500), "' OR 1=1 --", "%00"]) {
      await context.clearCookies();
      await context.addCookies([cookieFor(SESSION_COOKIE, value)]);
      const body = await (await page.request.get("/api/session")).json();
      expect(body.authenticated).toBe(false);
      expect((await page.request.get("/api/proxy/system/whoami")).status()).toBe(401);
    }
  });

  test("anonymous access to a protected page redirects to the login page", async ({ page }) => {
    await page.goto("/account");
    await expect(page).toHaveURL(/openid-connect\/auth/);
    await expect(page.locator("#username")).toBeVisible();
  });

  test("hostile error parameters are never reflected into the page", async ({ page }) => {
    await page.goto("/?auth_error=<script>alert(1)</script>");
    await expect(page.getByTestId("auth-error")).toHaveCount(0);
    await page.goto("/?auth_error=login_failed");
    await expect(page.getByTestId("auth-error")).toHaveText("Sign-in failed. Please try again.");
  });
});

test.describe("BFF proxy hardening", () => {
  test("unauthenticated proxy calls get a 401 problem+json", async ({ request }) => {
    const response = await request.get("/api/proxy/system/whoami");
    expect(response.status()).toBe(401);
    expect(response.headers()["content-type"]).toContain("application/problem+json");
    expect((await response.json()).code).toBe("UNAUTHENTICATED");
  });

  test("CSRF: unsafe methods need the same Origin AND the per-session token", async ({ page }) => {
    await login(page, "learner");
    const token = await csrfToken(page.request);
    const call = (headers: Record<string, string>) =>
      page.request.post("/api/proxy/system/ping", { headers, maxRedirects: 0 });

    expect((await call({})).status()).toBe(403); // no token, no origin
    expect((await call({ origin: "https://evil.example", "x-csrf-token": token })).status()).toBe(
      403,
    ); // right token, wrong site
    expect((await call({ origin: APP_ORIGIN, "x-csrf-token": "wrong-token" })).status()).toBe(403); // right site, wrong token
    expect((await call({ origin: APP_ORIGIN })).status()).toBe(403); // right site, no token
    const rejected = await call({ origin: APP_ORIGIN });
    expect((await rejected.json()).code).toBe("CSRF_REJECTED");

    // Both present: the request passes CSRF and is FORWARDED; the API answers 405 (ping is GET-only).
    const forwarded = await call({ origin: APP_ORIGIN, "x-csrf-token": token });
    expect(forwarded.status()).toBe(405);
    expect((await forwarded.json()).code).toBe("METHOD_NOT_ALLOWED");
  });

  test("path traversal / SSRF attempts never reach other API or actuator endpoints", async ({
    page,
  }) => {
    await login(page, "admin");
    const attempts = [
      "/api/proxy/%2e%2e/%2e%2e/actuator/env",
      "/api/proxy/system/%2e%2e/%2e%2e/actuator/health",
      "/api/proxy/%252e%252e/actuator",
      "/api/proxy/system%2fwhoami",
      "/api/proxy/system%5cwhoami",
      "/api/proxy/@evil.example/x",
      "/api/proxy/http:/evil.example/x",
    ];
    for (const path of attempts) {
      const response = await page.request.get(path, { maxRedirects: 0 });
      expect([400, 404], `${path} -> ${response.status()}`).toContain(response.status());
      expect(await response.text()).not.toMatch(/"status":"UP"|activeProfiles|propertySources/);
    }
  });

  test("Set-Cookie and upstream auth headers never cross the proxy boundary", async ({ page }) => {
    await login(page, "learner");
    const response = await page.request.get("/api/proxy/system/admin/ping"); // 403 from the API
    expect(response.status()).toBe(403);
    const headers = response.headers();
    expect(headers["set-cookie"]).toBeUndefined();
    expect(headers["www-authenticate"]).toBeUndefined();
    expect(headers["cache-control"]).toBe("no-store");
  });

  test("oversized request bodies are refused before reaching the API", async ({ page }) => {
    await login(page, "learner");
    const token = await csrfToken(page.request);
    const response = await page.request.post("/api/proxy/system/ping", {
      headers: { origin: APP_ORIGIN, "x-csrf-token": token, "content-type": "application/json" },
      data: JSON.stringify({ blob: "x".repeat(2 * 1024 * 1024) }),
    });
    expect(response.status()).toBe(413);
    expect((await response.json()).code).toBe("PAYLOAD_TOO_LARGE");
  });

  test("a client-supplied Authorization header is ignored; only the server-side session decides", async ({
    request,
  }) => {
    const response = await request.get("/api/proxy/system/whoami", {
      headers: { authorization: "Bearer anything" },
    });
    expect(response.status()).toBe(401); // no session cookie => anonymous, regardless of the header
  });
});

test.describe("session lifecycle", () => {
  test("logout destroys the session server-side: the old cookie is dead and SSO is ended", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const oldSid = (await sessionCookieValue(context))!;
    const { redis, close } = await openStore();
    try {
      const before = (await redis.keys("bff:session:*")).length;

      await page.getByRole("button", { name: "Log out" }).click();
      await page.waitForURL((url) => url.origin === APP_ORIGIN, { timeout: 20_000 });
      await expect(page.getByTestId("signed-out")).toBeVisible();
      expect(await sessionCookieValue(context)).toBeUndefined();
      expect((await redis.keys("bff:session:*")).length).toBe(before - 1);

      // Replaying the stolen/old cookie after logout must not work.
      await context.addCookies([cookieFor(SESSION_COOKIE, oldSid)]);
      expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(false);
      expect((await page.request.get("/api/proxy/system/whoami")).status()).toBe(401);
      await context.clearCookies();
    } finally {
      await close();
    }

    // The Keycloak SSO session ended too: logging in again asks for credentials (no silent re-login).
    await page.goto("/auth/login");
    await expect(page.locator("#username")).toBeVisible();
  });

  test("logout is POST-only and CSRF-protected", async ({ page }) => {
    await login(page, "learner");
    const token = await csrfToken(page.request);

    expect((await page.request.get("/auth/logout")).status()).toBe(405); // no GET logout (cannot be triggered by a link)
    const noToken = await page.request.post("/auth/logout", {
      headers: { origin: APP_ORIGIN },
      maxRedirects: 0,
    });
    expect(noToken.status()).toBe(403);
    const wrongOrigin = await page.request.post("/auth/logout", {
      headers: { origin: "https://evil.example" },
      form: { csrf: token },
      maxRedirects: 0,
    });
    expect(wrongOrigin.status()).toBe(403);
    expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(true); // still logged in
  });

  test("expired access token is refreshed transparently and the refresh token is rotated", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const sid = (await sessionCookieValue(context))!;
    const { store, close } = await openStore();
    try {
      const before = (await store.load(sid))!;
      await store.save(sid, { ...before, accessExpiresAt: Math.floor(Date.now() / 1000) - 10 }); // force expiry

      const response = await page.request.get("/api/proxy/system/whoami");
      expect(response.status()).toBe(200);

      const after = (await store.load(sid))!;
      expect(after.accessToken).not.toBe(before.accessToken);
      expect(after.refreshToken).not.toBe(before.refreshToken); // Keycloak rotation
      expect(after.refreshedAt).toBeGreaterThan(0);
      expect(after.accessExpiresAt).toBeGreaterThan(Math.floor(Date.now() / 1000));
      expect(after.csrfToken).toBe(before.csrfToken);
    } finally {
      await close();
    }
  });

  test("parallel requests with an expired token cause ONE refresh and never log the user out", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const sid = (await sessionCookieValue(context))!;
    const { store, close } = await openStore();
    try {
      for (let round = 0; round < 2; round++) {
        const before = (await store.load(sid))!;
        await store.save(sid, { ...before, accessExpiresAt: Math.floor(Date.now() / 1000) - 10 });
        const responses = await Promise.all(
          Array.from({ length: 8 }, () => page.request.get("/api/proxy/system/whoami")),
        );
        expect(responses.map((r) => r.status())).toEqual(Array(8).fill(200));
        // Re-use of a rotated refresh token would have made Keycloak revoke the session: the next round proves it didn't.
        expect((await store.load(sid))!.refreshToken).not.toBe(before.refreshToken);
      }
      expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(true);
    } finally {
      await close();
    }
  });

  test("a refresh token the IdP rejects ends the session (401, then login again)", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const sid = (await sessionCookieValue(context))!;
    const { store, close } = await openStore();
    try {
      const s = (await store.load(sid))!;
      await store.save(sid, { ...s, refreshToken: "revoked-or-garbage", accessExpiresAt: 0 });
      const response = await page.request.get("/api/proxy/system/whoami");
      expect(response.status()).toBe(401);
      expect((await response.json()).code).toBe("UNAUTHENTICATED");
      expect(await store.load(sid)).toBeNull(); // session removed
      expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(false);
    } finally {
      await close();
    }
  });

  test("a session entry tampered with in Redis is treated as no session", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const sid = (await sessionCookieValue(context))!;
    const { redis, keyOf, close } = await openStore();
    try {
      const key = keyOf(sid);
      const original = (await redis.get(key))!;
      expect(original).toMatch(/^v1\./);
      // flip bits in the ciphertext, keeping the TTL (a real attacker with Redis write access)
      await redis.set(key, original.slice(0, -3) + (original.endsWith("AAA") ? "BBB" : "AAA"), {
        KEEPTTL: true,
      });
      expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(false);
      expect(await redis.get(key)).toBeNull(); // the corrupt entry was dropped
    } finally {
      await close();
    }
  });

  test("ciphertext copied from another session's key does not authenticate (AAD binding)", async ({
    browser,
  }) => {
    const victim = await browser.newContext({ baseURL: APP_ORIGIN });
    const attacker = await browser.newContext({ baseURL: APP_ORIGIN });
    const vPage = await victim.newPage();
    const aPage = await attacker.newPage();
    await login(vPage, "admin");
    await login(aPage, "learner");
    const vSid = (await sessionCookieValue(victim))!;
    const aSid = (await sessionCookieValue(attacker))!;
    const { redis, keyOf, close } = await openStore();
    try {
      // attacker (with Redis write access) overwrites THEIR entry with the ADMIN session's ciphertext
      await redis.set(keyOf(aSid), (await redis.get(keyOf(vSid)))!, { KEEPTTL: true });
      expect((await (await aPage.request.get("/api/session")).json()).authenticated).toBe(false);
      expect((await (await vPage.request.get("/api/session")).json()).authenticated).toBe(true); // victim unaffected
    } finally {
      await close();
      await victim.close();
      await attacker.close();
    }
  });
});
