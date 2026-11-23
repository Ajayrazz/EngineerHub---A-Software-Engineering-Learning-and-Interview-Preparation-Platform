# 18 — Environment Setup

> Status: **Real as of M0.1 + M0.2** (commands below were run and verified on macOS/Apple Silicon, 2026-11-23). Judge-worker section is still a plan.

## Required tooling
Java 21 (Temurin; Maven wrapper included), Node 24 LTS + npm (`.nvmrc`), Docker Desktop (Compose v2). Quality/security CLIs (installed on this machine via Homebrew in M0.3): `gitleaks`, `osv-scanner`, `actionlint` (`brew install gitleaks osv-scanner actionlint`). Later: Terraform/OpenTofu (only after the hosting decision), Python 3.12 (AI service).

## Quality gates (verified in M0.3)
```bash
make verify        # backend (format, unit+integration tests incl. Testcontainers, Modulith, OpenAPI contract) + frontend (lint, typecheck, format, API-client sync, build)
make format        # auto-fix formatting (Spotless + Prettier)
make api-update    # after an intentional API change: regenerate openapi/engineerhub-v1.yaml + TS client
make scan          # gitleaks (secrets) + OSV (dependencies, via SBOM)
make hooks         # enable git pre-commit hook (secret scan, .env guard, formatting)
```
Integration tests need Docker running (Testcontainers starts a throw-away Postgres 18). The same gates run in `.github/workflows/ci.yml` — verified green on GitHub-hosted Ubuntu runners (2026-11-23), including the live Keycloak auth checks.

## Local stack (verified in M0.2)
```bash
make dev-up        # copies .env.example -> .env if missing, starts the stack, waits until healthy
make dev-verify    # 24 end-to-end checks (see below)
make dev-ps        # status        | make dev-logs SERVICE=keycloak
make dev-down      # stop, keep data   | make dev-reset  # stop AND delete all local data
make backend-run   # API on :8080, profile 'local' (needs make dev-up; JDK 21 auto-selected; sources .env)
make api-verify    # 33 live authn/authz checks against the running API + real Keycloak tokens
make frontend-dev  # Next.js dev server on :3000 (sources .env; needs make dev-up + make backend-run)
make frontend-start # production build served on :3000 (what E2E tests)
make e2e           # Playwright browser tests: needs dev-up + backend-run + frontend-start; first time: (cd frontend && npx playwright install chromium)
```
First start pulls images (a few minutes) and needs ~2–3 GB of Docker RAM (Docker Desktop was set to 8 GB here).

| Service | Image (pinned) | Host address (127.0.0.1 only) | Notes |
|---|---|---|---|
| PostgreSQL | `postgres:18` (18.7 at verification) | **5440** (`POSTGRES_HOST_PORT`) | Two DBs/users created by `infra/postgres/init`: `engineerhub` (app) and `keycloak` (isolated; app user cannot connect to it) |
| Redis | `redis:8` | **6380** (`REDIS_HOST_PORT`) | Password required, AOF on |
| RabbitMQ | `rabbitmq:4.3.8-management` | 5672, UI http://localhost:15672 | vhost `engineerhub`; default `guest` account disabled |
| Keycloak | `quay.io/keycloak/keycloak:26.8.2` | http://localhost:8081 | `start-dev` (dev mode, plain HTTP). Realm `engineerhub` built by `make realm-dev` from `infra/keycloak/base/realm.json` (prod-safe) + `infra/keycloak/dev/overlay.json` (fake users, dev clients) into the gitignored `infra/keycloak/build/dev/` |
| Mailpit | `axllent/mailpit:v1.34.0` | SMTP 1025, UI http://localhost:8025 | Replaces MailHog (unmaintained) |
| S3-compatible store | `rustfs/rustfs:1.0.6` (Apache-2.0) | API 9000, console 9001 | Replaces MinIO (MinIO no longer publishes community images). Buckets `engineerhub-media`, `engineerhub-testdata` created by `s3-init`. App code talks plain S3 API only (ADR-019), so this is swappable |

Host ports 5440/6380 are non-default on purpose: this machine already runs a native Postgres (5432) and Redis (6379). Change them in `.env` if they clash.

### Dev-only identities (fake; never reuse)
Keycloak admin console `http://localhost:8081` → `kcadmin` / `dev-only-kc-admin-pw` (master realm).
Realm `engineerhub` users, all with password `Dev-Only-Pass-1!`: `learner` (no staff role), `superadmin` (SUPER_ADMIN), `admin` (ADMIN), `contentmanager` (CONTENT_MANAGER), `problemsetter` (PROBLEM_SETTER), `moderator` (MODERATOR), `instructor` (INSTRUCTOR).
OIDC client `engineerhub-web` (confidential; authorization-code + PKCE S256; no implicit/password grants; redirect `http://localhost:3000/auth/callback`).

