import { expect, test } from "@playwright/test";
import { APP_ORIGIN, csrfToken, login } from "./helpers";

test.describe("user provisioning and the profile API through the BFF", () => {
  test("login provisions a local user (UUIDv7) and the account page shows it", async ({ page }) => {
    await login(page, "learner");
    const id = await page.getByTestId("user-id").textContent();
    expect(id).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    const me = await (await page.request.get("/api/proxy/me")).json();
    expect(me).toMatchObject({ id, username: "learner", status: "active", roles: [] });
  });

  test("the same user is returned on every login (no duplicate accounts)", async ({ browser }) => {
    const ids: string[] = [];
    for (let i = 0; i < 2; i++) {
      const context = await browser.newContext({ baseURL: APP_ORIGIN });
      const page = await context.newPage();
      await login(page, "moderator");
      ids.push((await (await page.request.get("/api/proxy/me")).json()).id);
      await context.close();
    }
    expect(ids[0]).toBe(ids[1]);
  });

  test("profile edits need the CSRF token, persist, and hostile links are rejected", async ({
    page,
  }) => {
    await login(page, "instructor");
    const token = await csrfToken(page.request);
    const headers = {
      origin: APP_ORIGIN,
      "x-csrf-token": token,
      "content-type": "application/json",
    };

    const noCsrf = await page.request.patch("/api/proxy/me/profile", {
      headers: { origin: APP_ORIGIN, "content-type": "application/json" },
      data: { displayName: "Nope" },
    });
    expect(noCsrf.status()).toBe(403);

    const ok = await page.request.patch("/api/proxy/me/profile", {
      headers,
      data: {
        displayName: "Ins Tructor",
        bio: "Teaches graphs",
        githubUrl: "https://github.com/instructor",
      },
    });
    expect(ok.status()).toBe(200);
    const after = await (await page.request.get("/api/proxy/me")).json();
    expect(after.profile).toMatchObject({
      displayName: "Ins Tructor",
      githubUrl: "https://github.com/instructor",
    });

    for (const website of [
      "javascript:alert(1)",
      "http://insecure.example",
      "https://u:p@evil.example",
    ]) {
      const bad = await page.request.patch("/api/proxy/me/profile", { headers, data: { website } });
      expect(bad.status(), website).toBe(400);
      expect((await bad.json()).code).toBe("VALIDATION_FAILED");
    }
  });
});
