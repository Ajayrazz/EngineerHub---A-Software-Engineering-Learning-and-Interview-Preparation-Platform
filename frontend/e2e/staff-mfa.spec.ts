import { expect, test, type Page } from "@playwright/test";
import { APP_ORIGIN, PASSWORD, SESSION_COOKIE, login, nextOtpCode } from "./helpers";

/** Staff roles must pass TOTP in the browser login (realm flow `browser-staff-mfa`, docs/08). */

async function passwordStep(page: Page, username: string) {
  await page.goto("/auth/login?returnTo=/account");
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

const sessionCookieOf = async (page: Page) =>
  (await page.context().cookies()).find((c) => c.name === SESSION_COOKIE);

test.describe("staff MFA", () => {
  test("a learner is never asked for a one-time code", async ({ page }) => {
    await passwordStep(page, "learner");
    await page.waitForURL((u) => u.origin === APP_ORIGIN, { timeout: 20_000 });
    expect(await sessionCookieOf(page)).toBeDefined();
  });

  test("a staff password alone does not create a session", async ({ page }) => {
    await passwordStep(page, "problemsetter");
    await expect(page.locator("#otp")).toBeVisible();
    // Skipping the second step and going straight to the app yields no session.
    await page.goto("/account");
    expect(await sessionCookieOf(page)).toBeUndefined();
    await expect(page.locator("#username")).toBeVisible(); // bounced back to the login form
  });

  test("a wrong one-time code is rejected", async ({ page }) => {
    await passwordStep(page, "contentmanager");
    await page.locator("#otp").fill("000000");
    await page.locator("#kc-login").click();
    await expect(page.getByText("Invalid authenticator code")).toBeVisible();
    expect(new URL(page.url()).origin).not.toBe(APP_ORIGIN);
    expect(await sessionCookieOf(page)).toBeUndefined();
  });

  test("a code that was already used cannot be replayed", async ({ browser }) => {
    const code = nextOtpCode("moderator"); // one code, used twice

    const first = await (await browser.newContext()).newPage();
    await passwordStep(first, "moderator");
    await first.locator("#otp").fill(code);
    await first.locator("#kc-login").click();
    await first.waitForURL((u) => u.origin === APP_ORIGIN, { timeout: 20_000 });

    const second = await (await browser.newContext()).newPage();
    await passwordStep(second, "moderator");
    await second.locator("#otp").fill(code);
    await second.locator("#kc-login").click();
    await expect(second.getByText("Invalid authenticator code")).toBeVisible();
    expect(await sessionCookieOf(second)).toBeUndefined();
  });

  test("a staff user with a valid code gets in (helper path)", async ({ page }) => {
    await login(page, "instructor");
    expect(await sessionCookieOf(page)).toBeDefined();
  });
});
