import { expect, test, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** The course/lesson CMS: authoring, preview safety, publish gates, concurrency, scoped instructors. */

test.describe.configure({ mode: "serial" });

const API = process.env.API_BASE_URL ?? "http://localhost:8080";
const stamp = Date.now();
const courseTitle = `E2E CMS ${stamp}`;
const courseSlug = `e2e-cms-${stamp}`;
const lessonSlug = "intro-lesson";

let courseUrl = "";
let lessonUrl = "";

async function newPage(browser: Browser, username: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await login(page, username, "/account");
  return page;
}

const publicLesson = (request: import("@playwright/test").APIRequestContext) =>
  request.get(`${API}/api/v1/courses/${courseSlug}/lessons/${lessonSlug}`);

test.beforeAll(async ({ browser }) => {
  // start from a clean slate: the instructor has no course assignments left over from earlier runs
  const admin = await newPage(browser, "superadmin");
  await (await newPage(browser, "instructor")).context().close(); // provisions the local user
  const headers = { origin: APP_ORIGIN, "x-csrf-token": await csrfToken(admin.request) };
  const found = await (await admin.request.get("/api/proxy/admin/users?q=instructor")).json();
  const id = found.items.find((u: { username: string }) => u.username === "instructor").id;
  const grants = await (await admin.request.get(`/api/proxy/admin/users/${id}/grants`)).json();
  for (const g of grants) {
    await admin.request.delete(`/api/proxy/admin/users/${id}/grants/${g.id}`, { headers });
  }
  await admin.context().close();
});

test.describe("access", () => {
  test("learners and non-editing staff get a 404", async ({ browser }) => {
    for (const user of ["learner", "moderator"]) {
      const page = await newPage(browser, user);
      expect((await page.goto("/admin/courses"))?.status(), user).toBe(404);
    }
  });

  test("an unassigned instructor sees an empty list, not the course catalog", async ({
    browser,
  }) => {
    const page = await newPage(browser, "instructor");
    expect((await page.goto("/admin/courses"))?.status()).toBe(200);
    await expect(page.getByText("You have not been assigned any courses yet.")).toBeVisible();
    await expect(page.getByTestId("courses-table")).toHaveCount(0);
  });
});

test.describe("authoring", () => {
  test("create a course, a section and a lesson", async ({ browser }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto("/admin/courses");
    await page.locator('input[name="course-title"]').fill(courseTitle);
    await expect(page.locator('input[name="course-slug"]')).toHaveValue(courseSlug);
    await page.locator('textarea[name="course-description"]').fill("Written in the e2e suite.");
    await page.getByTestId("course-create").click();
    await page.waitForURL(/\/admin\/courses\/[0-9a-f-]{36}$/);
    courseUrl = new URL(page.url()).pathname;
    await expect(page.getByTestId("course-status")).toHaveText("draft");

    await page.locator('input[name="new-section"]').fill("Basics");
    await page.getByTestId("section-add").click();
    await expect(page.getByTestId("section-box")).toHaveCount(1);

    await page.locator('input[name="lesson-title"]').fill("Intro lesson");
    await expect(page.locator('input[name="lesson-slug"]')).toHaveValue(lessonSlug);
    await page.getByTestId("lesson-add").click();
    await page.waitForURL(/\/lessons\/[0-9a-f-]{36}$/);
    lessonUrl = new URL(page.url()).pathname;
    await expect(page.getByTestId("lesson-status")).toHaveText("draft");
  });

  test("the live preview shows markdown but never executes or renders hostile content", async ({
    browser,
  }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(lessonUrl);
    await page.getByTestId("add-markdown").click();
    await page
      .getByLabel("Block 1 markdown")
      .fill(
        '# Hello\n\nSome **bold** text.\n\n<img src=x onerror="window.__pwned=1">\n\n[evil](javascript:window.__pwned=2)\n\n![tracker](https://tracker.example/p.png)',
      );
    const preview = page.getByTestId("block-live-preview");
    await expect(preview.locator("h1")).toHaveText("Hello");
    await expect(preview.locator("strong")).toHaveText("bold");
    await expect(preview.locator("img")).toHaveCount(0); // neither raw HTML nor markdown images
    await expect(preview.getByText("<img src=x")).toBeVisible(); // shown as literal text
    const hrefs = await preview
      .locator("a")
      .evaluateAll((as) => as.map((a) => a.getAttribute("href")));
    expect(hrefs.join()).not.toMatch(/javascript:/i);
    expect(
      await page.evaluate(() => (window as unknown as { __pwned?: number }).__pwned),
    ).toBeUndefined();
  });

  test("validation blocks saving until the content is valid", async ({ browser }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(lessonUrl);
    await page.getByTestId("add-markdown").click(); // empty block
    await expect(page.getByTestId("block-problems")).toContainText("empty");
    await expect(page.getByTestId("blocks-save")).toBeDisabled();
    await page.getByRole("button", { name: "Remove block 1" }).click();
    await expect(page.getByTestId("block-problems")).toHaveCount(0);
  });

  test("write content (markdown, code, callout), save the draft, and it persists", async ({
    browser,
  }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(lessonUrl);
    await page.getByTestId("add-markdown").click();
    await page.getByLabel("Block 1 markdown").fill("# Welcome\n\nVERSION-ONE of the lesson.");
    await page.getByTestId("add-code").click();
    await page.getByLabel("Block 2 code").fill("int x = 1;");
    await page.getByTestId("add-callout").click();
    await page.getByLabel("Block 3 callout text").fill("Remember this.");
    await page.getByTestId("blocks-save").click();
    await expect(page.getByRole("status")).toContainText("Content saved");
    await expect(page.getByTestId("lesson-preview")).toContainText("VERSION-ONE");
    await page.reload();
    await expect(page.getByLabel("Block 1 markdown")).toHaveValue(/VERSION-ONE/);
    await expect(page.getByLabel("Block 2 code")).toHaveValue("int x = 1;");
  });

  test("publishing needs a recorded source and the originality confirmation", async ({
    browser,
  }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(lessonUrl);
    await expect(page.getByTestId("lesson-publish")).toBeDisabled(); // not confirmed yet
    await page.getByTestId("originality").check();
    await page.getByTestId("lesson-publish").click();
    await expect(page.getByRole("alert").filter({ hasText: "source" })).toBeVisible();
    await expect(page.getByTestId("lesson-status")).toHaveText("draft");

    await page.getByTestId("source-save").click(); // default kind: original
    await expect(page.getByTestId("source-state")).toContainText("not yet signed off");
    await page.getByTestId("lesson-publish").click();
    await expect(page.getByTestId("lesson-status")).toHaveText("published");
    await expect(page.getByTestId("source-state")).toContainText("Signed off");
  });

  test("a course is published after its lesson, and readers get exactly the published snapshot", async ({
    browser,
    request,
  }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(courseUrl);
    await page.getByTestId("course-publish").click();
    await expect(page.getByTestId("course-status")).toHaveText("published");

    const live = await publicLesson(request);
    expect(live.status()).toBe(200);
    expect(await live.text()).toContain("VERSION-ONE");

    // edit the draft: readers still get version one until the next publish
    await page.goto(lessonUrl);
    await page.getByLabel("Block 1 markdown").fill("# Welcome\n\nVERSION-TWO of the lesson.");
    await page.getByTestId("blocks-save").click();
    await expect(page.getByTestId("lesson-unpublished")).toBeVisible();
    expect(await (await publicLesson(request)).text()).toContain("VERSION-ONE");

    await page.getByTestId("originality").check();
    await page.getByTestId("lesson-publish").click();
    await expect(page.getByTestId("lesson-unpublished")).toHaveCount(0);
    const updated = await (await publicLesson(request)).text();
    expect(updated).toContain("VERSION-TWO");
    expect(updated).not.toContain("VERSION-ONE");
  });

  test("lessons and sections can be reordered", async ({ browser }) => {
    const page = await newPage(browser, "contentmanager");
    await page.goto(courseUrl);
    await page.locator('input[name="lesson-title"]').fill("Second lesson");
    await page.getByTestId("lesson-add").click();
    await page.waitForURL(/\/lessons\//);
    await page.goto(courseUrl);
    const titles = () => page.getByTestId("lesson-item").locator("a").allTextContents();
    expect(await titles()).toEqual(["Intro lesson", "Second lesson"]);
    await page.getByRole("button", { name: "Move lesson Second lesson up" }).click();
    await expect.poll(titles).toEqual(["Second lesson", "Intro lesson"]);
    await page.reload();
    expect(await titles()).toEqual(["Second lesson", "Intro lesson"]);
  });

  test("two editors: the second save is rejected, not silently overwriting", async ({
    browser,
  }) => {
    const a = await newPage(browser, "contentmanager");
    const b = await newPage(browser, "contentmanager");
    await a.goto(lessonUrl);
    await b.goto(lessonUrl);
    await a.getByLabel("Block 1 markdown").fill("# Welcome\n\nEDIT-FROM-A");
    await a.getByTestId("blocks-save").click();
    await expect(a.getByRole("status")).toContainText("Content saved");
    await b.getByLabel("Block 1 markdown").fill("# Welcome\n\nEDIT-FROM-B");
    await b.getByTestId("blocks-save").click();
    await expect(b.getByRole("alert").filter({ hasText: "changed by someone else" })).toBeVisible();
    await a.reload();
    await expect(a.getByLabel("Block 1 markdown")).toHaveValue(/EDIT-FROM-A/);
  });
});

test.describe("scoped instructor", () => {
  test("an instructor assigned to the course can edit it but not publish, and sees no other course", async ({
    browser,
  }) => {
    // the super admin assigns the instructor to this course (through the real admin API)
    const admin = await newPage(browser, "superadmin");
    const inst = await newPage(browser, "instructor"); // provisions the local user
    const token = await csrfToken(admin.request);
    const headers = {
      origin: APP_ORIGIN,
      "x-csrf-token": token,
      "content-type": "application/json",
    };
    const found = await (await admin.request.get("/api/proxy/admin/users?q=instructor")).json();
    const id = found.items.find((u: { username: string }) => u.username === "instructor").id;
    const courseId = courseUrl.split("/").pop();
    const grant = await admin.request.post(`/api/proxy/admin/users/${id}/grants`, {
      headers,
      data: { permission: "course.edit", resourceType: "course", resourceId: courseId },
    });
    expect(grant.status()).toBe(201);

    await inst.goto("/admin/courses");
    await expect(inst.getByTestId("assigned-courses").locator("li")).toHaveCount(1);
    await inst.goto(courseUrl);
    await expect(inst.getByTestId("course-status")).toBeVisible();
    await expect(inst.getByTestId("course-publish")).toHaveCount(0);
    await expect(inst.getByTestId("course-unpublish")).toHaveCount(0);

    await inst.goto(lessonUrl);
    await expect(inst.getByText("Publishing is done by a content manager or admin.")).toBeVisible();
    await expect(inst.getByTestId("lesson-publish")).toHaveCount(0);

    // another course is invisible to them
    const other = await (await admin.request.get("/api/proxy/admin/courses")).json();
    const otherId = other.find((c: { id: string }) => c.id !== courseId)?.id;
    if (otherId) expect((await inst.goto(`/admin/courses/${otherId}`))?.status()).toBe(404);
  });
});
