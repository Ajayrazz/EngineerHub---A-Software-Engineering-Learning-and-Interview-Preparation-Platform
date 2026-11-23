import { expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { createRedisClient, redisKeyValueStore } from "../src/lib/session/redis-kv";
import { deriveKeys, keyFor } from "../src/lib/session/crypto";
import { SessionStore } from "../src/lib/session/store";
import fs from "node:fs";
import path from "node:path";
import { DEV_TOTP_SEED, totp } from "./totp";

export const PASSWORD = "Dev-Only-Pass-1!"; // fake dev password from the Keycloak dev realm
export const SESSION_COOKIE = "eh_session"; // plain name: tests run over http://localhost
export const LOGIN_COOKIE = "eh_login";
export const APP_ORIGIN = process.env.APP_URL ?? "http://localhost:3000";

// Persisted between runs: Keycloak remembers the last step each user used, so a fresh process must
// continue after it (the dev realm's wide look-ahead window leaves room for hundreds of logins).
const OTP_STATE = path.join(__dirname, ".otp-state.json");
function readOtpState(): Record<string, number> {
  try {
    return JSON.parse(fs.readFileSync(OTP_STATE, "utf8")) as Record<string, number>;
  } catch {
    return {};
  }
}
function rememberOtpStep(username: string, step: number) {
  const state = readOtpState();
  state[username] = Math.max(state[username] ?? 0, step);
  fs.writeFileSync(OTP_STATE, JSON.stringify(state));
}

/**
 * Keycloak rejects a TOTP code whose time step was already used (replay protection), and tests log the
 * same user in repeatedly within one 30 s window. So each login uses a strictly later step than the
 * previous one for that user (the dev realm widens the look-ahead window to allow it), retrying once
 * more if a step was consumed elsewhere.
 */
/** The next unused TOTP code for a user (recorded as used, so later calls move on). */
export function nextOtpCode(username: string): string {
  const now = Date.now();
  const current = Math.floor(now / 1000 / 30);
  const step = Math.max(current, (readOtpState()[username] ?? 0) + 1);
  rememberOtpStep(username, step);
  return totp(DEV_TOTP_SEED, now, step - current);
}

async function answerOtp(page: Page, username: string) {
  for (let attempt = 0; attempt < 5; attempt++) {
    await page.locator("#otp").fill(nextOtpCode(username));
    await page.locator("#kc-login").click();
    const result = await Promise.race([
      page.waitForURL((url) => url.origin === APP_ORIGIN, { timeout: 15_000 }).then(() => "ok"),
      page
        .getByText("Invalid authenticator code")
        .waitFor({ timeout: 15_000 })
        .then(() => "retry"),
    ]);
    if (result === "ok") return;
  }
  throw new Error(`OTP for ${username} was rejected five times`);
}

/** Logs in through the real Keycloak UI and lands on `returnTo`. */
export async function login(page: Page, username: string, returnTo = "/account") {
  await page.goto(`/auth/login?returnTo=${encodeURIComponent(returnTo)}`);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  // Staff accounts must pass TOTP (docs/08): answer the second step when Keycloak asks for it.
  const otp = page.locator("#otp");
  const next = await Promise.race([
    otp.waitFor({ state: "visible", timeout: 15_000 }).then(() => "otp" as const),
    page
      .waitForURL((url) => url.origin === APP_ORIGIN, { timeout: 15_000 })
      .then(() => "app" as const),
  ]);
  if (next === "otp") await answerOtp(page, username);
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
