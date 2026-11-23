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
- **M0.5 — Next.js ↔ Keycloak login (BFF) — DONE (2026-11-23).** Authorization Code + PKCE via `openid-client`; encrypted, HMAC-keyed server-side sessions in Redis; HttpOnly SameSite=Lax opaque cookie; CSRF-protected logout and API proxy (path/SSRF guards, header allowlists, body cap); refresh serialised under a lock (Keycloak rotates refresh tokens) and **sessions survive an IdP outage** (503 + Retry-After, same session recovers); per-request CSP nonce (`proxy.ts`), Cache Components turned off (ADR-028). **84 Vitest unit tests + 35 Playwright E2E tests** driving the real Keycloak login page, incl. attack tests (login CSRF with an attacker's valid callback, replay, fixation, forged cookies, traversal, Redis tampering) validated by **mutation** (broke 3 controls; all caught). Review found and fixed a real flaw (any refresh failure destroyed the session). Details: docs/08 §7, ADR-028…030.

- **M0.6 — Transactional outbox → RabbitMQ → idempotent consumer — DONE (2026-11-23). PHASE 0 COMPLETE.** Flyway V2 `shared.outbox`; `OutboxPublisher` (fails outside a transaction); `OutboxRelay` (`SKIP LOCKED`, publisher confirms, persistent messages, exponential backoff, retention purge, metrics); validated envelope codec; durable quorum queues + DLQ topology; `IdempotentEventHandler` (ledger + handler commit/rollback together); fail-fast broker config (no `guest`, TLS unless localhost). **98 backend tests** (39 unit + 59 integration on real PostgreSQL 18 + RabbitMQ 4.3.8) incl. 6 concurrent relays × 200 events with zero duplicates, crash-between-confirm-and-commit (2 deliveries → 1 effect), poison messages → DLQ; 4 controls mutation-validated; **20 live checks** (`make msg-verify`) incl. stopping the RabbitMQ container mid-publish: nothing lost, no duplicates, auto-reconnect. Bugs found: null payload accepted by the codec, silently ignored Boot-4 retry property (4 attempts not 3), relay log/reconnect flood during outages. ADR-031/032.

- **Phase 1 / M1.1 — Identity — DONE (2026-11-23).** Flyway V3 (`identity.users`, `identity.user_profiles`), JIT provisioning (atomic upsert, race-tested: 10 parallel first calls → 1 user + 1 event), `/me` profile + preferences API with strict validation (javascript:/http:/user-info links, control chars, mass assignment), suspension guard SPI, `UserDirectory` public API, ADR-033 + ADR-025 amendment (found by a test: anonymous writes got 400 before 401). 118 backend tests; live auth script 45 checks; 38 browser tests.

- **Phase 1 / M1.2 — Authorization model — DONE (2026-11-23).** Flyway V4 permission catalog seeded from the docs/08 matrix; `Authorizer` (`@authz.can(...)`, deny-by-default, fail-closed); scoped grants that never exceed the role; `/me/permissions`; `AuthorizationMatrixIT` (every role × permission cell, scoped/expired/suspended/leftover grants, typo guard); mutation-checked (2 caught, 1 equivalent). ADR-034.
- **Phase 1 / M1.3 — Audit log + admin API — DONE (2026-11-23).** Flyway V5 append-only hash-chained audit log written in the audited transaction; admin endpoints for users (search/page), status change (audited, immediate effect, never self), scoped grants (anti-escalation) and roles; audit search/verify. 147 backend tests (42 unit + 105 integration). Bug found by tests: invalid event name (`status-changed`) — the validator caught it. ADR-035.

## In progress
- **Phase 1 / M1.4 — Rate limiting + request-size limits — DONE (2026-11-23).** API: Redis fixed-window quotas per user/address (read/write/admin), 429 problem+json with Retry-After, fail-open; 1 MiB body cap (413, also for chunked). BFF: per-client limits on /auth/login and /auth/callback. 12 new backend tests (8 IT on a real Redis container, 4 unit) + 7 BFF unit tests; live check against the dev Redis. ADR-036. **Not verified:** behaviour behind a real reverse proxy (client-address trust is a hosting-dependent setting); BFF limiter has no browser E2E; no load test.

- **Phase 1 / M1.5 — Keycloak: realm split, staff MFA, role assignment — DONE (2026-11-23).** Realm = prod-safe base + dev overlay with a drift/safety check in `make verify` (13 mutations caught); TOTP required for all six staff roles in the browser flow (3 new Playwright specs + OTP in the login helper; disabling the OTP step makes them fail); `GET/PUT /admin/users/{id}/roles` via a least-privilege service account (client HTTP contract test, 10 integration tests, revert-path unit test, 3 guard mutants killed, 11 new live checks → `api-verify` 55/55); an admin can no longer suspend an administrator. Bugs found on the way: first-login provisioning raced on the username index (and a username clash was a 500) → race-safe insert + 409; BFF shared login bucket throttled everyone → 30x ceiling. ADR-037, ADR-038. **Not verified:** production Keycloak (HTTPS/hostname), authenticator-loss recovery, WebAuthn/step-up, immediate effect of a role removal (<= 5 min token lifetime).

- **Phase 1 / M1.6 — Admin shell UI — DONE (2026-11-23).** Overview, users (search/filter/paging), user detail (status, staff roles, scoped grants) and audit log with chain verification; server-side gating (404 for non-admins), mutations through the BFF with CSRF, API refusals surfaced. 12 new Playwright tests + 7 unit tests (e2e suite 55 passed twice in a row; 98 frontend unit tests); gate removal mutation caught. ADR-039. **Not verified:** accessibility, mobile layout, behaviour over HTTPS.

- **Phase 2 / M2.1 — Catalog backend + authoring workflow — DONE (2026-11-23).** Flyway V6 (`catalog` schema: topics with the 31-topic seed, courses, sections, lessons, content blocks, content sources); authoring API under `/api/v1/admin/courses/**` (course/section/lesson CRUD, reorder, blocks, source, publish/unpublish, archive); draft + published-snapshot model, publish gate, optimistic concurrency, scoped instructor access. 10 integration tests (authorization table for every endpoint × role incl. scoped instructor, workflow + snapshot isolation, gates, validation, concurrency, cross-course isolation, reorder/delete rules) + 6 unit tests; 3 mutants killed (one survived first: the no-source gate was masked by the empty-blocks gate, test fixed). ADR-040. **Not verified:** performance with large courses; the reader side (M2.2).

- **Phase 2 / M2.2 — Public reader API with server-side gating — DONE (2026-11-23).** Public topics, course list (filters/paging/escaped search), course outline and lesson endpoints serving only published snapshots; premium bodies need the server-side entitlement check (`403 ENTITLEMENT_REQUIRED`, no body); new `billing` module with `EntitlementService` + a stub (staff only, scoped for instructors) until M6.1; cache headers by audience. 12 integration tests; 4 mutants killed; 6 live checks added to `api-verify`. ADR-041. **Not verified:** CDN behaviour, load, real entitlements. CI found a test-isolation flake caused by the new tests (events left in the shared outbox consumed the fault injected by `OutboxFaultInjectionIT`); that test now starts from a drained outbox.

- **Phase 2 / M2.3 — Admin CMS UI — DONE (2026-11-23).** Course list/create, course editor (details, outline with section/lesson reordering, publish/unpublish/archive), lesson editor (settings, markdown/code/callout blocks with live preview and client-side validation, content source, originality-confirmed publish, "unpublished changes" indicator); scoped instructors see only their courses. New libraries `react-markdown` 10.1.0 + `remark-gfm` 4.0.1 (ADR-042). 11 Playwright specs + 14 unit tests (e2e suite 66 passed; 112 unit tests); a Markdown-safety mutation was caught. **Not verified:** accessibility, mobile layout, very large lessons, HTTPS.

- **Phase 2 / M2.4 — Reader UI + progress — DONE (2026-11-23).** Flyway V7 + `learning` module (enroll, per-lesson progress, resume, my enrollments, completion events) behind a new `CatalogApi`; public SSR reader (course list/filters, course outline with Enroll/Continue, lesson page with sidebar ticks, TOC, previous/next, mark complete, locked-premium panel, My courses). 10 backend integration tests (4 mutants killed after two survived and tests were strengthened), 8 Playwright specs (e2e suite 74 passed), 5 new unit tests (117 total). ADR-043. **Not verified:** accessibility, mobile layout, behaviour over HTTPS, load.

- **Phase 3 / M3.1 — Problem bank + authoring API — DONE (2026-11-23).** Flyway V8 (`problems` schema: problems, patterns, problem topics/patterns, versioned test cases, sources) and `/api/v1/admin/problems/**` (create/list/patch, tags, versioned test replacement, source, publish/unpublish/archive, patterns); snapshot publishing without hidden tests; verification gate. 12 integration tests + 5 unit tests; 5 mutants killed (hidden tests in snapshot, verification gate, version reset, test-count gate, source gate). ADR-044. **New Pending Decision P-9** (publishing before the judge exists; default: blocked). **Not verified:** large test data, performance; no way to reach `verified` yet (M3.3/Phase 4).

- **Phase 3 / M3.2 — Problem authoring UI — DONE (2026-11-23).** Problem list/create, editor with details + live statement preview, tags and pattern creation, test-case table (validation, versioned save, reorder), learner preview (samples only), source/publish panel showing the verification block; scoped problem setters. 9 Playwright specs (e2e suite 83 passed) + 5 unit tests (122 total); a preview-leak mutation was caught. ADR-045. **Not verified:** accessibility, mobile, very large test sets.

- **Phase 3 / M3.3 — Validation harness (design + trust boundary) — DONE (2026-11-23).** Flyway V9 (validation programs and runs), `ValidationExecutor` / `ProblemValidationApi` contracts for the judge, authoring API + editor panel for the three programs and run requests, strict `accept` path, invalidation rules, docs/10 §9. 12 integration tests (fake executor; proves end to end that a complete passing outcome verifies and unlocks publishing) + 1 Playwright spec (e2e suite 84 passed); 6 mutants killed. ADR-046. **Not verified / not built:** actual execution of the programs (needs the Phase 4 judge), input validator, special checkers. P-9 unchanged: publishing stays blocked until a real executor exists. Found on the way: the full browser suite exceeds the per-user API write limit (60/min), so the `local` profile now raises the limits (production defaults unchanged; rate-limit tests set their own).

- **Phase 3 / M3.4 — Public problem bank, bookmarks, status — DONE (2026-11-23).** Flyway V10 (bookmarks, user_problem_status); public list/detail/random/patterns driven by the published snapshot; premium gating through `EntitlementService`; bookmarks and state endpoints (per user, never on public responses); learner pages `/problems`, `/problems/[slug]`, `/problems/random`, `/my-problems`. 11 integration tests (6 mutants killed; one survived first and exposed that only an unfiltered list/random pick proves the status check) + 9 Playwright specs (e2e suite 93 passed). ADR-047. **Status is honest:** nothing writes `attempted`/`solved` until the judge exists (Phase 4). **Not verified:** accessibility, mobile, load.

**Phase 3 exit state:** problem model, taxonomy, authoring UI with versioned tests, validation-harness design with a strict trust boundary, and the public bank are in place and tested. What Phase 3 could not deliver without code execution: machine verification of test data, hence **no problem can be published through the API yet (Pending Decision P-9)**; E2E and tests use a database shortcut. Carried forward: editorials/solutions, company tags, problem sets, discussions, submissions/run.

**Phase 2 exit criterion (author and publish a lesson end to end; a learner can read it): met** — CMS e2e publishes a lesson that the reader e2e shows to anonymous and signed-in learners, with premium gating enforced server-side (stub entitlement until M6.1). Carried forward from Phase 2: bulk Markdown/JSON import-export, revision history and second-reviewer workflow, media assets, sidebar filters, lesson search, bookmarks/notes/highlights, certificates (see ADR-043).

**Phase 1 exit criterion (role-based admin access proven by tests): met** — API authorization matrix (IT), live checks against real tokens (`api-verify` 55), and browser E2E for every role boundary. Remaining Phase 1 leftovers are listed under Open items.

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
| P-9 | Publishing problems before the judge exists: wait for machine verification (default, follows docs/20 §8) or allow an explicit owner sign-off marked "not machine-verified" | **Blocked until verified** | M3.3 / Phase 4 |
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
- Dev realm: no social IdPs, `start-dev` HTTP — hardening in hosting. (Staff MFA is enforced since M1.5.)
- RustFS only partially verified (bucket create/list); `amazon/aws-cli:latest` unpinned.
- Native Postgres/Redis on this Mac occupy 5432/6379 → stack uses 5440/6380; another process also holds 5433.
- `.env` is git-ignored (dev values only).

## Known issues / debt (from M0.3)
- **CI verified on GitHub (2026-11-23):** all six CI jobs and CodeQL are green. The first run caught two portability bugs that passed locally (both fixed): `tsc` needed `next typegen` on a fresh clone, and `sh` (dash) has no `/dev/tcp`. Branch protection / required status checks are **not configured yet** (needs repo settings).
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
- The two **dev-only clients** (`engineerhub-dev-cli`, `engineerhub-dev-noaud`) now live only in `infra/keycloak/dev/overlay.json`; `make realm-check` keeps them out of the base realm. An existing dev realm must be deleted and re-imported to pick up realm changes.
- **Dependabot opened 5 PRs** (#1–#5: npm minor/patch group, TypeScript 5→7, ESLint 9→10, @types/node 20→26, RabbitMQ 4.3→4.4). Majors are *not* merged blindly: each is its own small milestone with `make verify`; ESLint 10 also depends on `eslint-config-next` support.
- The 17 files regenerated by spotless/google-java-format follow GJF 1.36.1 style.

- **Repository + CI live (2026-11-23):** project pushed to the public GitHub repo (6 milestone commits + 2 CI fixes); CI and CodeQL green. Standing instruction recorded: commit + push after every verified change set (CLAUDE.md rule 10).

## Known issues / debt (from M0.5)
- **HTTPS-only behaviour is unverified:** `Secure` cookies, the `__Host-` prefix, HSTS and `upgrade-insecure-requests` can only be exercised over https (dev is `http://localhost`, un-prefixed cookie names). Test when TLS exists (hosting decision H-1).
- Only Chromium is tested (no Firefox/WebKit). Dev-mode CSP is relaxed; only the production build was tested with the strict CSP.
- **No rate limiting** on `/auth/*` or `/api/proxy` yet (Phase 1 / edge). No MFA/step-up UI, no social login, no Keycloak back-channel logout (a user disabled in Keycloak is cut off at the next refresh ≈ ≤5 min).
- Redis is now a hard dependency of login; `SESSION_SECRET` rotation logs everyone out (no rotation scheme).
- ADR-028 open question: cacheable public pages (Phase 2) vs nonce CSP.
- No React component-test setup yet (RTL) — added with the first real components.
- E2E selectors depend on Keycloak's default login-theme element ids (`#username`, `#password`, `#kc-login`).
- The new CI `e2e` job is unproven on GitHub until pushed (see below).
- Dependabot PRs #1–#5 still open (majors not merged blindly).
- **CodeQL alert #1 (high) `java/spring-disabled-csrf-protection`** on `SecurityConfig` (`csrf.disable()`): a **false positive by design** — the API is a stateless bearer-token resource server with no cookies/session (ADR-025); the real CSRF defences are in the BFF (Origin + per-session token, tested). Left **open on purpose**: dismissing it ("won't fix"/"false positive" with that justification) is the owner's call. Do not "fix" it by hiding the call from the scanner.

## Known issues / debt (from M0.6)
- **No production consumer/producer yet:** the outbox, relay and idempotent handler are exercised only by tests and the live script (a test-only consumer). First real use: Phase 1 (e.g. user-provisioned/role-changed events, audit).
- **Relay runs in the API process.** `engineerhub.outbox.relay.enabled` can disable it per process; the `api`/`worker` profile split (docs/12) is not done.
- **Ordering is best-effort** (documented in ADR-031): consumers must not rely on global order.
- **Retries run in-memory on the consumer thread**: a slow failing message delays the queue behind it for up to ~1.5 s per attempt cycle; per-queue concurrency is the default (1).
- **No DLQ replay tooling**, only the manual runbook in docs/18. No alert rules (outbox backlog age, DLQ depth) — metrics `eh.outbox.pending/published/publish.failures/unroutable` exist but no Prometheus/Grafana yet.
- `eh.outbox.pending` is a gauge that queries the database on each scrape.
- RabbitMQ is single-node here; TLS to the broker, clustering/HA and quorum-queue behaviour under node loss are **unverified**. Only plain TCP to localhost was exercised.
- Messages with no bound queue are dropped by a topic exchange (counted + logged, still marked published); consumer queues must be declared by their owning module at start-up.
- Spring Modulith's own event externalization was not evaluated in depth (ADR-031).
- Outbox payloads should avoid secrets/PII (no automated check).
- CodeQL alert #1 (stateless API CSRF) still open for the owner's decision; Dependabot PRs #1–#5 still open.

## Next recommended task
**Phase 1 — Identity & platform core** (≈3–4 effort-weeks; docs/16): provision `identity.users` on first login (idempotent upsert keyed by Keycloak `sub`), roles/permissions/scoped-grant tables + seed + the authorization-matrix test suite generated from docs/08 §3, an append-only audit log (written through the outbox where useful), API-level rate limiting + request-size limits, admin shell + role management, MFA for admin accounts in the Keycloak realm, and cleaning up the dev realm's dev-only clients. Open decisions that matter now: none blocking. Still useful from you: weekly hours (T-1), and your call on CodeQL alert #1 and the Dependabot PRs.
