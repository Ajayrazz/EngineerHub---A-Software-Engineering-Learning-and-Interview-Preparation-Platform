import { expect, test, type Browser, type Page } from "@playwright/test";
import { login } from "./helpers";

/** The admin shell: server-side gating, and every action goes through the API with its own checks. */

test.describe.configure({ mode: "serial" });

async function newPage(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await login(page, username, "/account");
  return page;
}

async function openLearner(page: Page) {
  await page.goto("/admin/users?q=learner");
  await page.locator('[data-testid="user-row"] a', { hasText: /^learner$/ }).click();
  await expect(page.getByTestId("user-details")).toBeVisible();
}

test.beforeAll(async ({ browser }) => {
  // make sure the learner exists locally (users are provisioned on first login)
  await (await newPage(browser, "learner")).context().close();
});

test.describe("access to the admin area", () => {
  test("anonymous visitors are sent to the login page", async ({ page }) => {
    await page.goto("/admin/users");
    await expect(page.locator("#username")).toBeVisible();
  });

  test("a signed-in learner gets a 404, not a hint that the area exists", async ({ browser }) => {
    const page = await newPage(browser, "learner");
    for (const path of ["/admin", "/admin/users", "/admin/audit"]) {
      const response = await page.goto(path);
      expect(response?.status(), path).toBe(404);
      await expect(page.getByText("Admin", { exact: true })).toHaveCount(0);
    }
  });

  test("staff without admin permissions (a moderator) also get a 404", async ({ browser }) => {
    const page = await newPage(browser, "moderator");
    expect((await page.goto("/admin"))?.status()).toBe(404);
  });

  test("an admin sees the overview with links for the permissions they hold", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    const response = await page.goto("/admin");
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId("admin-welcome")).toContainText("admin");
    await expect(page.getByRole("link", { name: "Users" }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: "Audit log" }).first()).toBeVisible();
  });

  test("a malformed user id in the URL is a 404 and never reaches the API", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    expect((await page.goto("/admin/users/not-a-uuid"))?.status()).toBe(404);
    expect((await page.goto("/admin/users/..%2F..%2Factuator%2Fenv"))?.status()).toBe(404);
  });
});

test.describe("user administration", () => {
  test("search finds users and filters by status", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await page.goto("/admin/users?q=learner");
    await expect(page.getByTestId("user-row").first()).toContainText("learner");
    await page.goto("/admin/users?q=learner&status=suspended");
    await expect(page.getByTestId("user-row")).toHaveCount(0);
    // wildcard characters are searched literally
    await page.goto("/admin/users?q=%25");
    await expect(page.getByTestId("user-row")).toHaveCount(0);
  });

  test("suspend and reactivate an account (reason required, shown in the audit log, HTML escaped)", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    await openLearner(page);
    await expect(page.getByTestId("user-status")).toHaveText("active");

    const reason = `<b>bold</b> e2e suspension ${Date.now()}`;
    await page.locator('input[name="status-reason"]').fill(reason);
    await page.getByTestId("status-submit").click();
    await expect(page.getByTestId("user-status")).toHaveText("suspended");

    await page.locator('input[name="status-reason"]').fill("e2e reactivation after check");
    await page.getByTestId("status-submit").click();
    await expect(page.getByTestId("user-status")).toHaveText("active");

    await page.goto("/admin/audit?action=user.status.changed");
    const row = page.getByTestId("audit-row").filter({ hasText: "e2e suspension" }).first();
    await expect(row).toBeVisible();
    await expect(row).toContainText("<b>bold</b>"); // shown literally ...
    await expect(row.locator("b")).toHaveCount(0); // ... never interpreted as markup
  });

  test("an admin edits ordinary staff roles but cannot touch administrator roles", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    await openLearner(page);
    const roles = page.getByRole("region", { name: "Staff roles" });
    await expect(roles.getByRole("checkbox", { name: "ADMIN", exact: true })).toBeDisabled();
    await expect(roles.getByRole("checkbox", { name: "SUPER_ADMIN" })).toBeDisabled();

    await roles.getByRole("checkbox", { name: "INSTRUCTOR" }).check();
    await roles.locator('input[name="roles-reason"]').fill("e2e: temporary instructor");
    await roles.getByTestId("roles-submit").click();
    await expect(roles.getByRole("status")).toContainText("Roles updated");
    await page.reload();
    await expect(
      page
        .getByRole("region", { name: "Staff roles" })
        .getByRole("checkbox", { name: "INSTRUCTOR" }),
    ).toBeChecked();

    // revert
    const again = page.getByRole("region", { name: "Staff roles" });
    await again.getByRole("checkbox", { name: "INSTRUCTOR" }).uncheck();
    await again.locator('input[name="roles-reason"]').fill("e2e: revert instructor");
    await again.getByTestId("roles-submit").click();
    await expect(again.getByRole("status")).toContainText("Roles updated");
    await page.reload();
    await expect(
      page
        .getByRole("region", { name: "Staff roles" })
        .getByRole("checkbox", { name: "INSTRUCTOR" }),
    ).not.toBeChecked();
  });

  test("a super admin may toggle administrator roles", async ({ browser }) => {
    const page = await newPage(browser, "superadmin");
    await openLearner(page);
    const roles = page.getByRole("region", { name: "Staff roles" });
    await expect(roles.getByRole("checkbox", { name: "ADMIN", exact: true })).toBeEnabled();
  });

  test("scoped grants can be added and revoked", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await openLearner(page);
    const resource = `e2e-${Date.now()}`;
    const grants = page.getByRole("region", { name: "Scoped grants" });
    await grants.locator('input[name="grant-resource"]').fill(resource);
    await grants.getByTestId("grant-submit").click();
    const row = grants.getByTestId("grant-row").filter({ hasText: resource });
    await expect(row).toBeVisible();
    await row.getByRole("button", { name: /Revoke/ }).click();
    await expect(grants.getByTestId("grant-row").filter({ hasText: resource })).toHaveCount(0);
  });

  test("the API's refusals are shown, not swallowed (invalid grant)", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await openLearner(page);
    const grants = page.getByRole("region", { name: "Scoped grants" });
    await grants.locator('input[name="grant-permission"]').fill("user.suspend"); // not grantable per resource
    await grants.locator('input[name="grant-resource"]').fill("x1");
    await grants.getByTestId("grant-submit").click();
    await expect(grants.getByRole("alert")).toBeVisible();
  });
});

test.describe("audit log", () => {
  test("lists entries and verifies the hash chain", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await page.goto("/admin/audit");
    await expect(page.getByTestId("audit-row").first()).toBeVisible();
    await page.getByTestId("audit-verify").click();
    await expect(page.getByTestId("audit-verify-result")).toContainText("Chain intact");
  });
});
