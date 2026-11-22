import { expect, test } from "@playwright/test";
import {
  csrfToken,
  login,
  openStore,
  SESSION_COOKIE,
  sessionCookieValue,
  trackCspViolations,
} from "./helpers";

test.describe("login through the BFF (authorization code + PKCE)", () => {
  test("anonymous visitor sees the signed-out home page", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByTestId("signed-out")).toBeVisible();
    await expect(page.getByRole("link", { name: "Log in" })).toHaveAttribute("href", "/auth/login");
  });

  test("learner logs in via Keycloak and the API sees the right identity", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("link", { name: "Log in" }).click();
    await expect(page).toHaveURL(/\/realms\/engineerhub\/protocol\/openid-connect\/auth/);
    await page.locator("#username").fill("learner");
    await page.locator("#password").fill("Dev-Only-Pass-1!");
    await page.locator("#kc-login").click();

    await page.waitForURL("**/");
    await expect(page.getByTestId("signed-in")).toBeVisible();
    await expect(page.getByTestId("username")).toHaveText("learner");

    await page.goto("/account");
    await expect(page.getByTestId("account-username")).toHaveText("learner");
    await expect(page.getByTestId("api-subject")).not.toHaveText("unavailable"); // server-side API call worked
    await expect(page.getByTestId("api-roles")).toHaveText("none");
  });

  test("returnTo brings the user back to the requested page", async ({ page }) => {
    await page.goto("/account"); // not logged in -> redirected to login with returnTo=/account
    await page.locator("#username").fill("learner");
    await page.locator("#password").fill("Dev-Only-Pass-1!");
    await page.locator("#kc-login").click();
    await page.waitForURL("**/account");
    await expect(page.getByTestId("account-username")).toHaveText("learner");
  });

  test("admin gets the ADMIN role and may call the admin endpoint through the BFF proxy", async ({
    page,
  }) => {
    await login(page, "admin");
    await expect(page.getByTestId("api-roles")).toHaveText("ADMIN");
    await page.getByRole("button", { name: "Call admin endpoint via BFF" }).click();
    const result = page.getByTestId("api-result");
    await expect(result).toHaveAttribute("data-status", "200");
  });

  test("learner is refused (403) on the admin endpoint, with the API's problem+json", async ({
    page,
  }) => {
    await login(page, "learner");
    await page.getByRole("button", { name: "Call admin endpoint via BFF" }).click();
    const result = page.getByTestId("api-result");
    await expect(result).toHaveAttribute("data-status", "403");
    await expect(result).toContainText("FORBIDDEN");
  });

  test("tokens never reach the browser: HttpOnly opaque cookie, nothing in JS-visible storage or HTML", async ({
    page,
    context,
  }) => {
    await login(page, "learner");

    const cookie = (await context.cookies()).find((c) => c.name === SESSION_COOKIE);
    expect(cookie, "session cookie").toBeDefined();
    expect(cookie).toMatchObject({ httpOnly: true, sameSite: "Lax", path: "/" });
    expect(cookie!.value).toMatch(/^[A-Za-z0-9_-]{43}$/); // opaque 256-bit id, not a JWT
    expect(cookie!.value).not.toContain("eyJ");

    expect(await page.evaluate(() => document.cookie)).not.toContain(SESSION_COOKIE); // HttpOnly
    const storage = await page.evaluate(
      () => JSON.stringify({ ...localStorage }) + JSON.stringify({ ...sessionStorage }),
    );
    expect(storage).not.toMatch(/eyJ|token|session/i);

    const html = await page.content();
    expect(html).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}/); // no JWT anywhere in the DOM
    expect(html).not.toMatch(/access_token|refresh_token|id_token/i);

    const sessionJson = JSON.stringify(await (await page.request.get("/api/session")).json());
    expect(sessionJson).not.toMatch(/eyJ|access|refresh|idToken/i);
    expect(sessionJson).toContain("csrfToken"); // the only secret-ish value the UI gets
  });

  test("Redis holds the session encrypted and keyed by an HMAC of the id", async ({
    page,
    context,
  }) => {
    await login(page, "learner");
    const sid = (await sessionCookieValue(context))!;
    const { redis, close } = await openStore();
    try {
      const keys = await redis.keys("bff:session:*");
      expect(keys.length).toBeGreaterThan(0);
      for (const key of keys) {
        expect(key).not.toContain(sid);
        const raw = (await redis.get(key)) ?? "";
        expect(raw).toMatch(/^v1\.[\w-]+\.[\w-]+\.[\w-]+$/); // v1.<iv>.<tag>.<ciphertext>
        // Look inside the decoded ciphertext bytes (not the base64 text, where random data can contain
        // "eyJ" by chance): plaintext JSON markers must be absent.
        const bytes = Buffer.from(raw.split(".")[3], "base64url").toString("latin1");
        for (const marker of ['"accessToken"', '"refreshToken"', "learner", "eyJ"]) {
          expect(bytes.includes(marker), `plaintext marker ${marker} found in Redis value`).toBe(
            false,
          );
        }
        expect(await redis.ttl(key)).toBeGreaterThan(0);
      }
    } finally {
      await close();
    }
  });

  test("the proxy forwards authenticated calls with the user's identity (whoami)", async ({
    page,
  }) => {
    await login(page, "admin");
    const response = await page.request.get("/api/proxy/system/whoami");
    expect(response.status()).toBe(200);
    expect(response.headers()["cache-control"]).toBe("no-store");
    expect(response.headers()["x-correlation-id"]).toBeTruthy();
    const body = await response.json();
    expect(body.roles).toEqual(["ADMIN"]);
  });

  test("pages load without any CSP violation, and the nonce in the header matches the page's scripts", async ({
    page,
  }) => {
    const violations = await trackCspViolations(page);
    const response = await page.goto("/");
    const csp = response!.headers()["content-security-policy"];
    const nonce = /'nonce-([^']+)'/.exec(csp)![1];
    expect(csp).toContain("frame-ancestors 'none'");
    expect(csp).toContain("object-src 'none'");
    expect(csp).toContain("'strict-dynamic'");
    expect(csp).not.toContain("unsafe-inline"); // production build: no unsafe-inline / unsafe-eval
    expect(csp).not.toContain("unsafe-eval");
    // Browsers hide the nonce *attribute* from the DOM; the `nonce` property still carries it.
    const scriptNonces = await page
      .locator("script")
      .evaluateAll((els) => els.map((e) => (e as HTMLScriptElement).nonce));
    expect(scriptNonces).toContain(nonce);

    await login(page, "learner");
    await page.getByRole("button", { name: "Call whoami via BFF" }).click(); // client component hydrated & working
    await expect(page.getByTestId("api-result")).toHaveAttribute("data-status", "200");
    expect(await violations()).toEqual([]);
  });

  test("each request gets a fresh nonce and hardening headers are present", async ({ request }) => {
    const a = await request.get("/");
    const b = await request.get("/");
    const nonce = (r: typeof a) =>
      /'nonce-([^']+)'/.exec(r.headers()["content-security-policy"])![1];
    expect(nonce(a)).not.toBe(nonce(b));
    const h = a.headers();
    expect(h["x-frame-options"]).toBe("DENY");
    expect(h["x-content-type-options"]).toBe("nosniff");
    expect(h["referrer-policy"]).toBe("strict-origin-when-cross-origin");
    expect(h["x-powered-by"]).toBeUndefined();
  });

  test("/api/session reports anonymous visitors as unauthenticated without a CSRF token", async ({
    request,
  }) => {
    const body = await (await request.get("/api/session")).json();
    expect(body).toEqual({ authenticated: false });
  });

  test("csrf token is stable for a session and only available when logged in", async ({ page }) => {
    await login(page, "learner");
    expect(await csrfToken(page.request)).toBe(await csrfToken(page.request));
  });
});
