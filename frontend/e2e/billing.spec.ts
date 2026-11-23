import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

/** Plans, prices, admin-granted access and the real entitlement gating premium content. */

test.describe.configure({ mode: "serial" });

const stamp = Date.now();
const planCode = `e2e-plan-${stamp}`;
const planName = `E2E Premium ${stamp}`;
const hiddenPlan = `E2E Hidden ${stamp}`;
const courseSlug = `bill-${stamp}`;
const SECRET = `PREMIUM-BILLING-SECRET-${stamp}`;

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

/** Revokes every running grant of the learner, so no run (or failure) can leak premium access into other specs. */
async function revokeLearnerAccess(browser: Browser) {
  const admin = await newPage(browser, "admin");
  const call = await api(admin.request);
  const found = await call("get", "admin/users?q=learner");
  const id = found.items.find((u: { username: string }) => u.username === "learner").id;
  const access = await call("get", `admin/billing/users/${id}/access`);
  for (const p of access.periods) {
    if (!p.revoked)
      await call("post", `admin/billing/access-periods/${p.id}/revoke`, {
        reason: "e2e cleanup of the billing spec",
      });
  }
  await admin.context().close();
}

test.beforeAll(async ({ browser }) => {
  await revokeLearnerAccess(browser); // the learner starts without premium access
  const admin = await newPage(browser, "admin");
  const call = await api(admin.request);
  // a premium lesson to gate
  const manager = await newPage(browser, "contentmanager");
  const m = await api(manager.request);
  const course = await m("post", "admin/courses", {
    slug: courseSlug,
    title: `Billing ${stamp}`,
    description: "d",
    difficulty: "beginner",
    accessTier: "premium",
  });
  const base = `admin/courses/${course.id}`;
  const section = (await m("post", `${base}/sections`, { title: "S" })).sections[0].id;
  const l = await m("post", `${base}/lessons`, { sectionId: section, slug: "deep", title: "Deep" });
  await m("put", `${base}/lessons/${l.id}/blocks`, {
    expectedVersion: l.version,
    blocks: [{ type: "markdown", data: { text: SECRET } }],
  });
  await m("put", `${base}/lessons/${l.id}/source`, { kind: "original" });
  await m("post", `${base}/lessons/${l.id}/publish`, { originalityConfirmed: true });
  await m("post", `${base}/publish`);
  await admin.context().close();
  await manager.context().close();
});

test.afterAll(async ({ browser }) => {
  await revokeLearnerAccess(browser);
});

test("billing administration is for billing managers only", async ({ browser }) => {
  for (const user of ["learner", "moderator", "contentmanager"]) {
    const page = await newPage(browser, user);
    expect((await page.goto("/admin/billing"))?.status(), user).toBe(404);
  }
});

test("an admin creates plans and prices; only buyable plans are shown publicly, with exact final amounts", async ({
  browser,
}) => {
  const page = await newPage(browser, "admin");
  await page.goto("/admin/billing");
  await page.locator('input[name="plan-code"]').fill(planCode);
  await page.locator('input[name="plan-name"]').fill(planName);
  await page.getByTestId("plan-create").click();
  const card = page.getByRole("region", { name: `Plan ${planName}` });
  await expect(card).toBeVisible();
  await expect(card.getByText("No prices: this plan is not shown to learners.")).toBeVisible();

  await card.getByLabel("Includes premium content (lessons and problems)").click(); // flips after the API round trip
  await expect(page.getByRole("status").filter({ hasText: "Entitlements saved" })).toBeVisible();
  await expect(card.getByLabel("Includes premium content (lessons and problems)")).toBeChecked();

  // validation: amount must be a plain rupee amount
  const amount = card.locator('input[name="price-amount"]');
  await amount.fill("29x");
  await expect(card.getByText("Enter an amount like 299 or 299.50.")).toBeVisible();
  await expect(card.getByTestId("price-add")).toBeDisabled();
  await amount.fill("299.5");
  await card.getByTestId("price-add").click();
  await expect(page.getByRole("status").filter({ hasText: "Price added" })).toBeVisible();
  await expect(card.getByTestId("price-row")).toContainText(/₹\s?299\.50 per month, one-time pass/);
  await card.getByLabel("Billing mode").selectOption("RECURRING");
  await card.getByLabel("Period").selectOption("year");
  await amount.fill("2999");
  await card.getByTestId("price-add").click();
  await expect(card.getByTestId("price-row")).toHaveCount(2);

  // a second plan without any price must stay invisible to the public
  await page.locator('input[name="plan-code"]').fill(`${planCode}-hidden`);
  await page.locator('input[name="plan-name"]').fill(hiddenPlan);
  await page.getByTestId("plan-create").click();
  await expect(page.getByRole("region", { name: `Plan ${hiddenPlan}` })).toBeVisible();

  const anon = await newPage(browser);
  await anon.goto("/pricing");
  await expect(anon.getByTestId("payments-note")).toContainText("not available yet");
  const shown = anon.getByTestId("plan").filter({ hasText: planName });
  await expect(shown).toBeVisible();
  await expect(shown.getByTestId("price")).toHaveCount(2);
  await expect(shown).toContainText(/₹\s?2,999\.00 per year, auto-renewing/);
  await expect(anon.getByText(hiddenPlan)).toHaveCount(0);
});

