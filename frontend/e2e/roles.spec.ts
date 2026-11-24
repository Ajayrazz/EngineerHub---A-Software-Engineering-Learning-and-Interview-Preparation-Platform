import { expect, test, type Browser } from "@playwright/test";
import { login } from "./helpers";

/** Each role gets its own experience: staff land in the console with their tools; learners never see them. */

async function as(browser: Browser, user: string, returnTo: string) {
  const page = await (await browser.newContext()).newPage();
  await login(page, user, returnTo);
  return page;
}

test("an administrator lands in the console with a role badge and the staff navigation", async ({
  browser,
}) => {
  const page = await as(browser, "admin", "/");
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByTestId("admin-welcome")).toContainText("Administrator");
  await page.goto("/courses"); // public pages carry the staff navigation too
  const nav = page.getByRole("navigation", { name: "Main" });
  await expect(page.getByTestId("role-badge")).toHaveText("Administrator");
  await expect(nav.getByRole("link", { name: "Admin console" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Users" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Billing" })).toBeVisible();
  await expect(nav.getByRole("button", { name: /Learn/ })).toHaveCount(0); // not the learner menus
  await page.context().close();
});

test("a content manager sees course tools only, and the overview says so", async ({ browser }) => {
  const page = await as(browser, "contentmanager", "/admin");
  await expect(page.getByTestId("admin-welcome")).toContainText("Content manager");
  const nav = page.getByRole("navigation", { name: "Admin" });
  await expect(nav.getByRole("link", { name: "Courses" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Problems" })).toHaveCount(0); // that is the problem setter's tool
  await expect(nav.getByRole("link", { name: "Users" })).toHaveCount(0);
  await expect(nav.getByRole("link", { name: "Billing" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Add a course" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Manage users" })).toHaveCount(0);
  await page.context().close();
});

test("a problem setter gets problem tools and no course or user tools", async ({ browser }) => {
  const page = await as(browser, "problemsetter", "/admin");
  await expect(page.getByTestId("admin-welcome")).toContainText("Problem setter");
  const nav = page.getByRole("navigation", { name: "Admin" });
  await expect(nav.getByRole("link", { name: "Problems" })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Users" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Add a problem" })).toBeVisible();
  await page.context().close();
});

test("a learner sees the learning navigation and no admin entry points", async ({ browser }) => {
  const page = await as(browser, "learner", "/");
  await expect(page).not.toHaveURL(/\/admin/);
  const nav = page.getByRole("navigation", { name: "Main" });
  await expect(nav.getByRole("button", { name: /Learn/ })).toBeVisible();
  await expect(nav.getByRole("link", { name: "Admin console" })).toHaveCount(0);
  await expect(page.getByTestId("role-badge")).toHaveCount(0);
  expect((await page.goto("/admin"))?.status()).toBe(404); // the area is not advertised
  await page.context().close();
});
