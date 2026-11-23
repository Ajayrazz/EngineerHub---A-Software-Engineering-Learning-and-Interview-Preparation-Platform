import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login, markProblemVerified } from "./helpers";

/** The public problem bank: browse, filter, read, hints, premium lock, random, bookmarks and status. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const pattern = `pat-${stamp}`;
const HIDDEN = `HIDDEN-E2E-${stamp}`;
const PREMIUM = `PREMIUM-E2E-SECRET-${stamp}`;
const slugs = {
  alpha: `alpha-${stamp}`,
  beta: `beta-${stamp}`,
  gamma: `gamma-${stamp}`,
  delta: `delta-${stamp}`,
};

async function newPage(browser: Browser, username?: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  if (username) await login(page, username, "/account");
  return page;
}

async function authoring(request: APIRequestContext) {
  const headers = {
    origin: APP_ORIGIN,
    "x-csrf-token": await csrfToken(request),
    "content-type": "application/json",
  };
  return async (method: "post" | "put" | "patch" | "get", path: string, data?: unknown) => {
    const r = await request[method](`/api/proxy/${path}`, { headers, data });
    expect(r.status(), `${method} ${path}`).toBeLessThan(300);
    return r.json();
  };
}

test.beforeAll(async ({ browser }) => {
  const admin = await newPage(browser, "admin");
  const call = await authoring(admin.request);
  const topics = await call("get", "topics");
  const topicId = (slug: string) => topics.find((t: { slug: string }) => t.slug === slug).id;
  const pat = await call("post", "admin/patterns", { slug: pattern, name: `Pattern ${stamp}` });

  async function problem(
    key: keyof typeof slugs,
    title: string,
    difficulty: string,
    tier: string,
    topic: string,
    withPattern: boolean,
    statement: string,
    publish: boolean,
  ) {
    const p = await call("post", "admin/problems", {
      slug: slugs[key],
      title,
      difficulty,
      accessTier: tier,
    });
    const base = `admin/problems/${p.id}`;
    await call("patch", base, {
      statement,
      inputFormat: "Two integers.",
      outputFormat: "One integer.",
      constraints: "small",
      hints: ["First hint: think.", "Second hint: add."],
      expectedVersion: p.version,
    });
    await call("put", `${base}/tests`, {
      tests: [
        { input: "1 2", expected: "3", sample: true, explanation: "1 + 2 = 3" },
        { input: "5 5", expected: HIDDEN, sample: false },
      ],
      expectedTestcaseVersion: 0,
    });
    await call("put", `${base}/tags`, {
      topicIds: [topicId(topic)],
      patternIds: withPattern ? [pat.id] : [],
    });
    await call("put", `${base}/source`, { kind: "original" });
    if (publish) {
      markProblemVerified(p.id);
      await call("post", `${base}/publish`, { originalityConfirmed: true });
    }
  }

  await problem(
    "alpha",
    `Alpha ${stamp}`,
    "easy",
    "free",
    "arrays",
    true,
    'Add the **two** numbers.\n\n<img src=x onerror="window.__pwned=1">\n\n[x](javascript:window.__pwned=2)',
    true,
  );
  await problem("beta", `Beta ${stamp}`, "medium", "free", "graphs", true, "Beta statement.", true);
  await problem("gamma", `Gamma ${stamp}`, "hard", "premium", "arrays", false, PREMIUM, true);
  await problem("delta", `Delta ${stamp}`, "easy", "free", "arrays", false, "draft only", false);
  await admin.context().close();
});

test.describe("anonymous browsing", () => {
  test("the list shows published problems with filters, and never drafts or hidden tests", async ({
    browser,
  }) => {
    const page = await newPage(browser);
    await page.goto(`/problems?q=${stamp}`);
    await expect(page.getByTestId("problem-row")).toHaveCount(3);
    await expect(page.getByTestId("problem-table")).not.toContainText("Delta");
    await expect(page.getByRole("columnheader", { name: "Status" })).toHaveCount(0); // signed-out: no personal columns
    expect(await page.content()).not.toContain(HIDDEN);

    await page.goto(`/problems?q=${stamp}&difficulty=hard`);
    await expect(page.getByTestId("problem-row")).toHaveCount(1);
    await expect(page.getByTestId("problem-row")).toContainText("Gamma");
    await page.goto(`/problems?q=${stamp}&topic=graphs`);
    await expect(page.getByTestId("problem-row")).toHaveCount(1);
    await expect(page.getByTestId("problem-row")).toContainText("Beta");
    await page.goto(`/problems?pattern=${pattern}`);
    await expect(page.getByTestId("problem-row")).toHaveCount(2);
    await page.goto(`/problems?q=${stamp}&sort=title`);
    await expect(page.getByTestId("problem-row").first()).toContainText("Alpha");
  });

  test("a problem page shows the statement, examples (never hidden tests) and progressive hints", async ({
    browser,
  }) => {
    const page = await newPage(browser);
    await page.goto(`/problems/${slugs.alpha}`);
    await expect(page.getByTestId("problem-title")).toHaveText(`Alpha ${stamp}`);
    const statement = page.getByTestId("statement");
    await expect(statement.locator("strong")).toHaveText("two");
    await expect(statement.locator("img")).toHaveCount(0);
    await expect(statement.getByText("<img src=x")).toBeVisible();
    expect(
      await page.evaluate(() => (window as unknown as { __pwned?: number }).__pwned),
    ).toBeUndefined();

    await expect(page.getByTestId("sample")).toHaveCount(1);
    await expect(page.getByTestId("sample")).toContainText("1 + 2 = 3");
    expect(await page.content()).not.toContain(HIDDEN);
    await expect(page.getByTestId("no-judge")).toBeVisible();
    await expect(page.getByRole("link", { name: "Log in to bookmark" })).toBeVisible();

    await expect(page.getByTestId("hints").locator("li")).toHaveCount(0);
    await page.getByTestId("hint-next").click();
    await expect(page.getByTestId("hints").locator("li")).toHaveText(["First hint: think."]);
    await page.getByTestId("hint-next").click();
    await expect(page.getByTestId("hints").locator("li")).toHaveCount(2);
    await expect(page.getByTestId("hint-next")).toHaveCount(0);
  });

  test("a premium problem is locked and its statement never reaches the page", async ({
    browser,
  }) => {
    const page = await newPage(browser);
    const response = await page.goto(`/problems/${slugs.gamma}`);
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId("locked")).toBeVisible();
    expect(await page.content()).not.toContain(PREMIUM);
  });

  test("draft and unknown problems are 404", async ({ browser }) => {
    const page = await newPage(browser);
    for (const slug of [slugs.delta, "does-not-exist", "Bad_Slug"]) {
      expect((await page.goto(`/problems/${slug}`))?.status(), slug).toBe(404);
    }
  });

  test("random picks a matching problem, or explains that nothing matches", async ({ browser }) => {
    const page = await newPage(browser);
    await page.goto(`/problems/random?pattern=${pattern}&difficulty=easy`);
    await expect(page).toHaveURL(new RegExp(`/problems/${slugs.alpha}$`));
    await page.goto(`/problems/random?pattern=${pattern}&difficulty=hard`);
    await expect(page).toHaveURL(/nomatch=1/);
    await expect(page.getByText("No problem matches those filters.")).toBeVisible();
    // hostile filter values are ignored, never reflected or forwarded
    await page.goto(`/problems/random?topic=${encodeURIComponent("../../x")}&difficulty=<script>`);
    await expect(page).toHaveURL(/\/problems\/[a-z0-9-]+$|nomatch=1/);
  });
});

test.describe("signed-in learner", () => {
  test("bookmark a problem, find it under My problems, remove it again; another user sees nothing", async ({
    browser,
  }) => {
    const page = await newPage(browser, "learner");
    await page.goto(`/problems/${slugs.alpha}`);
    await expect(page.getByTestId("problem-state")).toHaveText("—"); // not attempted: no submissions exist before the judge
    await page.getByTestId("bookmark").click();
    await expect(page.getByTestId("bookmark")).toContainText("Bookmarked");

    await page.goto("/my-problems");
    const row = page.getByTestId("problem-row").filter({ hasText: `Alpha ${stamp}` });
    await expect(row).toBeVisible();
    await expect(row).toContainText("★");
    await page.goto(`/problems?q=${stamp}`);
    await expect(page.getByRole("columnheader", { name: "Status" })).toBeVisible();
    await expect(page.getByTestId("problem-row").filter({ hasText: "Alpha" })).toContainText("★");

    const other = await newPage(browser, "moderator");
    await other.goto("/my-problems");
    await expect(other.getByTestId("no-bookmarks")).toBeVisible();

    await page.goto(`/problems/${slugs.alpha}`);
    await page.getByTestId("bookmark").click();
    await expect(page.getByTestId("bookmark")).toContainText("☆");
    await page.goto("/my-problems");
    await expect(page.getByTestId("no-bookmarks")).toBeVisible();
  });

  test("a learner without premium access is locked out, staff are not", async ({ browser }) => {
    const learner = await newPage(browser, "learner");
    await learner.goto(`/problems/${slugs.gamma}`);
    await expect(learner.getByTestId("locked")).toBeVisible();
    expect(await learner.content()).not.toContain(PREMIUM);

    const staff = await newPage(browser, "admin");
    await staff.goto(`/problems/${slugs.gamma}`);
    await expect(staff.getByTestId("statement")).toContainText(PREMIUM);
  });

  test("My problems needs a login", async ({ browser }) => {
    const anon = await newPage(browser);
    await anon.goto("/my-problems");
    await expect(anon.locator("#username")).toBeVisible();
  });

  test("bookmark writes need the CSRF token", async ({ browser }) => {
    const page = await newPage(browser, "learner");
    const r = await page.request.put(`/api/proxy/problems/${slugs.alpha}/bookmark`, {
      headers: { origin: APP_ORIGIN },
    });
    expect(r.status()).toBe(403);
  });
});
