# EngineerHub

A Software Engineering Learning and Interview Preparation Platform: interactive courses, a secure online judge, interview prep, AI assistance, progress analytics, subscriptions and a CMS/admin console.

> **Status: in development.** Identity, content (CMS and reader), the problem bank, learner analytics and billing (payments, subscriptions, tax documents, refunds, reconciliation) are built and tested; the code judge (Phase 4) is not started, and payments are verified in Razorpay test mode only. See [docs/19-PROJECT-PROGRESS.md](docs/19-PROJECT-PROGRESS.md).

## Planned stack
Next.js · Spring Boot (Java 21) · PostgreSQL · Redis · RabbitMQ · Keycloak · S3/CloudFront · FastAPI (AI) · Docker · Terraform · AWS ECS.

## Documentation
Start at [docs/00-PRODUCT-VISION.md](docs/00-PRODUCT-VISION.md). Index:
architecture [03](docs/03-SYSTEM-ARCHITECTURE.md), security [09](docs/09-SECURITY-THREAT-MODEL.md), judge [10](docs/10-CODE-EXECUTION-ARCHITECTURE.md), roadmap [16](docs/16-DEVELOPMENT-ROADMAP.md), decisions [17](docs/17-ARCHITECTURAL-DECISIONS.md), content policy [20](docs/20-CONTENT-AND-LICENSING-POLICY.md), payments & tax [21](docs/21-PAYMENTS-AND-TAX.md).

## Getting started

**Requirements:** JDK 21 (set `JAVA_HOME`; the build fails fast on other versions), Node 24 LTS, Docker Desktop (running), `make`. Pinned versions: [docs/22-VERSION-COMPATIBILITY.md](docs/22-VERSION-COMPATIBILITY.md). Full setup notes: [docs/18-ENVIRONMENT-SETUP.md](docs/18-ENVIRONMENT-SETUP.md).

### Run the application locally
Run each of the last three in its own terminal, in this order:

```bash
make frontend-install   # once: install web dependencies from the lockfile
make dev-up             # Postgres, Redis, RabbitMQ, Keycloak, Mailpit, S3 (creates .env on first run)
make backend-run        # API on http://localhost:8080
make frontend-dev       # web app on http://localhost:3000
```

Open <http://localhost:3000>, click **Log in** and sign in with a dev user (all use the password in [docs/18](docs/18-ENVIRONMENT-SETUP.md#L45)): `learner`, `admin`, `superadmin`, `contentmanager`, `problemsetter`, `moderator`, `instructor`. Staff roles also ask for a one-time code. Mail sent by the app appears in Mailpit at <http://localhost:8025>.

Load the starter courses and problems (original content in `content/`) once the API is up: `make seed-content`. Browser tests clean up after themselves (`make clean-test-content` does it by hand).

To run the **production build** of the web app instead of the dev server (this is what the browser tests use): `make frontend-start`.

Stop and reset:

```bash
make dev-down    # stop the stack, keep data
make dev-reset   # stop the stack AND delete all local data (no confirmation)
make dev-ps      # show stack status
make dev-logs    # tail logs (SERVICE=keycloak to filter)
```

### Test payments (optional, Razorpay TEST mode only)
Put your **test** credentials in `.env` (it is git-ignored; never commit it): `RAZORPAY_KEY_ID` (`rzp_test_…`), `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`; keep `RAZORPAY_MODE=test`. Live keys are refused at startup. For webhooks, expose the API with a tunnel (for example `ngrok http 8080`) and register `https://<tunnel>/api/v1/webhooks/razorpay` in the Razorpay dashboard. Without credentials, payments are simply off. Details: [docs/21](docs/21-PAYMENTS-AND-TAX.md).

### Checks and tests

| Command | What it does |
|---|---|
| `make verify` | Everything CI runs: backend build and tests, frontend lint/types/format/unit tests, API contract |
| `make backend-test` | Backend tests only (needs Docker; includes module-boundary checks) |
| `make frontend-check` | Frontend lint, typecheck, format check, API-client sync, unit tests |
| `make e2e` | Browser tests (needs `make dev-up`, `make backend-run`, `make frontend-start`) |
| `make api-verify` | Live auth checks of the running API against real Keycloak tokens |
| `make msg-verify` | Live outbox check against RabbitMQ (restarts the broker container) |
| `make dev-verify` | Verify the running Docker stack end to end |
| `make scan` | Secret scan and known-vulnerable dependency scan |
| `make lint-scripts` | Lint shell scripts and GitHub workflows |

### Everyday helpers

| Command | What it does |
|---|---|
| `make format` | Auto-format backend (Spotless) and frontend (Prettier) |
| `make api-update` | Regenerate `openapi/engineerhub-v1.yaml` and the TypeScript client after an intentional API change |
| `make backend-build` | Package the backend jar (runs tests) |
| `make frontend-build` / `make frontend-lint` | Production build / lint of the web app |
| `make hooks` | Enable the git pre-commit hook (secret scan + formatting) |
| `make dev-env` | Create `.env` from `.env.example`, or append newly added variables |
| `make help` | List every command |
