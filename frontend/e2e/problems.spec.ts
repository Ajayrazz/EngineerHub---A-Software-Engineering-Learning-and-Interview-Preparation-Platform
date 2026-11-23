import { expect, test, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** Problem authoring UI: statement preview, tags, versioned tests, the publish gate, scoped setters. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const title = `E2E Problem ${stamp}`;
const slug = `e2e-problem-${stamp}`;
const HIDDEN = `HIDDEN-EXPECTED-${stamp}`;

let problemUrl = "";

async function newPage(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await login(page, username, "/account");
  return page;
}

test.beforeAll(async ({ browser }) => {
  // the problem setter starts without any assignment left over from earlier runs
  const admin = await newPage(browser, "superadmin");
  await (await newPage(browser, "problemsetter")).context().close(); // provisions the local user
  const headers = { origin: APP_ORIGIN, "x-csrf-token": await csrfToken(admin.request) };
  const found = await (await admin.request.get("/api/proxy/admin/users?q=problemsetter")).json();
  const id = found.items.find((u: { username: string }) => u.username === "problemsetter").id;
  const grants = await (await admin.request.get(`/api/proxy/admin/users/${id}/grants`)).json();
  for (const g of grants)
    await admin.request.delete(`/api/proxy/admin/users/${id}/grants/${g.id}`, { headers });
  await admin.context().close();
});

test.describe("access", () => {
  test("learners and staff without problem permissions get a 404", async ({ browser }) => {
    for (const user of ["learner", "moderator", "contentmanager"]) {
      const page = await newPage(browser, user);
      expect((await page.goto("/admin/problems"))?.status(), user).toBe(404);
    }
  });

  test("an unassigned problem setter sees an empty list, not the problem bank", async ({
    browser,
  }) => {
    const page = await newPage(browser, "problemsetter");
    expect((await page.goto("/admin/problems"))?.status()).toBe(200);
    await expect(page.getByText("You have not been assigned any problems yet.")).toBeVisible();
    await expect(page.getByTestId("problems-table")).toHaveCount(0);
  });
});

test.describe("authoring", () => {
  test("create a problem", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await page.goto("/admin/problems");
    await page.locator('input[name="problem-title"]').fill(title);
    await expect(page.locator('input[name="problem-slug"]')).toHaveValue(slug);
    await page.getByTestId("problem-create").click();
    await page.waitForURL(/\/admin\/problems\/[0-9a-f-]{36}$/);
    problemUrl = new URL(page.url()).pathname;
    await expect(page.getByTestId("problem-status")).toHaveText("draft");
    await expect(page.getByTestId("validation-status")).toHaveText("not verified");
  });

  test("the statement preview renders markdown but never runs hostile content; details persist", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    await page.goto(problemUrl);
    await page
      .getByLabel("Statement", { exact: true })
      .fill(
        'Given two integers, print their **sum**.\n\n<img src=x onerror="window.__pwned=1">\n\n[x](javascript:window.__pwned=2)',
      );
    const preview = page.getByTestId("statement-preview");
    await expect(preview.locator("strong")).toHaveText("sum");
    await expect(preview.locator("img")).toHaveCount(0);
    await expect(preview.getByText("<img src=x")).toBeVisible();
    expect(
      await page.evaluate(() => (window as unknown as { __pwned?: number }).__pwned),
    ).toBeUndefined();

    await page.getByLabel("Input format").fill("Two integers a and b.");
    await page.getByLabel("Output format").fill("One integer: a + b.");
    await page.getByLabel("Constraints").fill("|a|, |b| <= 1000");
    await page.getByRole("button", { name: "+ hint" }).click();
    await page.getByLabel("Hint 1", { exact: true }).fill("Think about integer addition.");
    await page.getByTestId("details-save").click();
    await expect(page.getByRole("status").filter({ hasText: "Details saved" })).toBeVisible();
    await page.reload();
    await expect(page.getByLabel("Output format")).toHaveValue("One integer: a + b.");
    await expect(page.getByLabel("Hint 1", { exact: true })).toHaveValue(
      "Think about integer addition.",
    );
  });

  test("tags: choose a topic and create and choose a pattern", async ({ browser }) => {
    const page = await newPage(browser, "admin");
    await page.goto(problemUrl);
    const tags = page.getByRole("region", { name: "Tags" });
    await tags.getByRole("group", { name: "Topics" }).getByLabel("Arrays", { exact: true }).check();
    await tags.locator('input[name="new-pattern"]').fill(`Two pointers ${stamp}`);
    await page.getByTestId("pattern-add").click();
    await expect(page.getByRole("status").filter({ hasText: "Pattern created" })).toBeVisible();
    await tags.getByRole("group", { name: "Patterns" }).getByLabel(`Two pointers ${stamp}`).check();
    await page.getByTestId("tags-save").click();
    await expect(page.getByRole("status").filter({ hasText: "Tags saved" })).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole("group", { name: "Topics" }).getByLabel("Arrays", { exact: true }),
    ).toBeChecked();
    await expect(
      page.getByRole("group", { name: "Patterns" }).getByLabel(`Two pointers ${stamp}`),
    ).toBeChecked();
  });

  test("test cases: validation, versioned save, and hidden tests stay out of the learner preview", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    await page.goto(problemUrl);
    await page.getByTestId("add-sample").click();
    await expect(page.getByTestId("test-problems")).toContainText("expected output is empty");
    await expect(page.getByTestId("tests-save")).toBeDisabled();
    await page.getByLabel("Test 1 input").fill("1 2");
    await page.getByLabel("Test 1 expected").fill("3");
    await page.getByLabel("Test 1 explanation").fill("1 + 2 = 3");
    await page.getByTestId("add-hidden").click();
    await page.getByLabel("Test 2 input").fill("10 20");
    await page.getByLabel("Test 2 expected").fill(HIDDEN);
    await expect(page.getByTestId("test-counts")).toContainText("1 sample · 1 hidden");
    await page.getByTestId("tests-save").click();
    await expect(page.getByRole("status").filter({ hasText: "new version" })).toBeVisible();
    await expect(page.getByTestId("test-counts")).toContainText("set v1");

    // the learner preview shows the sample (saved) and never the hidden expected output
    const preview = page.getByTestId("problem-preview");
    await expect(page.getByTestId("preview-sample")).toHaveCount(1);
    await expect(preview).toContainText("1 + 2 = 3");
    await expect(preview).not.toContainText(HIDDEN);

    await page.reload();
    await expect(page.getByLabel("Test 2 expected")).toHaveValue(HIDDEN); // authoring view keeps them
    await page.getByLabel("Test 1 expected").fill("3 ");
    await page.getByTestId("tests-save").click();
    await expect(page.getByTestId("test-counts")).toContainText("set v2");
    await expect(page.getByTestId("validation-status")).toHaveText("not verified");
  });

  test("publishing is blocked until the test data is verified, with a clear message", async ({
    browser,
  }) => {
    const page = await newPage(browser, "admin");
    await page.goto(problemUrl);
    await page.getByTestId("source-save").click(); // original
    await expect(page.getByTestId("source-state")).toContainText("not yet signed off");
    await expect(page.getByTestId("problem-publish")).toBeDisabled(); // originality not confirmed
    await page.getByTestId("originality").check();
    await page.getByTestId("problem-publish").click();
    await expect(page.getByRole("alert").filter({ hasText: "not verified" })).toBeVisible();
    await expect(page.getByTestId("problem-status")).toHaveText("draft");
  });

  test("two editors: the second save of the details is rejected", async ({ browser }) => {
    const a = await newPage(browser, "admin");
    const b = await newPage(browser, "admin");
    await a.goto(problemUrl);
    await b.goto(problemUrl);
    await a.locator('input[name="detail-title"]').fill(`${title} (A)`);
    await a.getByTestId("details-save").click();
    await expect(a.getByRole("status").filter({ hasText: "Details saved" })).toBeVisible();
    await b.locator('input[name="detail-title"]').fill(`${title} (B)`);
    await b.getByTestId("details-save").click();
    await expect(b.getByRole("alert").filter({ hasText: "changed by someone else" })).toBeVisible();
  });
});

