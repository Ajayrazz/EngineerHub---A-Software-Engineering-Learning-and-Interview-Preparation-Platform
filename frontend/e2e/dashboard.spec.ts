import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** The learner dashboard, fed asynchronously from lesson/course events (API -> outbox -> RabbitMQ -> analytics). */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const slug = `dash-${stamp}`;

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
  return async (method: "post" | "put", path: string, data?: unknown) => {
    const r = await request[method](`/api/proxy/${path}`, { headers, data });
    expect(r.status(), `${method} ${path}`).toBeLessThan(300);
    return r.json();
  };
}

const number = async (page: Page, testId: string) =>
  Number(await page.getByTestId(testId).innerText());

test.beforeAll(async ({ browser }) => {
  const manager = await newPage(browser, "contentmanager");
  const call = await authoring(manager.request);
  const course = await call("post", "admin/courses", {
    slug,
    title: `Dashboard ${stamp}`,
    description: "d",
    difficulty: "beginner",
    accessTier: "free",
  });
  const base = `admin/courses/${course.id}`;
  const section = (await call("post", `${base}/sections`, { title: "S" })).sections[0].id;
  for (const s of ["one", "two"]) {
    const l = await call("post", `${base}/lessons`, {
      sectionId: section,
      slug: s,
      title: `Lesson ${s}`,
    });
    await call("put", `${base}/lessons/${l.id}/blocks`, {
      expectedVersion: l.version,
      blocks: [{ type: "markdown", data: { text: `Body ${s}` } }],
    });
    await call("put", `${base}/lessons/${l.id}/source`, { kind: "original" });
    await call("post", `${base}/lessons/${l.id}/publish`, { originalityConfirmed: true });
  }
  await call("post", `${base}/publish`);
  await manager.context().close();
});

test("the dashboard needs a login", async ({ browser }) => {
  const page = await newPage(browser);
  await page.goto("/dashboard");
  await expect(page.locator("#username")).toBeVisible();
});

test("a learner who has done nothing sees an honest empty state", async ({ browser }) => {
  const page = await newPage(browser, "moderator"); // never completes a lesson in any spec
  await page.goto("/dashboard");
  await expect(page.getByTestId("lessons-total")).toHaveText("0");
  await expect(page.getByTestId("courses-total")).toHaveText("0");
  await expect(page.getByTestId("streak-current")).toHaveText("0");
  await expect(page.getByTestId("streak-note")).toContainText("start a streak");
  await expect(page.getByTestId("problems-note")).toContainText("online judge");
  expect(await page.getByTestId("activity-cell").count()).toBeGreaterThan(100); // the grid renders (16 weeks)
  const counts = await page
    .getByTestId("activity-cell")
    .evaluateAll((cells) => cells.map((c) => c.getAttribute("data-count")));
  expect(new Set(counts)).toEqual(new Set(["0"]));
});

test("completing lessons updates the streak, totals and today's cell", async ({ browser }) => {
  const page = await newPage(browser, "learner");
  await page.goto("/dashboard");
  const lessonsBefore = await number(page, "lessons-total");
  const coursesBefore = await number(page, "courses-total");

  for (const s of ["one", "two"]) {
    await page.goto(`/courses/${slug}/lessons/${s}`);
    await page.getByTestId("lesson-complete").click();
    await expect(page.getByTestId("lesson-complete")).toContainText("Completed");
  }

  // the dashboard is eventually consistent: it is fed by events through RabbitMQ
  await expect
    .poll(
      async () => {
        await page.goto("/dashboard");
        return [await number(page, "lessons-total"), await number(page, "courses-total")];
      },
      { timeout: 30_000, intervals: [500, 1000, 2000] },
    )
    .toEqual([lessonsBefore + 2, coursesBefore + 1]);

  expect(await number(page, "streak-current")).toBeGreaterThanOrEqual(1);
  await expect(page.getByTestId("streak-note")).toContainText("You studied today");
  const cells = page.getByTestId("activity-cell");
  const last = cells.last();
  expect(Number(await last.getAttribute("data-count"))).toBeGreaterThanOrEqual(2); // today is the last non-future cell
});

test("un-marking and completing the same lesson again does not inflate the numbers", async ({
  browser,
}) => {
  const page = await newPage(browser, "learner");
  await page.goto("/dashboard");
  const before = await number(page, "lessons-total");
  await page.goto(`/courses/${slug}/lessons/one`);
  await page.getByTestId("lesson-complete").click(); // undo
  await expect(page.getByTestId("lesson-complete")).toHaveText("Mark as complete");
  await page.getByTestId("lesson-complete").click(); // complete again
  await expect(page.getByTestId("lesson-complete")).toContainText("Completed");
  await page.waitForTimeout(3000); // give the (ignored) events time to be consumed
  await page.goto("/dashboard");
  expect(await number(page, "lessons-total")).toBe(before);
});

test("another user's activity is not visible", async ({ browser }) => {
  const page = await newPage(browser, "moderator");
  await page.goto("/dashboard");
  await expect(page.getByTestId("lessons-total")).toHaveText("0");
});
