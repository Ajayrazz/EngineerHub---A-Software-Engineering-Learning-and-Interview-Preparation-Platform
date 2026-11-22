# 19 — Project Progress

_Last updated: 2026-11-23_

## Completed milestones
- **M0.1 — Product analysis & architecture blueprint** (docs 00–19, README, CHANGELOG, CONTRIBUTING, `.env.example`).
- **M0.2 — Round-1 decisions recorded** (branding, judge/sandbox, languages, Razorpay, Keycloak) — docs 20, 21 added.
- **M0.3 — Round-2 decisions recorded** (purchase models, GST/pricing, refunds, content ownership, hosting portability, solo roadmap) — docs 01, 06, 07, 15, 16, 17, 20, 21 updated; ADR-018…022 added.

- **M0.1 — Monorepo scaffold & version matrix — DONE (2026-11-23).** Backend: Spring Boot 4.1.3 / Java 21 / Modulith 2.2.0; `make backend-test` passes (2 tests: module-boundary verification, ping endpoint); JDK-21 guard verified to reject JDK 17. Frontend: Next.js 16.4.6 / React 19.3 / Tailwind 4.3.3; `lint` and production `build` pass. Added `CLAUDE.md`, PR template, Makefile, `docs/22`. **Not yet:** Docker Compose, CI, database, auth, any product feature. `git init` done at repo root; **nothing committed.**

- **M0.2 — Local dev stack — DONE (2026-11-23).** `docker-compose.yml` (Postgres 18, Redis 8, RabbitMQ 4.3, Keycloak 26.8.2 with realm-as-code, Mailpit, RustFS S3 + bucket init), `.env.example` workflow, `make dev-*` targets, `scripts/verify-dev-stack.sh`: **24/24 checks pass, also after stop/start** (data + realm persist). Details and limitations: docs/18.

- **M0.3 — CI & quality gates — DONE locally (2026-11-23); GitHub side unverified.** Backend: Spotless/google-java-format, failsafe ITs, Testcontainers 2.0.5 Postgres 18 IT, springdoc 3.2.1 with committed OpenAPI contract + drift test, API docs off by default. Frontend: Prettier, typecheck, ESLint+Prettier, openapi-typescript client + sync check. Security: gitleaks config + pre-commit hook (`make hooks`, enabled), OSV scan of SBOM + lockfile (`make scan`, clean), actionlint. GitHub: `ci.yml` (backend, frontend, secrets, dependency-scan, dev-stack, workflow-lint), `codeql.yml`, `dependabot.yml`, actions pinned to commit SHAs. `make verify` passes; every gate was checked against a deliberate failure (docs/14).

- **M0.4 — Backend skeleton — DONE (2026-11-23).** RFC 9457 error model (stable codes, correlation id, no leakage), stateless JWT resource server (issuer + audience + realm-role allowlist, 401/403/503 problem+json, strict headers, CORS allowlist, fail-fast config), method-security policy enforced by an ArchUnit build rule, Flyway V1 baseline + JPA (`ddl-auto=validate`) on PostgreSQL 18, idempotency primitive (`shared.processed_messages` + `IdempotencyService`), UUIDv7 `Ids`, UTC `Clock`, ECS JSON logging, diagnostic endpoints, regenerated OpenAPI + TS client. **66 backend tests pass** (31 unit + 35 integration on real Postgres 18) plus **33 live checks** against the real dev Keycloak and 24 stack checks. Tests found and fixed two real bugs (IdP outage → 500; wrong error code).

## In progress
Nothing running. Next is **M0.5** (Spring Boot skeleton: error model, security config, Flyway baseline, JPA, structured logging).

## Pending
Roadmap (doc 16) Phases 0–9 plus later items; content track.

## Known bugs / Technical debt / Blockers
None (no code). No blockers for Phase 0–5. Phase 6 *live* payments are blocked by P-4/P-8 by design (test mode proceeds).

## Decisions — RESOLVED

