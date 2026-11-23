import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** The learner-facing reader: public pages, TOC, locking, enrolment, progress and resume. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const slug = `reader-${stamp}`;
const title = `Reader ${stamp}`;
const SECRET = `PREMIUM-SECRET-${stamp}`;

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
  const call = async (method: "post" | "put", path: string, data?: unknown) => {
    const r = await request[method](`/api/proxy/${path}`, { headers, data });
    expect(r.status(), `${method} ${path}`).toBeLessThan(300);
    return r.status() === 204 ? null : r.json();
  };
  return call;
}

test.beforeAll(async ({ browser }) => {
  const manager = await newPage(browser, "contentmanager");
  const call = await authoring(manager.request);
  const course = await call("post", "admin/courses", {
    slug,
    title,
    description: "A course written by the e2e suite.",
    difficulty: "beginner",
    accessTier: "free",
  });
  const base = `admin/courses/${course.id}`;
  const withSection = await call("post", `${base}/sections`, { title: "Getting started" });
  const section = withSection.sections[0].id;

  async function lesson(lslug: string, ltitle: string, tier: string, markdown: string) {
    const l = await call("post", `${base}/lessons`, {
      sectionId: section,
      slug: lslug,
      title: ltitle,
      accessTier: tier,
    });
    await call("put", `${base}/lessons/${l.id}/blocks`, {
      expectedVersion: l.version,
      blocks: [
        { type: "markdown", data: { text: markdown } },
        { type: "code", data: { language: "java", code: "int x = 42;", caption: "Example" } },
        { type: "callout", data: { kind: "tip", text: "A friendly tip." } },
      ],
    });
    await call("put", `${base}/lessons/${l.id}/source`, { kind: "original" });
    await call("post", `${base}/lessons/${l.id}/publish`, { originalityConfirmed: true });
  }
  await lesson(
    "first-steps",
    "First steps",
    "free",
    '# First steps\n\nIntro text.\n\n## Setup\n\nInstall things.\n\n## Usage\n\nUse things.\n\n```md\n## not a heading\n```\n\n<img src=x onerror="window.__pwned=1">',
  );
  await lesson("second-steps", "Second steps", "free", "## Going on\n\nMore text.");
  await lesson("premium-deep-dive", "Premium deep dive", "premium", `## Secret\n\n${SECRET}`);
  await call("post", `${base}/publish`);
  await manager.context().close();
});

