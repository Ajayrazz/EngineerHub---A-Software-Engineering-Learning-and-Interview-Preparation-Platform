import { expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { createRedisClient, redisKeyValueStore } from "../src/lib/session/redis-kv";
import { deriveKeys, keyFor } from "../src/lib/session/crypto";
import { SessionStore } from "../src/lib/session/store";

export const PASSWORD = "Dev-Only-Pass-1!"; // fake dev password from the Keycloak dev realm
export const SESSION_COOKIE = "eh_session"; // plain name: tests run over http://localhost
export const LOGIN_COOKIE = "eh_login";
export const APP_ORIGIN = process.env.APP_URL ?? "http://localhost:3000";

/** Logs in through the real Keycloak UI and lands on `returnTo`. */
export async function login(page: Page, username: string, returnTo = "/account") {
  await page.goto(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  await page.waitForURL((url) => url.origin === APP_ORIGIN, { timeout: 20_000 });
}

export async function sessionCookieValue(context: BrowserContext): Promise<string | undefined> {
  return (await context.cookies()).find((c) => c.name === SESSION_COOKIE)?.value;
}

export async function csrfToken(request: APIRequestContext): Promise<string> {
  const body = await (await request.get("/api/session")).json();
  expect(body.authenticated).toBe(true);
  return body.csrfToken as string;
}

/** Direct access to the server-side session store (same crypto + keys as the app) for white-box checks. */
export async function openStore() {
  const secret = process.env.SESSION_SECRET;
  const url = process.env.REDIS_URL;
  if (!secret || !url)
    throw new Error("SESSION_SECRET and REDIS_URL must be exported (use `make e2e`)");
  const redis = createRedisClient(url);
  await redis.connect();
  const keys = deriveKeys(secret);
  const store = new SessionStore(redisKeyValueStore(redis), keys, {
    idleTtlSeconds: 604800,
    absoluteTtlSeconds: 2592000,
  });
  /** The Redis key that holds the session with this cookie value. */
  const keyOf = (sid: string) => keyFor(keys.macKey, "session", sid);
  return { redis, store, keyOf, close: () => redis.quit() };
}

/** Collects CSP violations reported by the browser while the page runs. */
export async function trackCspViolations(page: Page) {
  await page.addInitScript(() => {
    (window as unknown as { __csp: string[] }).__csp = [];
    document.addEventListener("securitypolicyviolation", (e) =>
      (window as unknown as { __csp: string[] }).__csp.push(
        `${e.violatedDirective} ${e.blockedURI}`,
      ),
    );
  });
  return () => page.evaluate(() => (window as unknown as { __csp: string[] }).__csp);
}