### API ↔ database ↔ Keycloak (verified in M0.4)
With the stack up, `make backend-run` starts the API (profile `local`): Flyway applies `V1__shared_baseline.sql` to the `engineerhub` database as the least-privilege app user, Hibernate validates the mapping, and the JWT decoder talks to Keycloak at `http://localhost:8081/realms/engineerhub`. Swagger UI is on in this profile only: http://localhost:8080/swagger-ui/index.html. `make api-verify` then exercises login-less API access with real tokens (dev clients `engineerhub-dev-cli`, `engineerhub-dev-noaud`). Role assignment also needs `KEYCLOAK_ADMIN_CLIENT_ID` / `KEYCLOAK_ADMIN_CLIENT_SECRET` (the service-account client in the realm; without them the role endpoints answer 503). Staff dev users (`superadmin`, `admin`, …) have a fixed fake TOTP seed for browser login (see `frontend/e2e/totp.ts`); the scripted dev clients bypass OTP. To apply realm changes to an existing dev stack, delete the `engineerhub` realm in the Keycloak console (or `make dev-reset`) and run `make dev-up`. Payments (test mode only) need `RAZORPAY_KEY_ID` (`rzp_test_...`), `RAZORPAY_KEY_SECRET` and `RAZORPAY_WEBHOOK_SECRET` in `.env`; blank means payments are off and `/pricing` shows no Buy button. Razorpay must reach `POST /api/v1/webhooks/razorpay` of your API (a public URL, e.g. a tunnel) with the same webhook secret configured in its dashboard, events `payment.captured`, `order.paid` and `payment.failed`. Production needs `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI` (see `.env.example`); the API refuses to start without them.

### What `make dev-verify` proves (24 checks)
Postgres: both DB users work, **cross-DB isolation**. Redis: auth required. RabbitMQ: API, vhost, guest rejected. Keycloak: discovery, PKCE S256, issuer, JWKS, admin API, staff roles imported, **no PREMIUM/FREE roles (ADR-006)**, 7 users, client-secret env substitution, client flow flags, realm security settings (brute force, 5-min access tokens, refresh rotation), **real email Keycloak → Mailpit**. Mailpit up. S3 buckets exist.

### Known limitations (honest list)
- **No interactive browser login has been tested yet** — the Next.js BFF ↔ Keycloak flow is milestone M0.5 (API-side token validation *is* verified, with password-grant tokens).
- Dev realm does **not** enforce admin MFA, has no Google/GitHub IdPs, and runs Keycloak in `start-dev` (HTTP). Production realm/hardening is Phase 1 / hosting phase.
- RustFS is a young project (1.0.x): only plain bucket create/list is verified. Presigned-URL upload/download is verified when the storage interface is built; fallback options: Garage, SeaweedFS, or a cloud S3-compatible service.
- Dev compose is single-node, no TLS, not a production topology (docs/15).
- `finguard-*` containers on this machine belong to another project and are untouched.

## Judge worker on a Mac (your dev machine is macOS/Apple Silicon)
`isolate` needs Linux (namespaces + cgroups v2). Plan: run a Linux VM (Lima, Multipass, or OrbStack) with cgroup v2, install isolate + g++/OpenJDK/Python there, and run the judge worker inside it connected to the Compose RabbitMQ. Docker-Desktop `--privileged` containers are acceptable **for dev only**. Exact commands will be written and verified in Phase 4a — nothing here is tested yet. Note Apple Silicon = arm64 VM; production images must also be built/tested for the target arch (likely x86_64 or arm64 Graviton — decide with Q-E).

## Notes
- Judge worker is disabled locally unless explicitly enabled with the unsafe-dev flag (see doc 10/13).
- macOS (Apple Silicon): use arm64 images; Firecracker/gVisor are Linux-only → test judge hardening in a Linux VM/CI.
- Never commit `.env`.

### Web app login (verified in M0.5)
With the stack up, `make backend-run` (API :8080) and `make frontend-dev` (web :3000), open http://localhost:3000 → "Log in" → Keycloak → sign in as e.g. `learner` / `Dev-Only-Pass-1!` (or `admin`, `superadmin`, …). `make dev-env` now also *appends* variables newly added to `.env.example` to your existing `.env` (never overwrites). New variables for the web app: `APP_URL`, `REDIS_URL`, `SESSION_SECRET` (≥ 32 chars; derives the session encryption keys), optional `SESSION_IDLE_TTL_SECONDS` / `SESSION_ABSOLUTE_TTL_SECONDS`. `make e2e` includes a test that **stops and restarts the Keycloak container** (gated by `E2E_CONTAINER_CONTROL=1`, set by the Make target).

### Messaging (verified in M0.6)
`make msg-verify` runs 20 live checks of the transactional outbox against the real RabbitMQ — **it stops and restarts the `rabbitmq` container** (needs `make dev-up` + `make backend-run`). RabbitMQ management UI: http://localhost:15672 (user/password from `.env`, vhost `engineerhub`).

**Runbook (manual, no tooling yet):**
- *Outbox backlog:* `select count(*), min(created_at) from shared.outbox where published_at is null;` — growing = broker down or relay not running. Failing rows: `select id, event_type, attempts, last_error from shared.outbox where attempts > 0;`.
- *Dead letters:* queue `<consumer-queue>.dlq` in the management UI (Get messages: the original message, its `messageId` = event id, and an `x-death` header with the reason). *Replay:* after fixing the cause, move them back to the events exchange with a Shovel or `rabbitmqadmin publish` (routing key = the event's `type`); the consumer's idempotency ledger makes a replay of an already-processed event harmless. A replay tool is a TODO.
- *Stop publishing:* set `engineerhub.outbox.relay.enabled=false` and restart that process; events accumulate safely in PostgreSQL.
