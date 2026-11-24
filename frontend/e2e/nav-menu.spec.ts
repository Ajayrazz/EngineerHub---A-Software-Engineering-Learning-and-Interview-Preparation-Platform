import { expect, test } from "@playwright/test";

test.describe("main navigation menus", () => {
  test("Learn opens a mega menu whose links go to the catalog", async ({ page }) => {
    await page.goto("/");
    const nav = page.getByRole("navigation", { name: "Main" });
    await expect(nav.getByRole("link", { name: "System design", exact: true })).toBeHidden();
    await nav.getByRole("button", { name: /Learn/ }).hover();
    const link = nav.getByRole("link", { name: "System design", exact: true }).first();
    await expect(link).toBeVisible();
    await link.click();
    await expect(page).toHaveURL(/\/courses\?q=system%20design/);
    await expect(page.getByRole("heading", { name: "Courses" })).toBeVisible();
  });

  test("Practice lists real destinations and marks unbuilt ones as Soon (not links)", async ({
    page,
  }) => {
    await page.goto("/");
    const nav = page.getByRole("navigation", { name: "Main" });
    await nav.getByRole("button", { name: /Practice/ }).hover();
    await expect(nav.getByRole("link", { name: "Problem bank" })).toHaveAttribute(
      "href",
      "/problems",
    );
    await expect(nav.getByText("Company-wise problems")).toBeVisible();
    await expect(nav.getByRole("link", { name: /Company-wise/ })).toHaveCount(0);
  });

  test("the search box sends the query to the course catalog", async ({ page }) => {
    await page.goto("/");
    await page.getByRole("search").getByRole("textbox").fill("graphs");
    await page.keyboard.press("Enter");
    await expect(page).toHaveURL(/\/courses\?q=graphs/);
  });
});
