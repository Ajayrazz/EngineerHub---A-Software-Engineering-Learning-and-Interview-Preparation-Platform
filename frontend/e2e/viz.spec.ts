import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** Visualizations in lessons: authored in the real editor, published, played by readers. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const courseSlug = `e2e-viz-${stamp}`;
const lessonSlug = "viz-lesson";

let courseId = "";
let lessonId = "";

async function newPage(browser: Browser, username?: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  if (username) await login(page, username, "/account");
  return page;
}

async function api(request: APIRequestContext) {
  const headers = {
    origin: APP_ORIGIN,
    "x-csrf-token": await csrfToken(request),
    "content-type": "application/json",
  };
  return async (method: "get" | "post" | "put", path: string, data?: unknown) => {
    const r = await request[method](`/api/proxy/${path}`, { headers, data });
    expect(r.status(), `${method} ${path}`).toBeLessThan(300);
    return r.json();
  };
}

test("a content manager adds a visualization and gets a live, validated preview", async ({
  browser,
}) => {
  const page = await newPage(browser, "contentmanager");
  const call = await api(page.request);
  const course = await call("post", "admin/courses", {
    slug: courseSlug,
    title: `Viz ${stamp}`,
    description: "d",
    difficulty: "beginner",
    accessTier: "free",
  });
  courseId = course.id;
  const section = (await call("post", `admin/courses/${courseId}/sections`, { title: "S" }))
    .sections[0].id;
  lessonId = (
    await call("post", `admin/courses/${courseId}/lessons`, {
      sectionId: section,
      slug: lessonSlug,
      title: "Viz lesson",
    })
  ).id;

  await page.goto(`/admin/courses/${courseId}/lessons/${lessonId}`);
  await page.getByTestId("add-viz").click();
  const editor = page.getByTestId("viz-editor");
  await expect(editor.getByTestId("viz-note")).toContainText("Start with the unsorted list"); // a working example from the start
  await expect(page.getByTestId("blocks-save")).toBeEnabled();

  // another algorithm brings its own example and value field
  await page.getByLabel("Block 1 algorithm").selectOption({ label: "Binary search" });
  await expect(page.getByLabel("Block 1 visualization value")).toHaveValue("23");
  await expect(editor.getByTestId("viz-note")).toContainText("Search for 23");

  // an unsorted list is explained and blocks saving
  await page.getByLabel("Block 1 visualization input").fill("9, 1, 5");
  await expect(editor.getByTestId("viz-editor-problem")).toContainText("sorted");
  await expect(page.getByTestId("block-problems")).toContainText("sorted");
  await expect(page.getByTestId("blocks-save")).toBeDisabled();
  await expect(editor.getByTestId("viz-player")).toHaveCount(0);

  await page.getByLabel("Block 1 visualization input").fill("1, 3, 5, 7, 9");
  await page.getByLabel("Block 1 visualization value").fill("7");
  await expect(editor.getByTestId("viz-note")).toContainText("Search for 7");
  await expect(page.getByTestId("blocks-save")).toBeEnabled();

  await page.getByTestId("blocks-save").click();
  await expect(page.getByRole("status").filter({ hasText: "Content saved" })).toBeVisible();
  await page.reload();
  await expect(page.getByLabel("Block 1 algorithm")).toHaveValue("binary-search");
  await expect(page.getByLabel("Block 1 visualization input")).toHaveValue("1, 3, 5, 7, 9");

  await call("put", `admin/courses/${courseId}/lessons/${lessonId}/source`, { kind: "original" });
  await call("post", `admin/courses/${courseId}/lessons/${lessonId}/publish`, {
    originalityConfirmed: true,
  });
  await call("post", `admin/courses/${courseId}/publish`);
  await page.context().close();
});