| Topic | Decision | Recorded in |
|---|---|---|
| Name / content | EngineerHub; original or licensed content only | ADR-013, doc 20 |
| Identity | Self-hosted Keycloak | ADR-015, doc 08 §5 |
| Judge engine | Custom `isolate`-based worker; Judge0 rejected (re-validated in M4.1 gate) | ADR-005, doc 10 |
| Judge scope | Java, Python, C++; stdin/stdout first, signature-ready | JUDGE-003/010 |
| Payment provider | Razorpay only (test mode until Gate); Stripe deferred | ADR-014, ADR-022 |
| Purchase options | Monthly pass, annual pass, auto-renew monthly, auto-renew annual — one billing core | ADR-018, doc 21 |
| Pricing/tax | Tax-inclusive displayed prices; final payable shown before payment; GST configurable, no assumed registration/rate | ADR-021, doc 21 §5 |
| Refunds | Proposed policy (6 points), configurable; admin refund workflow & records | Doc 21 §7 |
| Legal entity/KYC | **Not assumed**; live activation after finalization; no placeholder legal data | Doc 21 §9 |
| Content | Owner authors with Claude assistance; owner is final reviewer; CMS built for independent authoring | Doc 20 §8, doc 16 §4 |
| Hosting | Portable, Docker/env-based, no unnecessary AWS lock-in; provider undecided | ADR-019, doc 15 |
| Team | Solo developer + Claude; roadmap re-baselined | ADR-020, doc 16 |
| Infra posture | Free/OSS-first; avoid early spend | ADR-017 |
| Defaults applied earlier (unchanged unless you object) | Monorepo; S3-compatible object storage; responsive web/PWA only; "certificate of completion" only; SES-or-any-SMTP for email; self-run RabbitMQ | docs 13/15 |

## PENDING DECISIONS (not assumed — nothing is built on these yet)

**Payments & tax (doc 21 §11)**
| ID | Topic | Proposed default (non-binding) | Needed by |
|---|---|---|---|
| P-1 | Overlapping purchases (extend vs block) | Extend for passes; block 2nd active recurring | M6.1 |
| P-2 | Grace period after failed renewal | 3 days | M6.4 |
| P-3 | Plan switching / proration | None in v1 | M6.4 |
| P-4 | GST registration, rate, SAC, rounding, invoice rules (accountant) | **None shipped** | Live launch (M6.5 builds config only) |
| P-5 | Coupons on recurring plans | Not in v1 | M6.2 |
| P-6 | Final refund policy wording/request window; ToS/Privacy | Proposed text in doc 21 §7 | Live launch |
| P-7 | Plan names, INR prices, free trial? | Not set | M6.1 |
| P-8 | Legal entity type, Razorpay KYC, bank details | Not assumed | Live launch |

**Hosting (doc 15 §7)**: H-1 provider/option · H-2 region & data residency · H-3 CDN/WAF · H-4 SMTP provider · H-5 object-storage provider. *Needed by: first beta deployment (after Phase 4–5); not needed for local development.*

**Roadmap (doc 16 §7)**: T-1 weekly hours available · T-2 content pace (after first batch) · T-3 target beta date/deadline. *T-1 is useful before Phase 0 ends so I can convert effort-weeks to dates.*

**Other still-open items:** retention periods for source code/transcripts/logs (OQ-12, legal review) · exact stack versions (resolved by the Phase 0 compatibility matrix) · OIDC library for the Next.js BFF (decided in M0.5).

## Risk register (updated)

| Risk | Impact | Mitigation |
|---|---|---|
| Solo capacity / burnout / scope creep | **High** | MVP fence, small milestones, cut scope not quality, automation (doc 16 §5) |
| Sandbox escape / abuse | Critical | isolate in disposable no-egress VM, hostile corpus in CI, M4.1 go/no-go gate, external/second review before public exposure |
| Custom judge slower to build than expected | Medium | M4.1 time-boxed; fallback options documented |
| Billing complexity (4 models + refunds + tax) | High | Single core (ADR-018), passes before recurring, exhaustive test matrix, test mode only until Gate |
| Compliance (GST, consumer law, RBI recurring rules, KYC) | High | Configurable design, accountant/lawyer sign-off, Live Readiness Gate |
| Content volume and correctness | High | CMS + validation harness first; machine-verified tests; owner review |
| IP/legal on content & AI-assisted material | Medium–High | Doc 20; no pasting third-party text; lawyer review |
| Self-hosted ops (Keycloak, Postgres backups, single VM) | Medium | Backups + restore drills, small stack, runbooks; managed DB option (doc 15) |
| Stack version incompatibility | Medium | Phase 0 compatibility matrix, pinned versions |

