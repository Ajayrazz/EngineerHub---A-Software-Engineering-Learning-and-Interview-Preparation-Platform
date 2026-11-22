# 22 — Version Compatibility Matrix

_Established 2026-11-23 (milestone M0.1). Sources: Maven Central metadata, npm registry via `create-next-app`, keycloak.org and postgresql.org via web search. "Verified" means exercised in this repo's build/tests; everything else is "Researched" or "Not yet checked" and must be re-verified when first used._

## Backend

| Component | Version | Status | Notes |
|---|---|---|---|
| JDK | **21 LTS** (Temurin 21.0.7 installed) | Verified | Maven Enforcer fails the build on any other JDK. Shell default on this machine is JDK 17 → `make` selects 21 automatically; for raw `mvn`, set `JAVA_HOME` |
| Spring Boot | **4.1.3** | Verified (builds, 2 tests pass) | Boot **3.5 reached OSS end-of-life 30 Jun 2026** → we start on 4.x. Boot **4.2.0 is GA** (on Maven Central, 19 Nov 2026) but only days old; stay on the 4.1 line, plan the 4.2 upgrade after its first patch releases |
| Spring Framework / Security / Data | 7.0.9 / 7.1.1 / 2026.0.1 | Verified (M0.4: resource server, method security, JPA) | Do not override |
| Hibernate ORM | 7.4.5.Final | Verified (M0.4: `ddl-auto=validate` against Flyway schema, native upsert, JPQL delete) | |
| Flyway | 12.4.0 (+ `flyway-database-postgresql`) | Verified (M0.4, PG 18.7) | Starter is `spring-boot-starter-flyway`; Maven Central has 13.x — **do not override** unless Boot supports it |
| Jackson | 3.1.5 (`tools.jackson`) | Verified (problem+json writer) | Boot 4 moved to Jackson 3 — `tools.jackson.databind.ObjectMapper`; Spring's `ProblemDetail` serialises correctly |
| Spring AMQP | 4.1.1 | Boot-managed | Used from M0.6 |
| Testcontainers | **2.0.5** | Verified (Postgres 18 IT on Docker Desktop/macOS) | Artifacts `testcontainers-junit-jupiter`, `testcontainers-postgresql`; package `org.testcontainers.postgresql.PostgreSQLContainer` |
| Spring Modulith | **2.2.0** | Verified (`ModularityTests` passes on Boot 4.1.3) | Boot 4-compatible line |
| springdoc-openapi | **3.2.1** | Verified with Boot 4.1.3 (OpenAPI 3.1 generation; contract test) | API docs/Swagger UI disabled by default; enabled by `local` profile and the contract test |
| MapStruct | 1.6.3 stable (1.7.0 is Beta) | Not yet added | Use stable only |
| Bucket4j | 8.23.0 | Not yet added | Verify with Boot 4 when rate limiting lands (Phase 1) |
| Starters (Boot 4 naming) | `spring-boot-starter-webmvc`, `-validation`, `-actuator`, test: `-webmvc-test` | Verified | `@AutoConfigureMockMvc` now in `org.springframework.boot.webmvc.test.autoconfigure` |
| Maven | 3.9.12 (wrapper `./mvnw` generated) | Verified | Use the wrapper |

**New in M0.4 (all verified):** `spring-boot-starter-data-jpa`, `-flyway`, `-security`, `-oauth2-resource-server`; tests: `spring-boot-testcontainers` (`@ServiceConnection` works with Testcontainers 2.x `org.testcontainers.postgresql.PostgreSQLContainer`), `-starter-security-test`, `-starter-data-jpa-test`; ArchUnit comes transitively via Modulith. Boot 4 package moves hit: `@AutoConfigureMockMvc` → `org.springframework.boot.webmvc.test.autoconfigure`, `ValidationAutoConfiguration` → `org.springframework.boot.validation.autoconfigure`.

**Mockito agent warning:** surefire/failsafe run with `-XX:+EnableDynamicAgentLoading` (JDK 21 noise suppressed; revisit when moving past JDK 21).

## Frontend

