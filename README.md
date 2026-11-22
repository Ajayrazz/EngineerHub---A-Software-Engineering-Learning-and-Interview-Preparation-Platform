# EngineerHub

A Software Engineering Learning and Interview Preparation Platform: interactive courses, a secure online judge, interview prep, AI assistance, progress analytics, subscriptions and a CMS/admin console.

> **Status: Phase 0 (foundations).** Architecture is documented; the repo has a verified backend/frontend skeleton only — no product features yet. See [docs/19-PROJECT-PROGRESS.md](docs/19-PROJECT-PROGRESS.md).

## Planned stack
Next.js · Spring Boot (Java 21) · PostgreSQL · Redis · RabbitMQ · Keycloak · S3/CloudFront · FastAPI (AI) · Docker · Terraform · AWS ECS.

## Documentation
Start at [docs/00-PRODUCT-VISION.md](docs/00-PRODUCT-VISION.md). Index:
architecture [03](docs/03-SYSTEM-ARCHITECTURE.md), security [09](docs/09-SECURITY-THREAT-MODEL.md), judge [10](docs/10-CODE-EXECUTION-ARCHITECTURE.md), roadmap [16](docs/16-DEVELOPMENT-ROADMAP.md), decisions [17](docs/17-ARCHITECTURAL-DECISIONS.md), content policy [20](docs/20-CONTENT-AND-LICENSING-POLICY.md), payments & tax [21](docs/21-PAYMENTS-AND-TAX.md).

## Getting started
Quality gates: `make verify`, `make scan`, `make hooks`. Requires JDK 21 and Node (24 LTS recommended). Then: `make help`, `make verify`. Local stack: `make dev-up` then `make dev-verify` (Docker Compose: Postgres, Redis, RabbitMQ, Keycloak, Mailpit, S3) — see [docs/18-ENVIRONMENT-SETUP.md](docs/18-ENVIRONMENT-SETUP.md). Pinned versions: [docs/22-VERSION-COMPATIBILITY.md](docs/22-VERSION-COMPATIBILITY.md).
