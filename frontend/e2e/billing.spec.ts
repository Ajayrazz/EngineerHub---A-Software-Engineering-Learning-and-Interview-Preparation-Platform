import { expect, test, type APIRequestContext, type Browser, type Page } from "@playwright/test";
import {
  APP_ORIGIN,
  csrfToken,
  deleteSubscription,
  login,
  seedPayment,
  seedSubscription,
  setSubscriptionStatus,
} from "./helpers";

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
  return async (method: "get" | "post" | "put" | "patch", path: string, data?: unknown) => {
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

test("an auto-renewing subscriber sees the renewal date and no renew-now reminder, and cancelling fails safely while payments are off", async ({
  browser,
}) => {
  await revokeLearnerAccess(browser);
  const admin = await newPage(browser, "admin");
  const call = await api(admin.request);
  const learnerId = (await call("get", "admin/users?q=learner")).items.find(
    (u: { username: string }) => u.username === "learner",
  ).id;
  const plan = (await call("get", "admin/billing/plans")).find(
    (p: { code: string }) => p.code === planCode,
  );
  const price = plan.prices.find((p: { billingMode: string }) => p.billingMode === "RECURRING");
  // 3 days left: inside the renewal window, but the subscription renews by itself
  await call("post", `admin/billing/users/${learnerId}/access-grants`, {
    planId: plan.id,
    days: 3,
    reason: "e2e: paid-up cycle of a subscription",
  });
  await admin.context().close();

  const subscriptionId = crypto.randomUUID();
  seedSubscription({
    subscriptionId,
    userId: learnerId,
    planId: plan.id,
    planPriceId: price.id,
    status: "active",
  });
  try {
    const learner = await newPage(browser, "learner");
    await learner.goto("/billing");
    const state = learner.getByTestId("subscription-state");
    await expect(state).toContainText("Renews on");
    await expect(state).toContainText(/₹\s?2,999\.00 per year/);
    await expect(learner.getByTestId("renewal-reminder")).toHaveCount(0);

    // payments are off in the dev stack: the provider cannot be told, so nothing may change
    await learner.getByTestId("cancel-start").click();
    await expect(learner.getByTestId("cancel-confirm")).toContainText("not refunded");
    await learner.getByTestId("cancel-yes").click();
    await expect(learner.getByTestId("cancel-error")).toContainText("Nothing changed");
    await learner.reload();
    await expect(learner.getByTestId("subscription-state")).toContainText("Renews on");

    // once auto-renew is off the reminder returns, and there is nothing left to cancel
    setSubscriptionStatus(subscriptionId, "cancel_scheduled");
    await learner.reload();
    await expect(learner.getByTestId("subscription-state")).toContainText("Auto-renew is off");
    await expect(learner.getByTestId("renewal-reminder")).toContainText("in 3 days");
    await expect(learner.getByTestId("cancel-start")).toHaveCount(0);
  } finally {
    deleteSubscription(subscriptionId);
  }
});

test("an admin configures documents, and a payer downloads a receipt only once they are allowed", async ({
  browser,
}) => {
  const admin = await newPage(browser, "admin");
  const call = await api(admin.request);
  await call("put", "admin/billing/tax", { mode: "UNCONFIGURED" }); // a known start, whatever a failed run left
  const userId = (await call("get", "admin/users?q=instructor")).items.find(
    (u: { username: string }) => u.username === "instructor",
  ).id;
  const plan = (await call("get", "admin/billing/plans")).find(
    (p: { code: string }) => p.code === planCode,
  );
  const price = plan.prices.find((p: { billingMode: string }) => p.billingMode === "RECURRING");
  const subscriptionId = crypto.randomUUID();
  seedSubscription({
    subscriptionId,
    userId,
    planId: plan.id,
    planPriceId: price.id,
    status: "cancelled",
  });
  const paymentId = crypto.randomUUID();
  seedPayment({ paymentId, subscriptionId, userId });

  try {
    const payer = await newPage(browser, "instructor");
    await payer.goto("/billing");
    const row = payer.getByTestId("payment").first();
    await expect(row).toContainText(/₹\s?2,999\.00/);
    await expect(row).toContainText("Not available yet"); // nothing is issued while tax is unconfigured

    // the admin page states what is and is not decided, and refuses incomplete tax invoices
    await admin.goto("/admin/billing");
    const panel = admin.getByRole("region", { name: "Tax and documents" });
    await expect(panel).toContainText("not tax advice");
    // no rate is ever assumed: nothing active exists until the owner adds one (earlier runs only switch theirs off)
    const start = await call("get", "admin/billing/tax");
    expect((start.rules ?? []).filter((r: { active: boolean }) => r.active)).toHaveLength(0);
    expect(start.mode).toBe("UNCONFIGURED");
    await panel.getByRole("radio", { name: /Tax invoices/ }).check();
    await expect(panel.getByTestId("tax-problems")).toContainText("ENABLED needs");
    await expect(panel.getByTestId("tax-save")).toBeDisabled();

    // receipts (no tax wording) are the choice that needs no tax details
    await panel.getByRole("radio", { name: /No tax: receipts/ }).check();
    await expect(panel.getByTestId("tax-save")).toBeEnabled();
    await panel.getByTestId("tax-save").click();
    await expect(admin.getByRole("status").filter({ hasText: "Tax settings saved" })).toBeVisible();

    await payer.reload();
    const download = payer.waitForEvent("download");
    await payer.getByTestId("payment").first().getByTestId("receipt-download").click();
    const file = await download;
    expect(file.suggestedFilename()).toMatch(/^RCPT-\d{4}-\d{2}-\d{6}\.pdf$/);
    const stream = await file.createReadStream();
    const head: Buffer[] = [];
    for await (const chunk of stream) {
      head.push(chunk as Buffer);
      break;
    }
    expect(Buffer.concat(head).subarray(0, 5).toString("ascii")).toBe("%PDF-");

    // from now on the page shows the issued number
    await payer.reload();
    await expect(
      payer.getByTestId("payment").first().getByTestId("receipt-download"),
    ).toContainText(/^RCPT\/\d{4}-\d{2}\/\d{6}$/);
    // a rule is entered as a percentage and stored exactly; full tax-invoice details unlock "Tax invoices"
    await admin.goto("/admin/billing");
    const rules = admin.getByRole("region", { name: "Tax and documents" });
    await rules.locator('input[name="rule-name"]').fill("E2E example tax");
    await rules.locator('input[name="rule-rate"]').fill("5.25");
    await rules.locator('input[name="rule-from"]').fill("2020-01-01");
    await rules.getByTestId("rule-add").click();
    await expect(admin.getByRole("status").filter({ hasText: "Rule added" })).toBeVisible();
    await expect(
      rules.getByTestId("tax-rule").filter({ hasText: "E2E example tax" }).first(),
    ).toContainText("5.25%");
    await rules.getByRole("radio", { name: /Tax invoices/ }).check();
    await expect(rules.getByTestId("tax-problems")).not.toContainText(
      "at least one active tax rule",
    );
    await expect(rules.getByTestId("tax-problems")).toContainText("ENABLED needs the legal name");

    // someone else cannot fetch it: it looks exactly like a missing payment
    const other = await newPage(browser, "learner");
    const theirs = await other.request.get(`/api/proxy/me/payments/${paymentId}/document`);
    expect(theirs.status()).toBe(404);
    await other.context().close();
  } finally {
    await call("put", "admin/billing/tax", { mode: "UNCONFIGURED" });
    const tax = await call("get", "admin/billing/tax");
    for (const r of tax.rules ?? [])
      if (r.active && r.name === "E2E example tax")
        await call("patch", `admin/billing/tax/rules/${r.id}`, { active: false });
    await admin.context().close();
  }
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