test("a reader steps, plays, scrubs and restarts the visualization with the mouse and the keyboard", async ({
  browser,
}) => {
  const anon = await newPage(browser);
  await anon.goto(`/courses/${courseSlug}/lessons/${lessonSlug}`);
  const viz = anon.getByTestId("viz");
  await expect(viz).toBeVisible();
  await expect(viz.getByTestId("viz-note")).toContainText("Search for 7 in the whole list");
  await expect(viz.getByTestId("viz-position")).toHaveText(/Step 1 of \d+/);
  const total = Number(
    ((await viz.getByTestId("viz-position").textContent()) ?? "").match(/of (\d+)/)![1],
  );
  await expect(viz.getByTestId("viz-prev")).toBeDisabled();

  // stepping forward changes the note, the position and the highlighted pseudocode line
  const firstLine = await viz
    .locator('[data-testid="viz-pseudo"] li[data-active="true"]')
    .textContent();
  await viz.getByTestId("viz-next").click();
  await expect(viz.getByTestId("viz-position")).toHaveText(`Step 2 of ${total}`);
  await expect(viz.getByTestId("viz-note")).toContainText("Look at the middle item, 5");
  await expect(viz.locator('[data-testid="viz-pseudo"] li[data-active="true"]')).not.toHaveText(
    firstLine ?? "",
  );
  await expect(viz.locator('[data-testid="viz-pseudo"] li[aria-current="step"]')).toHaveCount(1);

  // the keyboard works on the focused player
  await viz.getByTestId("viz-player").focus();
  await anon.keyboard.press("ArrowRight");
  await expect(viz.getByTestId("viz-position")).toHaveText(`Step 3 of ${total}`);
  await anon.keyboard.press("ArrowLeft");
  await expect(viz.getByTestId("viz-position")).toHaveText(`Step 2 of ${total}`);

  // the scrubber jumps to the end, where the answer is shown
  await viz.getByTestId("viz-scrub").fill(String(total - 1));
  await expect(viz.getByTestId("viz-position")).toHaveText(`Step ${total} of ${total}`);
  await expect(viz.getByTestId("viz-note")).toContainText("found at position 3");
  await expect(viz.getByTestId("viz-next")).toBeDisabled();
  await expect(viz.locator('[data-mark="found"]')).toHaveCount(1);

  // restart, then play at double speed: it advances by itself and a pause holds the position
  await viz.getByTestId("viz-restart").click();
  await expect(viz.getByTestId("viz-position")).toHaveText(`Step 1 of ${total}`);
  await viz.getByTestId("viz-speed").selectOption("2");
  await viz.getByTestId("viz-play").click();
  await expect(viz.getByTestId("viz-position")).not.toHaveText(`Step 1 of ${total}`);
  await viz.getByTestId("viz-play").click(); // pause
  const held = await viz.getByTestId("viz-position").textContent();
  await anon.waitForTimeout(1200);
  await expect(viz.getByTestId("viz-position")).toHaveText(held ?? "");

  // playing from the last step starts again
  await viz.getByTestId("viz-scrub").fill(String(total - 1));
  await expect(viz.getByTestId("viz-play")).toContainText("Replay");
  await viz.getByTestId("viz-play").click();
  await expect(viz.getByTestId("viz-position")).not.toHaveText(`Step ${total} of ${total}`);
  await anon.context().close();
});

test("a reader can try their own input within the same limits, and go back to the example", async ({
  browser,
}) => {
  const anon = await newPage(browser);
  await anon.goto(`/courses/${courseSlug}/lessons/${lessonSlug}`);
  const viz = anon.getByTestId("viz");
  await viz.getByTestId("viz-custom-open").click();

  await viz.locator('input[name="viz-input"]').fill("5, 1, 4");
  await viz.locator('input[name="viz-param"]').fill("4");
  await viz.getByTestId("viz-custom-apply").click();
  await expect(viz.getByTestId("viz-custom-error")).toContainText("sorted"); // the example's list is unchanged until the input is valid
  await expect(viz.getByTestId("viz-note")).toContainText("Search for 7");

  await viz.locator('input[name="viz-input"]').fill("2, 4, 6, 8");
  await viz.locator('input[name="viz-param"]').fill("6");
  await viz.getByTestId("viz-custom-apply").click();
  await expect(viz.getByTestId("viz-note")).toContainText("Search for 6");
  await expect(viz.getByTestId("viz-position")).toHaveText(/Step 1 of/);

  await viz.getByTestId("viz-custom-open").click();
  await viz.locator('input[name="viz-input"]').fill("<script>alert(1)</script>");
  await viz.getByTestId("viz-custom-apply").click();
  await expect(viz.getByTestId("viz-custom-error")).toBeVisible();
  expect(await anon.evaluate(() => (window as unknown as { __x?: number }).__x)).toBeUndefined();
  await viz.getByRole("button", { name: "Cancel" }).click();

  await viz.getByTestId("viz-custom-reset").click();
  await expect(viz.getByTestId("viz-note")).toContainText("Search for 7");
  await anon.context().close();
});
