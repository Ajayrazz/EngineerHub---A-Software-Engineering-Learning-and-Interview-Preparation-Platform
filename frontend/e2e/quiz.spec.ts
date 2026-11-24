import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** Quizzes in lessons: authored in the real editor, published, delivered without the key, graded on the server. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const courseSlug = `e2e-quiz-${stamp}`;
const lessonSlug = "quiz-lesson";
const EXPLANATION = `SECRET-EXPLANATION-${stamp}`;

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

test("a content manager authors and publishes a quiz in the lesson editor", async ({ browser }) => {
  const page = await newPage(browser, "contentmanager");
  const call = await api(page.request);
  const course = await call("post", "admin/courses", {
    slug: courseSlug,
    title: `Quiz ${stamp}`,
    description: "d",
    difficulty: "beginner",
    accessTier: "free",
  });
  courseId = course.id;
  const section = (await call("post", `admin/courses/${courseId}/sections`, { title: "S" }))
    .sections[0].id;
  const lesson = await call("post", `admin/courses/${courseId}/lessons`, {
    sectionId: section,
    slug: lessonSlug,
    title: "Quiz lesson",
  });
  lessonId = lesson.id;

  await page.goto(`/admin/courses/${courseId}/lessons/${lessonId}`);
  await page.getByTestId("add-quiz").click();
  const save = page.getByTestId("blocks-save");

  // an unfinished quiz cannot be saved, and the editor says why
  await expect(page.getByTestId("block-problems")).toContainText("write the question");

  await page.getByLabel("Block 1, question 1 text").fill("Which operation is O(1)?");
  await page
    .getByLabel("Block 1, question 1 option 1 text")
    .fill("Reading an array element by index");
  await page.getByLabel("Block 1, question 1 option 2 text").fill("Searching an unsorted list");
  await page.getByLabel("Block 1, question 1 explanation").fill(EXPLANATION);
  await expect(page.getByTestId("block-problems")).toHaveCount(0);

  // a second question: multiple choice with two right answers
  await page.getByLabel("Block 1 add question").click();
  await page.getByLabel("Block 1, question 2 type").selectOption("multi");
  await page.getByLabel("Block 1, question 2 text").fill("Which sorts are O(n log n) on average?");
  await page.getByLabel("Block 1, question 2 option 1 text").fill("Merge sort");
  await page.getByLabel("Block 1, question 2 option 2 text").fill("Bubble sort");
  await page.getByLabel("Block 1, question 2 add option").click();
  await page.getByLabel("Block 1, question 2 option 3 text").fill("Heap sort");
  await page.getByLabel("Block 1, question 2 option 1 is correct").check(); // already the default answer
  await page.getByLabel("Block 1, question 2 option 3 is correct").check();
  await expect(page.getByTestId("block-problems")).toHaveCount(0);

  await save.click();
  await expect(page.getByRole("status").filter({ hasText: "Content saved" })).toBeVisible();

  // the key survives a reload (authors see it), then the lesson is published through the API
  await page.reload();
  await expect(page.getByLabel("Block 1, question 1 explanation")).toHaveValue(EXPLANATION);
  await expect(page.getByLabel("Block 1, question 2 option 3 is correct")).toBeChecked();
  await call("put", `admin/courses/${courseId}/lessons/${lessonId}/source`, { kind: "original" });
  await call("post", `admin/courses/${courseId}/lessons/${lessonId}/publish`, {
    originalityConfirmed: true,
  });
  await call("post", `admin/courses/${courseId}/publish`);
  await page.context().close();
});

test("a learner takes the quiz: the key is never in the page, grading is on the server", async ({
  browser,
}) => {
  const learner = await newPage(browser, "learner");
  const response = await learner.goto(`/courses/${courseSlug}/lessons/${lessonSlug}`);
  expect(response?.status()).toBe(200);
  const html = await learner.content();
  expect(html).not.toContain(EXPLANATION); // neither the explanation nor the key is in what the browser got
  expect(html).not.toContain('"correct"');

  const quiz = learner.getByTestId("quiz");
  await expect(quiz).toBeVisible();
  await expect(quiz.getByTestId("quiz-check")).toBeDisabled(); // every question must be answered first

  // first attempt: question 1 wrong, question 2 right
  const q1 = quiz.getByTestId("quiz-question").nth(0);
  const q2 = quiz.getByTestId("quiz-question").nth(1);
  await q1.getByLabel("Searching an unsorted list").check();
  await q2.getByLabel("Merge sort").check();
  await q2.getByLabel("Heap sort").check();
  await quiz.getByTestId("quiz-check").click();
  await expect(quiz.getByTestId("quiz-score")).toHaveText("1 of 2 correct (50%)");
  await expect(q1.getByTestId("quiz-feedback")).toContainText("Not quite.");
  await expect(q1.getByTestId("quiz-feedback")).toContainText(EXPLANATION); // revealed only after grading
  await expect(q1.locator('[data-state="wrong"]')).toContainText("Searching an unsorted list");
  await expect(q1.locator('[data-state="missed"]')).toContainText(
    "Reading an array element by index",
  );
  await expect(q2.getByTestId("quiz-feedback")).toContainText("Correct.");

  // second attempt: everything right
  await quiz.getByTestId("quiz-retry").click();
  await expect(quiz.getByTestId("quiz-check")).toBeDisabled();
  await quiz
    .getByTestId("quiz-question")
    .nth(0)
    .getByLabel("Reading an array element by index")
    .check();
  await quiz.getByTestId("quiz-question").nth(1).getByLabel("Merge sort").check();
  await quiz.getByTestId("quiz-question").nth(1).getByLabel("Heap sort").check();
  await quiz.getByTestId("quiz-check").click();
  await expect(quiz.getByTestId("quiz-score")).toHaveText("2 of 2 correct (100%)");
  await expect(quiz).toContainText("Attempt 2");
  await learner.context().close();
});

test("an anonymous reader sees the questions but cannot be graded", async ({ browser }) => {
  const anon = await newPage(browser);
  await anon.goto(`/courses/${courseSlug}/lessons/${lessonSlug}`);
  const quiz = anon.getByTestId("quiz");
  await expect(quiz).toBeVisible();
  await expect(quiz.getByTestId("quiz-check")).toHaveCount(0);
  await expect(quiz.getByRole("link", { name: "Log in" })).toBeVisible();
  expect(await anon.content()).not.toContain(EXPLANATION);
  const r = await anon.request.post(
    `/api/proxy/lessons/${lessonId}/quizzes/${crypto.randomUUID()}/attempts`,
    {
      data: { answers: [] },
    },
  );
  expect(r.status()).toBe(401); // the BFF refuses a session-less caller before the API is even asked
  await anon.context().close();
});
