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
Integration tests need Docker running (Testcontainers starts a throw-away Postgres 18). The same gates run in `.github/workflows/ci.yml` — **not yet executed on GitHub** (no repository yet).

## Local stack (verified in M0.2)
```bash
make dev-up        # copies .env.example -> .env if missing, starts the stack, waits until healthy
make dev-verify    # 24 end-to-end checks (see below)
make dev-ps        # status        | make dev-logs SERVICE=keycloak
make dev-down      # stop, keep data   | make dev-reset  # stop AND delete all local data
make backend-run   # API on :8080, profile 'local' (needs make dev-up; JDK 21 auto-selected; sources .env)
make api-verify    # 33 live authn/authz checks against the running API + real Keycloak tokens
make frontend-dev  # Next.js on :3000
```
First start pulls images (a few minutes) and needs ~2–3 GB of Docker RAM (Docker Desktop was set to 8 GB here).

| Service | Image (pinned) | Host address (127.0.0.1 only) | Notes |
|---|---|---|---|
| PostgreSQL | `postgres:18` (18.7 at verification) | **5440** (`POSTGRES_HOST_PORT`) | Two DBs/users created by `infra/postgres/init`: `engineerhub` (app) and `keycloak` (isolated; app user cannot connect to it) |
| Redis | `redis:8` | **6380** (`REDIS_HOST_PORT`) | Password required, AOF on |
| RabbitMQ | `rabbitmq:4.3.8-management` | 5672, UI http://localhost:15672 | vhost `engineerhub`; default `guest` account disabled |
| Keycloak | `quay.io/keycloak/keycloak:26.8.2` | http://localhost:8081 | `start-dev` (dev mode, plain HTTP). Realm `engineerhub` imported from `infra/keycloak/realm/engineerhub-realm.json` |
| Mailpit | `axllent/mailpit:v1.34.0` | SMTP 1025, UI http://localhost:8025 | Replaces MailHog (unmaintained) |
| S3-compatible store | `rustfs/rustfs:1.0.6` (Apache-2.0) | API 9000, console 9001 | Replaces MinIO (MinIO no longer publishes community images). Buckets `engineerhub-media`, `engineerhub-testdata` created by `s3-init`. App code talks plain S3 API only (ADR-019), so this is swappable |

Host ports 5440/6380 are non-default on purpose: this machine already runs a native Postgres (5432) and Redis (6379). Change them in `.env` if they clash.

### Dev-only identities (fake; never reuse)
Keycloak admin console `http://localhost:8081` → `kcadmin` / `dev-only-kc-admin-pw` (master realm).
Realm `engineerhub` users, all with password `Dev-Only-Pass-1!`: `learner` (no staff role), `superadmin` (SUPER_ADMIN), `admin` (ADMIN), `contentmanager` (CONTENT_MANAGER), `problemsetter` (PROBLEM_SETTER), `moderator` (MODERATOR), `instructor` (INSTRUCTOR).
OIDC client `engineerhub-web` (confidential; authorization-code + PKCE S256; no implicit/password grants; redirect `http://localhost:3000/auth/callback`).

### API ↔ database ↔ Keycloak (verified in M0.4)
With the stack up, `make backend-run` starts the API (profile `local`): Flyway applies `V1__shared_baseline.sql` to the `engineerhub` database as the least-privilege app user, Hibernate validates the mapping, and the JWT decoder talks to Keycloak at `http://localhost:8081/realms/engineerhub`. Swagger UI is on in this profile only: http://localhost:8080/swagger-ui/index.html. `make api-verify` then exercises login-less API access with real tokens (dev clients `engineerhub-dev-cli`, `engineerhub-dev-noaud`). Production needs `DB_URL`, `DB_USER`, `DB_PASSWORD`, `KEYCLOAK_ISSUER_URI` (see `.env.example`); the API refuses to start without them.

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
