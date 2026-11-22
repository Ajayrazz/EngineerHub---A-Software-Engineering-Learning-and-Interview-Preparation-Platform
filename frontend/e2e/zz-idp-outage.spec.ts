import { execSync } from "node:child_process";
import { resolve } from "node:path";
import { expect, test } from "@playwright/test";
import { login, openStore, sessionCookieValue } from "./helpers";

/**
 * Regression test for a real flaw found in review: a failed token refresh used to destroy the session,
 * so a short Keycloak outage would have logged every user out. Now only a definitive refusal ends a
 * session; an outage keeps it and yields 503 + Retry-After until the IdP returns.
 *
 * This test stops and restarts the Keycloak container, so it only runs when explicitly allowed
 * (E2E_CONTAINER_CONTROL=1, set by `make e2e` and CI) and is named to run last.
 */
test.skip(
  process.env.E2E_CONTAINER_CONTROL !== "1",
  "set E2E_CONTAINER_CONTROL=1 to allow stopping Keycloak",
);
test.describe.configure({ mode: "serial" });

const repoRoot = resolve(__dirname, "..", "..");
const compose = (args: string) =>
  execSync(`docker compose ${args}`, { cwd: repoRoot, stdio: "pipe" });
const issuer = process.env.KEYCLOAK_ISSUER_URI ?? "http://localhost:8081/realms/engineerhub";

async function waitForKeycloak(timeoutMs = 120_000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      if ((await fetch(`${issuer}/.well-known/openid-configuration`)).ok) return;
    } catch {
      /* still starting */
    }
    await new Promise((r) => setTimeout(r, 2000));
  }
  throw new Error("Keycloak did not come back");
}

test("a Keycloak outage keeps the session, returns 503, and the same session recovers afterwards", async ({
  page,
  context,
}) => {
  test.setTimeout(240_000);
  await login(page, "learner");
  const sid = (await sessionCookieValue(context))!;
  const { store, close } = await openStore();
  try {
    const before = (await store.load(sid))!;
    await store.save(sid, { ...before, accessExpiresAt: Math.floor(Date.now() / 1000) - 10 }); // refresh needed

    compose("stop keycloak");
    try {
      const down = await page.request.get("/api/proxy/system/whoami");
      expect(down.status()).toBe(503);
      expect(down.headers()["retry-after"]).toBe("5");
      expect((await down.json()).code).toBe("SERVICE_UNAVAILABLE");

      // The user is still logged in (session NOT destroyed) and the account page degrades gracefully.
      expect((await (await page.request.get("/api/session")).json()).authenticated).toBe(true);
      await page.goto("/account");
      await expect(page.getByTestId("idp-unavailable")).toBeVisible();
      expect(await store.load(sid)).not.toBeNull();
    } finally {
      compose("start keycloak");
    }

    await waitForKeycloak();
    // Same cookie, no new login: the refresh now succeeds.
    await expect
      .poll(async () => (await page.request.get("/api/proxy/system/whoami")).status(), {
        timeout: 60_000,
      })
      .toBe(200);
    const after = (await store.load(sid))!;
    expect(after.refreshToken).not.toBe(before.refreshToken);
  } finally {
    await close();
  }
});
