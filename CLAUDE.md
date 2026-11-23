# EngineerHub — working agreement for Claude

Solo project: one developer/owner + Claude. Read `docs/` before changing anything; start with `docs/19-PROJECT-PROGRESS.md` (state), `docs/16-DEVELOPMENT-ROADMAP.md` (plan), `docs/17-ARCHITECTURAL-DECISIONS.md` (ADRs), `docs/22-VERSION-COMPATIBILITY.md` (pinned versions).

## Layout
- `backend/` Spring Boot 4.1.x, Java 21, Maven, **modular monolith** (Spring Modulith; boundaries verified by `ModularityTests`).
- `frontend/` Next.js 16 (App Router), React 19, Tailwind 4, npm. **`frontend/AGENTS.md` says this Next.js differs from training data: read `frontend/node_modules/next/dist/docs/` before writing Next code.**
- `judge-worker/`, `infra/` — not created yet (Phase 4 / hosting decision).
- `docs/` — source of truth for requirements and design.

## Commands
`make help` · `make dev-up` / `make dev-verify` / `make dev-down` (Docker stack; needs Docker Desktop running) · `make verify` (all gates incl. 149 frontend unit tests) · `make e2e` (105 Playwright browser tests; needs `make dev-up`, `make backend-run`, `make frontend-start`) · `make backend-run` + `make api-verify` (live API ↔ Keycloak checks) · `make msg-verify` (live outbox ↔ RabbitMQ check; restarts the broker container) · `make format` · `make api-update` (after any API change — the OpenAPI contract test and TS-client check fail otherwise) · `make scan` · `make hooks` (pre-commit secret scan) · `make backend-test` · `make frontend-check`. Dev host ports: Postgres 5440, Redis 6380, Keycloak 8081, RabbitMQ UI 15672, Mailpit 8025, S3 9000. Backend requires **JDK 21** (`JAVA_HOME`); the build fails fast on other versions.

## Rules
1. Understand first; inspect existing code before editing; incremental, one milestone at a time.
2. A feature is done only when implemented **and** tested; never claim otherwise; no fake/placeholder features.
3. Security-first: backend is authoritative for authn/authz/entitlements; every endpoint has an explicit policy and negative tests; DTOs only (no entity binding); ownership from the security context; no secrets or PII in logs/code. Checklist: `docs/09-SECURITY-THREAT-MODEL.md` §8.
4. Modules talk via public `api` packages or events — never another module's repositories.
5. Every controller method needs `@PreAuthorize` or `@PermitAll` (enforced by `EndpointPolicyArchTest`); errors via `ApiException`/`ErrorCode` (problem+json, never leak internals); ids via `Ids.newId()`; time via injected `Clock`. DB changes only via Flyway, backward compatible. UUIDv7 ids for domain entities.
6. No new library/framework without an ADR entry. Prefer free/OSS, portable (Docker + env config, no cloud-proprietary code).
7. Content: original or licensed only; never paste third-party problem/lesson text (`docs/20`).
8. Payments: Razorpay **test mode only**; never fabricate legal/tax data; follow `docs/21`. Pending Decisions (docs/19) must not be silently assumed.
9. After each milestone update `docs/19-PROJECT-PROGRESS.md`, CHANGELOG, and affected docs; report what was *not* verified.
10. **Git workflow (standing instruction from the owner, 2026-11-23):** after each completed, verified change set, commit with meaningful Conventional Commit messages (small logical commits, body explains *why*) and push to `origin/main` (https://github.com/Ajayrazz/EngineerHub---A-Software-Engineering-Learning-and-Interview-Preparation-Platform). Never force-push, never use `--no-verify` (the pre-commit hook scans for secrets), never commit `.env`. After pushing, check the GitHub Actions run and fix failures. The repo is **public**: no real secrets, personal data, or non-public business details in any file.