## Known issues / debt (from M0.1)
- Local default JDK is 17 and Node is 25 (non-LTS): use `make` (selects JDK 21); install Node 24 LTS when convenient.
- ESLint 9.39 deprecation notice and Mockito dynamic-agent warning — handle in M0.3.
- springdoc 3.2.1, shadcn/ui + Tailwind 4, Testcontainers 2.x, Bucket4j not yet verified against this stack (checked when first added).
- Spring Boot 4.2.0 GA exists; upgrade is a later separate milestone.

## Known issues / debt (from M0.2)
- Interactive browser login (Next.js BFF ↔ Keycloak) **not yet tested** → M0.5.
- Dev realm: no admin MFA, no social IdPs, `start-dev` HTTP — hardening in Phase 1/hosting.
- RustFS only partially verified (bucket create/list); `amazon/aws-cli:latest` unpinned.
- Native Postgres/Redis on this Mac occupy 5432/6379 → stack uses 5440/6380; another process also holds 5433.
- `.env` is git-ignored (dev values only).

## Known issues / debt (from M0.3)
- **CI workflows have never run on GitHub** (no repo yet): syntax-checked with actionlint and each step's command run locally, but runner-specific issues (Docker, permissions, image pulls, `make dev-up` on Ubuntu) are unproven.
- google-java-format pinned to 1.36.1 (1.37.0 breaks under Spotless 3.12.0); revisit on upgrades.
- Trivy/container-image scanning deferred (no images yet, see ADR-024). No DAST yet. No frontend unit-test setup yet (comes with M0.5).
- `osv-scanner` prints a "risky enricher" warning for pom scanning; we scan the SBOM instead.
- Branch protection / required checks must be configured in GitHub once a repo exists.
- ESLint deprecation notice from npm remains.

## PENDING DECISION added
| ID | Question | Notes |
|---|---|---|
| ~~C-1~~ | ~~GitHub repository~~ | **RESOLVED 2026-11-23:** repo exists and is **public** (`Ajayrazz/EngineerHub---A-Software-Engineering-Learning-and-Interview-Preparation-Platform`), so CodeQL is free. Standing instruction: commit + push after every change set. |

## Known issues / debt (from M0.4)
- **Not implemented (by design, scheduled):** API rate limiting + request-size limits + brute-force controls (Phase 1); admin token revocation/`token_version` (Phase 1); permission/scoped-grant model and `@authz` evaluator (Phase 1); actuator on a separate management port; admin MFA/step-up.
- **Flyway runs as the application DB user** (it owns the DB). Production should use a separate migration user and a runtime user without DDL rights — decide at hosting time.
- `ProcessedMessage`/`IdempotencyService` and `Ids` have no production caller yet (first use: M0.6 outbox consumers, Phase 1 entities).
- `ddl-auto=validate` is proven by successful start-up; no negative control (deliberate schema mismatch) was run.
- Exception messages are logged with stack traces — rule: never put secrets/PII in them (docs/09 addendum).
- Dev realm contains two **dev-only clients** (`engineerhub-dev-cli`, `engineerhub-dev-noaud`); they must never be imported into staging/prod.
- GitHub CI (including the new live-auth steps in the dev-stack job) still unrun.
- The 17 files regenerated by spotless/google-java-format follow GJF 1.36.1 style.

## Next recommended task
**M0.5** — Keycloak + Next.js BFF login spike: Auth.js or `openid-client` decision (ADR), authorization-code + PKCE with a server-side session in Redis, HttpOnly cookie, CSRF protection for BFF routes, proxy route that attaches the bearer token, silent refresh, logout; verify end-to-end in a real browser (needs Playwright). Adds frontend test setup (Vitest + RTL). Then M0.6 (transactional outbox → RabbitMQ → idempotent consumer, using the primitives from M0.4). Still useful: your weekly hours (T-1) and answer to C-1.
