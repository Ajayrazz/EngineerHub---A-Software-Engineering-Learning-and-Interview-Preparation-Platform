import { expect, type APIRequestContext, type BrowserContext, type Page } from "@playwright/test";
import { createRedisClient, redisKeyValueStore } from "../src/lib/session/redis-kv";
import { deriveKeys, keyFor } from "../src/lib/session/crypto";
import { SessionStore } from "../src/lib/session/store";
import { execSync } from "node:child_process";
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

/**
 * Test-only shortcut for "verified" test data. The real path is the validation harness executed by the judge
 * (Phase 4); until then nothing can set the flag through the API, so E2E sets it directly in the dev database.
 * Needs the Docker stack (E2E_CONTAINER_CONTROL=1, set by `make e2e` and CI).
 */
export function markProblemVerified(problemId: string) {
  if (process.env.E2E_CONTAINER_CONTROL !== "1") throw new Error("set E2E_CONTAINER_CONTROL=1");
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(problemId))
    throw new Error("bad id");
  const user = process.env.APP_DB_USER ?? "engineerhub_app";
  const db = process.env.APP_DB_NAME ?? "engineerhub";
  execSync(
    `docker compose exec -T postgres psql -U ${user} -d ${db} -c "update problems.problems set validation_status = 'verified' where id = '${problemId}'"`,
    { cwd: path.join(__dirname, "..", ".."), stdio: "pipe" },
  );
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function psql(statement: string) {
  if (process.env.E2E_CONTAINER_CONTROL !== "1") throw new Error("set E2E_CONTAINER_CONTROL=1");
  const user = process.env.APP_DB_USER ?? "engineerhub_app";
  const db = process.env.APP_DB_NAME ?? "engineerhub";
  execSync(
    `docker compose exec -T postgres psql -U ${user} -d ${db} -v ON_ERROR_STOP=1 -c "${statement}"`,
    {
      cwd: path.join(__dirname, "..", ".."),
      stdio: "pipe",
    },
  );
}

const SUBSCRIPTION_STATUSES = ["active", "past_due", "cancel_scheduled", "cancelled"];

/**
 * Creates a subscription row for a user, the way the webhook-driven flow would leave it. Real checkout needs the
 * payment provider's test keys, so the browser tests seed the state they want to look at. Docker only.
 */
export function seedSubscription(args: {
  subscriptionId: string;
  userId: string;
  planId: string;
  planPriceId: string;
  status: string;
}) {
  for (const id of [args.subscriptionId, args.userId, args.planId, args.planPriceId])
    if (!UUID_RE.test(id)) throw new Error("bad id");
  if (!SUBSCRIPTION_STATUSES.includes(args.status)) throw new Error("bad status");
  psql(
    `insert into billing.subscriptions (id, user_id, plan_id, plan_price_id, period_unit, currency, amount_minor, status, provider, provider_subscription_ref, created_at, updated_at) select '${args.subscriptionId}', '${args.userId}', '${args.planId}', '${args.planPriceId}', period_unit, currency, amount_incl_tax_minor, '${args.status}', 'razorpay', 'sub_e2e_${args.subscriptionId.slice(0, 8)}', now(), now() from billing.plan_prices where id = '${args.planPriceId}'`,
  );
}

export function setSubscriptionStatus(subscriptionId: string, status: string) {
  if (!UUID_RE.test(subscriptionId)) throw new Error("bad id");
  if (!SUBSCRIPTION_STATUSES.includes(status)) throw new Error("bad status");
  psql(`update billing.subscriptions set status = '${status}' where id = '${subscriptionId}'`);
}

export function deleteSubscription(subscriptionId: string) {
  if (!UUID_RE.test(subscriptionId)) throw new Error("bad id");
  psql(`delete from billing.subscriptions where id = '${subscriptionId}'`);
}

/**
 * Records a captured payment of a subscription, the way the webhook would. NOTE: once a receipt has been issued for
 * it, neither the payment nor its subscription can be deleted (documents are immutable by design), so only use it
 * with a persona no other test counts payments for.
 */
export function seedPayment(args: { paymentId: string; subscriptionId: string; userId: string }) {
  for (const id of [args.paymentId, args.subscriptionId, args.userId])
    if (!UUID_RE.test(id)) throw new Error("bad id");
  psql(
    `insert into billing.payments (id, subscription_id, user_id, provider, provider_payment_ref, amount_minor, currency, status, captured_at, created_at) select '${args.paymentId}', id, user_id, 'razorpay', 'pay_e2e_${args.paymentId.slice(0, 8)}', amount_minor, currency, 'captured', now(), now() from billing.subscriptions where id = '${args.subscriptionId}' and user_id = '${args.userId}'`,
  );
}
