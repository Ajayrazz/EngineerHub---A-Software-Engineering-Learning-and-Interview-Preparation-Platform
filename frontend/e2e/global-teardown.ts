import { execFileSync } from "node:child_process";
import path from "node:path";

/** Keeps a developer's local database free of test content (CI starts from an empty database anyway). */
export default async function globalTeardown() {
  if (process.env.CI || process.env.E2E_KEEP_CONTENT) return;
  try {
    execFileSync(path.join(__dirname, "../../scripts/clean-test-content.sh"), { stdio: "ignore" });
  } catch {
    console.warn("e2e teardown: could not clean test content (is the docker stack up?)");
  }
}