test("premium content is locked for a learner until an admin grants access, and revoking locks it again", async ({
  browser,
}) => {
  const learner = await newPage(browser, "learner");
  await learner.goto(`/courses/${courseSlug}/lessons/deep`);
  await expect(learner.getByTestId("locked")).toBeVisible();
  await expect(learner.getByRole("link", { name: "pricing page" })).toBeVisible();
  expect(await learner.content()).not.toContain(SECRET);
  await learner.goto("/billing");
  await expect(learner.getByTestId("access-state")).toHaveText("Free plan");
  await expect(learner.getByTestId("no-payments")).toBeVisible();
  await expect(learner.getByTestId("renewal-reminder")).toHaveCount(0);

  // the admin grants 30 days from the user's page
  const admin = await newPage(browser, "admin");
  await admin.goto("/admin/users?q=learner");
  await admin.locator('[data-testid="user-row"] a', { hasText: /^learner$/ }).click();
  const panel = admin.getByRole("region", { name: "Premium access" });
  await expect(panel.getByTestId("access-summary")).toHaveText("No premium access");
  await panel.getByLabel("Plan").selectOption({ label: planName });
  await panel.locator('input[name="grant-reason"]').fill("e2e: complimentary access");
  await panel.getByTestId("grant-access").click();
  await expect(panel.getByTestId("access-summary")).toContainText("Premium until");
  await expect(panel.getByTestId("access-period").filter({ hasNotText: "revoked" })).toHaveCount(1);

  await learner.goto("/billing");
  await expect(learner.getByTestId("access-state")).toContainText("Premium access until");
  await expect(learner.getByTestId("renewal-reminder")).toHaveCount(0); // 30 days is outside the window
  await expect(learner.getByTestId("period").filter({ hasText: planName })).toContainText(planName);
  await learner.goto(`/courses/${courseSlug}/lessons/deep`);
  await expect(learner.getByTestId("lesson-body")).toContainText(SECRET);

  // revoke: effective immediately
  admin.once("dialog", (d) => void d.accept("e2e: end of the test"));
  await panel.getByRole("button", { name: "Revoke" }).click();
  await expect(panel.getByTestId("access-summary")).toHaveText("No premium access");
  await learner.goto(`/courses/${courseSlug}/lessons/deep`);
  await expect(learner.getByTestId("locked")).toBeVisible();
  expect(await learner.content()).not.toContain(SECRET);
  await learner.goto("/billing");
  await expect(learner.getByTestId("access-state")).toHaveText("Free plan");
  await expect(learner.getByTestId("period").filter({ hasText: planName })).toContainText(
    "revoked",
  );
});

test("a learner whose access is about to end sees a renewal reminder that leads to pricing", async ({
  browser,
}) => {
  const admin = await newPage(browser, "admin");
  const call = await api(admin.request);
  const id = (await call("get", "admin/users?q=learner")).items.find(
    (u: { username: string }) => u.username === "learner",
  ).id;
  const plan = (await call("get", "admin/billing/plans")).find(
    (p: { code: string }) => p.code === planCode,
  );
  await call("post", `admin/billing/users/${id}/access-grants`, {
    planId: plan.id,
    days: 3,
    reason: "e2e: short grant to show the reminder",
  });
  await admin.context().close();

  const learner = await newPage(browser, "learner");
  await learner.goto("/billing");
  await expect(learner.getByTestId("access-state")).toContainText("Premium access until");
  const reminder = learner.getByTestId("renewal-reminder");
  await expect(reminder).toContainText("in 3 days");
  await expect(reminder).toContainText("will not renew automatically");
  await learner.getByTestId("renew-link").click();
  await expect(learner).toHaveURL(/\/pricing$/);
});

test("without payment credentials the pricing page offers no purchase, and the CSP admits the provider only there", async ({
  browser,
}) => {
  const anon = await newPage(browser);
  await anon.goto("/pricing");
  await expect(anon.getByTestId("payments-note")).toBeVisible();
  await expect(anon.getByTestId("buy-start")).toHaveCount(0); // payments are not configured in the dev stack

  const csp = async (path: string) =>
    (await anon.request.get(path)).headers()["content-security-policy"] ?? "";
  const pricing = await csp("/pricing");
  expect(pricing).toContain("frame-src https://checkout.razorpay.com");
  expect(pricing).not.toMatch(/script-src[^;]*razorpay/);
  for (const path of ["/", "/courses", "/problems"]) {
    const other = await csp(path);
    expect(other, path).not.toContain("razorpay");
    expect(other, path).toContain("frame-src 'none'");
  }
});

test("the access page needs a login", async ({ browser }) => {
  const anon = await newPage(browser);
  await anon.goto("/billing");
  await expect(anon.locator("#username")).toBeVisible();
});
