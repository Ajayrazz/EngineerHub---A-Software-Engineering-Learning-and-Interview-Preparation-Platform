// Fails if src/lib/api/schema.d.ts is not exactly what `npm run api:generate` would produce from the
// committed OpenAPI contract (../openapi/engineerhub-v1.yaml). Keeps the TS client and the API in sync.
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const spec = "../openapi/engineerhub-v1.yaml";
const committed = "src/lib/api/schema.d.ts";
const dir = mkdtempSync(join(tmpdir(), "api-check-"));
const generated = join(dir, "schema.d.ts");
try {
  execFileSync("npx", ["openapi-typescript", spec, "-o", generated], { stdio: "ignore" });
  const a = readFileSync(generated, "utf8");
  const b = readFileSync(committed, "utf8");
  if (a !== b) {
    console.error(`${committed} is out of date with ${spec}. Run: npm run api:generate`);
    process.exit(1);
  }
  console.log("API client is in sync with the OpenAPI contract.");
} finally {
  rmSync(dir, { recursive: true, force: true });
}
