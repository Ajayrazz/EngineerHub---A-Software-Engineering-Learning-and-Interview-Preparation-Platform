# Changelog

Format: [Keep a Changelog](https://keepachangelog.com/), versioning: SemVer once releases begin.

## [Unreleased]
### Added
- Initial product, requirements, architecture, security and roadmap documentation (docs 00–19).
- Docs 20 (content & licensing policy) and 21 (Razorpay payments & GST design).
### Changed
- Recorded product-owner decisions (2026-11-23): EngineerHub branding; custom isolate-based judge (Judge0 rejected) with Java/Python/C++ + stdin/stdout; Razorpay-only payments; self-hosted Keycloak; progressive roadmap with content track; free/OSS-first infrastructure. Updated docs 01, 08, 10, 13, 16, 17, 18, 19, `.env.example`.
- (2026-11-23, round 2) Billing redesigned for four purchase options on one core (doc 21 rewritten); tax-inclusive pricing with no default GST rate; configurable refund policy + admin refund workflow; Live Readiness Gate; portable hosting options (doc 15 rewritten, banners on docs 03/04/13); solo-developer roadmap (doc 16 rewritten); content authoring workflow (doc 20 §8); ADR-018…022; DB/API addenda.
- **M0.1 (2026-11-23):** monorepo scaffold — `backend/` (Spring Boot 4.1.3, Java 21, Maven wrapper, Spring Modulith boundary test, `/api/v1/system/ping` smoke endpoint, JDK-21 enforcer), `frontend/` (Next.js 16.4.6, React 19.3, Tailwind 4.3.3), `Makefile`, `.gitignore`, `.editorconfig`, `.nvmrc`, `CLAUDE.md`, PR template, `docs/22-VERSION-COMPATIBILITY.md`.
### Changed
- Frontend package manager: pnpm → npm (docs 18/22). Spring Boot target: 3.5 → 4.1.x (3.5 EOL).
- **M0.2 (2026-11-23):** `docker-compose.yml` dev stack (Postgres 18, Redis 8, RabbitMQ 4.3.8, Keycloak 26.8.2 + realm-as-code, Mailpit, RustFS S3), `infra/` configs, `scripts/verify-dev-stack.sh` (24 checks), `make dev-*` targets, rewritten `.env.example`; ADR-023.
- **M0.3 (2026-11-23):** quality gates — Spotless, failsafe + Testcontainers IT, springdoc + committed OpenAPI contract with drift test, Prettier/typecheck/openapi-typescript client + sync check, gitleaks + pre-commit hook, OSV scan via CycloneDX SBOM, actionlint, GitHub workflows (`ci.yml`, `codeql.yml`), Dependabot; new Makefile targets (`format`, `api-update`, `hooks`, `scan`, `frontend-check`); ADR-024.
- **M0.4 (2026-11-23):** backend skeleton — JPA + Flyway V1 baseline (PostgreSQL 18), RFC 9457 error model with stable codes and correlation ids, stateless JWT resource server (issuer/audience validation, Keycloak realm-role allowlist, 401/403/503 problem+json, security headers, CORS allowlist), method-security policy rule (ArchUnit), idempotency ledger + service, UUIDv7 ids, UTC clock, ECS JSON logging, diagnostic endpoints (`ping`, `whoami`, `admin/ping`), `scripts/verify-api-auth.sh`, `make api-verify`; dev-only Keycloak clients; ADR-025…027; 66 backend tests.