test.describe("scoped problem setter", () => {
  test("an assigned setter edits that problem, cannot publish, and sees no other problem", async ({
    browser,
  }) => {
    const admin = await newPage(browser, "superadmin");
    const setter = await newPage(browser, "problemsetter");
    const headers = {
      origin: APP_ORIGIN,
      "x-csrf-token": await csrfToken(admin.request),
      "content-type": "application/json",
    };
    const found = await (await admin.request.get("/api/proxy/admin/users?q=problemsetter")).json();
    const id = found.items.find((u: { username: string }) => u.username === "problemsetter").id;
    const problemId = problemUrl.split("/").pop();
    const grant = await admin.request.post(`/api/proxy/admin/users/${id}/grants`, {
      headers,
      data: { permission: "problem.edit", resourceType: "problem", resourceId: problemId },
    });
    expect(grant.status()).toBe(201);

    await setter.goto("/admin/problems");
    await expect(setter.getByTestId("assigned-problems").locator("li")).toHaveCount(1);
    await setter.goto(problemUrl);
    await expect(setter.getByTestId("problem-status")).toBeVisible();
    await expect(setter.getByLabel("Test 2 expected")).toHaveValue(HIDDEN); // owns this problem's tests
    await expect(setter.getByText("Publishing is done by an admin.")).toBeVisible();
    await expect(setter.getByTestId("problem-publish")).toHaveCount(0);

    const others = await (await admin.request.get("/api/proxy/admin/problems")).json();
    const otherId = others.find((p: { id: string }) => p.id !== problemId)?.id;
    if (otherId) expect((await setter.goto(`/admin/problems/${otherId}`))?.status()).toBe(404);
  });
});
