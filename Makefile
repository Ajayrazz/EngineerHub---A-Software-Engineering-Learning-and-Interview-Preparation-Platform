# EngineerHub developer commands. Run `make help`.
# Backend needs JDK 21 (the Maven build fails fast otherwise). On macOS we auto-select it.
# If a JDK 21 is found it overrides any JAVA_HOME already set in the shell (often a different JDK).
JDK21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
ifneq ($(JDK21),)
export JAVA_HOME := $(JDK21)
endif

.DEFAULT_GOAL := help
.PHONY: help backend-test backend-run backend-build frontend-install frontend-dev frontend-lint frontend-build test verify dev-env dev-up dev-down dev-reset dev-logs dev-ps dev-verify api-verify msg-verify format api-update hooks scan frontend-check frontend-start e2e lint-scripts seed-content clean-test-content

help: ## List commands
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*?## "}{printf "  %-18s %s\n",$$1,$$2}'

backend-test: ## Run backend tests (incl. module-boundary verification)
	cd backend && ./mvnw -B verify

backend-build: ## Package the backend jar (runs tests)
	cd backend && ./mvnw -B package

backend-run: dev-env ## Run the API locally on :8080 (profile 'local'; needs `make dev-up`)
	set -a && . ./.env && set +a && cd backend && SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run

frontend-install: ## Install frontend deps from the lockfile
	cd frontend && npm ci

frontend-dev: dev-env ## Run the web app on :3000 (needs make dev-up; sources .env)
	set -a && . ./.env && set +a && cd frontend && npm run dev

frontend-start: dev-env ## Build and serve the PRODUCTION web build on :3000 (what the E2E tests run against)
	set -a && . ./.env && set +a && cd frontend && npm run build && npm run start

e2e: dev-env ## Playwright browser tests (needs make dev-up, make backend-run, make frontend-start)
	set -a && . ./.env && set +a && cd frontend && E2E_CONTAINER_CONTROL=1 npx playwright test

frontend-lint: ## Lint the frontend
	cd frontend && npm run lint

frontend-build: ## Production build of the frontend
	cd frontend && npm run build

frontend-check: ## Frontend gates: lint, typecheck, format, API-client sync
	cd frontend && npm run check

format: ## Auto-format backend (Spotless) and frontend (Prettier)
	cd backend && ./mvnw -B -q spotless:apply
	cd frontend && npm run format

api-update: ## Accept an intentional API change: regenerate openapi/engineerhub-v1.yaml and the TS client
	cd backend && ./mvnw -B -q verify -Dit.test=OpenApiContractIT -Dopenapi.update=true
	cd frontend && npm run api:generate

hooks: ## Enable the git pre-commit hook (secret scan + formatting)
	git config core.hooksPath .githooks
	@echo "git hooks enabled (.githooks/pre-commit)"

lint-scripts: ## Lint shell scripts and GitHub workflows (shellcheck + actionlint)
	shellcheck -S warning scripts/*.sh .githooks/pre-commit infra/postgres/init/*.sh
	actionlint

scan: ## Security scans: secrets (gitleaks) + known-vulnerable dependencies (OSV, full resolved Maven tree via SBOM)
	gitleaks dir . --config .gitleaks.toml --no-banner --redact
	cd backend && ./mvnw -B -q org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeBom -DoutputFormat=json -DincludeTestScope=false
	osv-scanner scan source -L backend/target/bom.json -L frontend/package-lock.json

dev-env: ## Create .env from .env.example, or append any newly added variables (fake local values only)
	@./scripts/sync-env.sh

realm-check: ## Guard the prod-safe Keycloak realm base (no users, no dev clients, no localhost/wildcards, no literal secrets)
	python3 scripts/build-realm.py check

realm-dev: realm-check ## Build the dev realm import (base + dev overlay) into infra/keycloak/build/dev
	python3 scripts/build-realm.py build dev infra/keycloak/build/dev

dev-up: dev-env realm-dev ## Start the local stack (Postgres, Redis, RabbitMQ, Keycloak, Mailpit, S3) and wait until healthy
	docker compose up -d --wait --wait-timeout 240 postgres redis rabbitmq keycloak mailpit s3
	docker compose up s3-init   # one-shot job: creates buckets, exits 0 (kept so its log can be verified)

dev-down: ## Stop the stack (data volumes kept)
	docker compose down

dev-reset: ## Stop the stack AND delete all local data volumes (asks no questions!)
	docker compose down -v

dev-logs: ## Tail stack logs (SERVICE=name to filter)
	docker compose logs -f --tail=100 $(SERVICE)

dev-ps: ## Show stack status
	docker compose ps

dev-verify: ## Verify the running stack end to end (scripts/verify-dev-stack.sh)
	./scripts/verify-dev-stack.sh

msg-verify: ## Live outbox check against the real RabbitMQ incl. a broker outage (STOPS+restarts the rabbitmq container; needs make dev-up + make backend-run)
	./scripts/verify-messaging.sh

api-verify: ## Live authn/authz check of the RUNNING API against real Keycloak tokens (needs make dev-up + make backend-run)
	./scripts/verify-api-auth.sh

test: backend-test frontend-check ## All automated checks available today

verify: realm-check test frontend-build ## Everything CI should run (grows as milestones land)

seed-content: ## Load the original courses and problems (content/*.json) into the LOCAL stack (needs dev-up + backend-run)
	node scripts/seed-content.mjs

clean-test-content: ## Remove browser-test leftovers from the LOCAL database (e2e runs this automatically afterwards)
	./scripts/clean-test-content.sh