test.describe("anonymous reading", () => {
  test("browse, open a course and read a free lesson with navigation and an on-page TOC", async ({
    browser,
  }) => {
    const page = await newPage(browser);
    await page.goto(`/courses?q=${encodeURIComponent(title)}`);
    await expect(page.getByTestId("course-card")).toHaveCount(1);
    await page.getByRole("link", { name: title }).click();
    await expect(page.getByTestId("course-title")).toHaveText(title);
    await expect(page).toHaveTitle(new RegExp(`${title}`));
    await expect(page.getByTestId("outline-lesson")).toHaveCount(3);
    await expect(page.getByTestId("outline").getByText("premium", { exact: true })).toBeVisible();

    await page.getByTestId("start-reading").click();
    await expect(page.getByTestId("lesson-title")).toHaveText("First steps");
    await expect(page.getByTestId("lesson-body")).toContainText("Install things.");
    await expect(
      page.getByTestId("lesson-body").locator('code[data-language="java"]'),
    ).toContainText("int x = 42;");
    await expect(page.getByTestId("lesson-body").locator('aside[data-kind="tip"]')).toContainText(
      "A friendly tip.",
    );

    // the TOC lists the real headings (not the one inside the code fence) and links land on them
    const toc = page.getByTestId("toc");
    await expect(toc.locator("a")).toHaveText(["First steps", "Setup", "Usage"]);
    await toc.getByRole("link", { name: "Usage" }).click();
    await expect(page).toHaveURL(/#b0-usage$/);
    await expect(page.locator("#b0-usage")).toBeInViewport();

    // previous / next
    await expect(page.getByTestId("prev")).toHaveCount(0);
    await page.getByTestId("next").click();
    await expect(page.getByTestId("lesson-title")).toHaveText("Second steps");
    await expect(page.getByTestId("prev")).toContainText("First steps");
    await expect(page.getByText("Log in").first()).toBeVisible(); // progress needs an account
    await expect(page.getByTestId("lesson-complete")).toHaveCount(0);
  });

  test("hostile markup in a lesson is shown as text and never runs", async ({ browser }) => {
    const page = await newPage(browser);
    await page.goto(`/courses/${slug}/lessons/first-steps`);
    await expect(page.getByTestId("lesson-body")).toContainText("<img src=x");
    await expect(page.getByTestId("lesson-body").locator("img")).toHaveCount(0);
    expect(
      await page.evaluate(() => (window as unknown as { __pwned?: number }).__pwned),
    ).toBeUndefined();
  });

  test("a premium lesson shows a locked panel and never sends its body", async ({ browser }) => {
    const page = await newPage(browser);
    const response = await page.goto(`/courses/${slug}/lessons/premium-deep-dive`);
    expect(response?.status()).toBe(200);
    await expect(page.getByTestId("locked")).toBeVisible();
    await expect(page.getByTestId("lesson-title")).toHaveText("Premium deep dive"); // the title is public
    expect(await page.content()).not.toContain(SECRET);
  });

  test("unknown courses and lessons are 404s", async ({ browser }) => {
    const page = await newPage(browser);
    for (const path of [
      `/courses/does-not-exist`,
      `/courses/${slug}/lessons/nope`,
      `/courses/Bad_Slug`,
    ]) {
      expect((await page.goto(path))?.status(), path).toBe(404);
    }
  });
});

test.describe("learner progress", () => {
  test("enrol, read, mark complete, resume, and see it under My courses", async ({ browser }) => {
    const page = await newPage(browser, "learner");
    await page.goto(`/courses/${slug}`);
    await page.getByTestId("enroll").click();
    await page.waitForURL(new RegExp(`/courses/${slug}/lessons/first-steps$`));
    await expect(page.getByTestId("lesson-complete")).toHaveText("Mark as complete");

    await page.getByTestId("lesson-complete").click();
    await expect(page.getByTestId("lesson-complete")).toContainText("Completed");
    await expect(page.getByTestId("sidebar-lesson").first()).toContainText("✓");

    // resume: the course page now says "Continue" and goes to the next unfinished lesson
    await page.goto(`/courses/${slug}`);
    await expect(page.getByTestId("course-progress")).toHaveText("1/3 lessons completed");
    await expect(page.getByTestId("enroll")).toHaveText("Continue");
    await page.getByTestId("enroll").click();
    await page.waitForURL(new RegExp(`/lessons/second-steps$`));

    await page.goto("/my-courses");
    const row = page.getByTestId("enrollment").filter({ hasText: title });
    await expect(row).toContainText("1/3 lessons");
    await expect(row.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "33");

    // undo
    await page.goto(`/courses/${slug}/lessons/first-steps`);
    await page.getByTestId("lesson-complete").click();
    await expect(page.getByTestId("lesson-complete")).toHaveText("Mark as complete");
    await page.goto(`/courses/${slug}`);
    await expect(page.getByTestId("course-progress")).toHaveText("0/3 lessons completed");
  });

  test("a learner without premium access sees the locked panel; staff see the lesson", async ({
    browser,
  }) => {
    const learner = await newPage(browser, "learner");
    await learner.goto(`/courses/${slug}/lessons/premium-deep-dive`);
    await expect(learner.getByTestId("locked")).toBeVisible();
    expect(await learner.content()).not.toContain(SECRET);

    const staff = await newPage(browser, "admin");
    await staff.goto(`/courses/${slug}/lessons/premium-deep-dive`);
    await expect(staff.getByTestId("lesson-body")).toContainText(SECRET);
    await expect(staff.getByTestId("locked")).toHaveCount(0);
  });

  test("My courses needs a login and is empty for a new learner", async ({ browser }) => {
    const anon = await newPage(browser);
    await anon.goto("/my-courses");
    await expect(anon.locator("#username")).toBeVisible(); // redirected to the login page
    const moderator = await newPage(browser, "moderator");
    await moderator.goto("/my-courses");
    await expect(moderator.getByTestId("no-enrollments")).toBeVisible();
  });

  test("progress cannot be written without the CSRF token", async ({ browser }) => {
    const page = await newPage(browser, "learner");
    const r = await page.request.put(
      "/api/proxy/lessons/" + "00000000-0000-4000-8000-000000000000" + "/progress",
      {
        headers: { origin: APP_ORIGIN, "content-type": "application/json" },
        data: { status: "completed" },
      },
    );
    expect(r.status()).toBe(403);
  });
});
