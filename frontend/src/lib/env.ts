import "server-only";
import { z } from "zod";

/**
 * Server-only configuration, validated lazily on first use (so `next build` does not need secrets) and
 * failing fast with a clear message. Nothing here is exposed to the browser.
 */
const schema = z.object({
  /** Public origin of this web app, e.g. https://app.example.com (no trailing path). */
  APP_URL: z.url(),
  /** Base URL of the Spring API (server-to-server). */
  API_BASE_URL: z.url(),
  KEYCLOAK_ISSUER_URI: z.url(),
  KEYCLOAK_BFF_CLIENT_ID: z.string().min(1),
  KEYCLOAK_BFF_CLIENT_SECRET: z.string().min(16),
  REDIS_URL: z.string().regex(/^rediss?:\/\//, "must start with redis:// or rediss://"),
  /** Master secret for session encryption and key MACs (rotate = all sessions invalid). */
  SESSION_SECRET: z.string().min(32),
  /** Honour X-Forwarded-For for per-client rate limiting; only set behind a proxy that overwrites it. */
  TRUST_PROXY_HEADERS: z.stringbool().default(false),
  SESSION_IDLE_TTL_SECONDS: z.coerce
    .number()
    .int()
    .min(60)
    .default(7 * 24 * 3600),
  SESSION_ABSOLUTE_TTL_SECONDS: z.coerce
    .number()
    .int()
    .min(300)
    .default(30 * 24 * 3600),
});

export type Env = z.infer<typeof schema> & {
  appOrigin: string;
  issuerOrigin: string;
  /** True when served over HTTPS: enables Secure cookies and the __Host- cookie prefix. */
  secureCookies: boolean;
};

const isLocalHost = (host: string) =>
  host === "localhost" || host === "127.0.0.1" || host === "[::1]";

let cached: Env | undefined;

export function getEnv(): Env {
  if (cached) return cached;
  const parsed = schema.safeParse(process.env);
  if (!parsed.success) {
    const problems = parsed.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
    throw new Error(`Invalid server configuration: ${problems}`);
  }
  const base = parsed.data;
  const app = new URL(base.APP_URL);
  const issuer = new URL(base.KEYCLOAK_ISSUER_URI);
  // Plain HTTP is tolerated only for local development.
  if (app.protocol !== "https:" && !isLocalHost(app.hostname)) {
    throw new Error("APP_URL must be https:// outside localhost");
  }
  if (issuer.protocol !== "https:" && !isLocalHost(issuer.hostname)) {
    throw new Error("KEYCLOAK_ISSUER_URI must be https:// outside localhost");
  }
  cached = {
    ...base,
    appOrigin: app.origin,
    issuerOrigin: issuer.origin,
    secureCookies: app.protocol === "https:",
  };
  return cached;
}
