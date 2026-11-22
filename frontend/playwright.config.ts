import { defineConfig, devices } from "@playwright/test";

/**
 * Browser tests of the real login flow. They run against an ALREADY RUNNING stack (they do not start
 * one): `make dev-up`, `make backend-run`, `make frontend-start`, then `make e2e` (CI does the same).
 */
export default defineConfig({
  testDir: "./e2e",
  globalSetup: "./e2e/global-setup.ts",
  fullyParallel: false, // sessions and Keycloak are shared; determinism over speed
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  timeout: 60_000,
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.APP_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