| Component | Version | Status | Notes |
|---|---|---|---|
| Next.js | **16.4.6** (App Router) | Verified (`lint` + `build` pass) | Next 15 LTS EOL is 21 Oct 2026, so 16 is correct. `frontend/AGENTS.md` warns Next 16 differs from older knowledge → read `node_modules/next/dist/docs/` before coding |
| React / React DOM | 19.3.0 | Verified | |
| Tailwind CSS | 4.3.3 (+ `@tailwindcss/turbopack`) | Verified (builds) | v4 config is CSS-first; shadcn/ui compatibility to be **verified when initialised (M0.x / Phase 1)** |
| TypeScript / ESLint | 5.x / 9.x | Verified | ESLint 9.39 prints an "unsupported" deprecation notice from npm — not yet addressed (eslint-config-next 16.4.6 targets ESLint 9); revisit when Next supports ESLint 10 |
| Node.js | **24 LTS recommended** (`.nvmrc`); installed here: v25.7.0 (non-LTS) | Verified building on 25.7 | Use LTS for CI/production. Corepack is not bundled with Node 25 |
| Package manager | **npm** (lockfile committed) | Decision change | Docs earlier said pnpm; pnpm isn't installed and npm avoids extra tooling for a solo dev. Revisit only if needed |
| openid-client | 6.9.1 | Verified (M0.5, against Keycloak 26.8.2: PKCE, refresh rotation, end-session) | Dev http issuer needs `allowInsecureRequests` (restricted to localhost) |
| redis (node-redis) | 6.3.1 | Verified against Redis 8 (SET NX/EX, GETDEL, EVAL, KEEPTTL) | |
| zod | 4.6.x | Verified (`z.url()`, `z.coerce`) | Server env validation |
| server-only | 0.0.1 | Verified | |
| Vitest | 4.1.12 | Verified | npm shows 5.x as latest; 4.x was resolved by the install and works — 5.x not evaluated |
| @playwright/test | 1.65.1 + Chromium | Verified (local + needs CI run) | Only Chromium installed/tested |
| shadcn/ui, TanStack Query, Zustand, RHF, Monaco, Recharts, Framer Motion, TipTap | — | **Not yet added / not checked** | Added when their first feature needs them; each verified against React 19.3 / Next 16.4 at that time |
| Next.js config | `cacheComponents`/`partialPrefetching` removed from the scaffold default | ADR-028 | |

## Infrastructure (dev stack — pinned and verified in M0.2)

| Component | Pinned | Status | Notes |
|---|---|---|---|
| PostgreSQL | `postgres:18` → 18.7 | Verified (`make dev-verify`) | PG 18 image requires volume mounted at `/var/lib/postgresql` (not `/data`). PG 19 reported by one source only → not used. pgvector (`pgvector/pgvector:pg18` exists) deferred to AI phase |
| Redis | `redis:8` | Verified | |
| RabbitMQ | `rabbitmq:4.3.8-management` | Verified (starts, vhost, auth) | AMQP client interplay with Spring AMQP 4.1.1 verified in M0.6 |
| Keycloak | `quay.io/keycloak/keycloak:26.8.2` | Verified: import, admin API, email | Uses `KC_BOOTSTRAP_ADMIN_*` (26.x); health on management port 9000 |
| Mailpit | `axllent/mailpit:v1.34.0` | Verified | Replaces MailHog |
| S3-compatible | `rustfs/rustfs:1.0.6` | Partially verified (bucket create/list only) | Replaces MinIO (no community images published). Apache-2.0 |
| aws-cli (bucket init) | `amazon/aws-cli:latest` | Verified | Dev-only helper; **unpinned** — pin when convenient |
| Docker | 29.9.3 / Compose v5.7.1 | Verified | |
| Redis/Valkey, MailHog, MinIO alternatives | — | — | See ADR-023 |

## Quality & security tooling (M0.3)

| Tool | Version | Status | Notes |
|---|---|---|---|
| Spotless Maven plugin + google-java-format | 3.12.0 + **1.36.1** | Verified | **GJF 1.37.0 fails** with an InvocationTargetException under Spotless 3.12.0 on JDK 21.0.7 → pinned to 1.36.1 (retry on upgrades) |
| maven-failsafe | 3.5.6 (Boot-managed) | Verified | `*IT` classes run in `verify`; `*Test` in surefire |
| Prettier / eslint-config-prettier | 3.10.x / 10.1.x | Verified | `printWidth: 100` |
| openapi-typescript | 7.13.0 | Verified | Generates `frontend/src/lib/api/schema.d.ts` from `openapi/engineerhub-v1.yaml` (OpenAPI 3.1) |
| CycloneDX Maven plugin | 2.9.3 | Verified | Produces full resolved SBOM for scanning (pom.xml alone hid Boot-managed deps) |
| gitleaks | 8.30.1 | Verified (negative + positive controls) | CLI/container, not the marketplace action |
| OSV-Scanner | 2.7.1 | Verified (positive control: log4j 2.14.1 → 8 vulns) | Scans SBOM + npm lockfile |
| actionlint | 1.7.12 | Verified (catches planted errors) | |
| GitHub Actions pinned | checkout v7.0.1, setup-java v6.3.0, setup-node v7.1.0, upload-artifact v7.0.2, codeql-action v4.39.4 | SHAs resolved from upstream tags; **verified: all CI jobs + CodeQL green on GitHub (2026-11-23)** | Dependabot keeps them current |
| Node for CI | `.nvmrc` = 24 | Not run locally (local Node is 25.x) | |

## Upgrade policy
Boot-managed dependencies follow the Boot BOM; patch releases monthly after reading release notes; minor/major upgrades are separate milestones with a green `make verify`. Dependabot/Renovate added in M0.3.
