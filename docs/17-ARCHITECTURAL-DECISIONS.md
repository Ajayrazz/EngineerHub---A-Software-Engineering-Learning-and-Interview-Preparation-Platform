# 17 — Architectural Decision Records

Format: Context → Decision → Alternatives → Consequences. Status: `Proposed` until user approval.

## ADR-001 Modular monolith first — *Proposed*
**Decision:** Single Spring Boot deployable with Modulith-verified modules; judge, AI, web separate. **Alternatives:** microservices day one (operational burden, premature); plain layered monolith (boundary erosion). **Consequences:** simpler ops/transactions; discipline needed; extraction path via events.

## ADR-002 Keycloak for identity, Next.js BFF holds tokens — *Proposed*
**Alternatives:** Spring Authorization Server; Auth0/Cognito (SaaS, cost/lock-in); tokens in browser (XSS risk). **Consequences:** strong security posture, extra service to run; BFF adds a hop.

## ADR-003 PostgreSQL as single source of truth, schema-per-module — *Proposed*
**Alternatives:** polyglot early (Mongo, ES). **Consequences:** FTS/pgvector reduce infra; revisit OpenSearch when FTS insufficient.

## ADR-004 Transactional outbox + RabbitMQ with idempotent consumers — *Proposed*
**Alternatives:** direct publish after commit (dual-write loss), Kafka (heavier; not needed yet). **Consequences:** at-least-once semantic; requires idempotency everywhere.

## ADR-005 Judge: custom worker on `isolate` in a no-egress VM; Judge0 rejected — *Accepted (2026-11-23)*
**Context:** must be free, secure, maintainable, Spring-integrable; Java/Python/C++ stdin/stdout first. **Decision:** thin custom worker over `isolate` (namespaces+cgroups v2) on dedicated no-egress VMs, behind a swappable `Sandbox` interface. **Alternatives:** Judge0 CE (duplicates our queue/state/test-data model, extra Ruby stack, cgroup/privilege constraints, CVE-cadence dependency), nsjail (viable equal), Piston, Docker-only (rejected), Fargate per run (slow/costly), Firecracker/gVisor (later hardening). **Consequences:** ~2–3 weeks more build effort than Judge0; full control of hidden tests, drivers, checkers; Linux-only dev needs a VM; compute cost for worker VMs; shared-kernel risk mitigated by no-egress/disposable hosts and later gVisor/Firecracker. Details: doc 10 §1. Spike in Phase 4a may revise if assumptions about isolate/cgroup v2 on the target OS fail.

## ADR-006 Premium/free are entitlement states, not roles — *Proposed*
**Context:** brief lists `PREMIUM_USER`, `FREE_USER` as roles. **Decision:** derive from subscription; expose as computed authorities. **Why:** avoids drift between payment state and role assignments; expiry is automatic. **Consequence:** role table holds staff roles only.

## ADR-007 UUIDv7 ids for domain entities; bigint for append-only logs — *Accepted; generator implemented (M0.4)*
Index locality vs. UUIDv4; non-guessable public ids. **Generated in Java** (`shared.id.Ids`, ~20 lines, RFC 9562 layout, unit-tested) rather than by PostgreSQL 18's native `uuidv7()`, because services need the id before the INSERT (events, outbox, response bodies) and it keeps us independent of the DB version. No entity uses it yet (first ones arrive in Phase 1).

## ADR-008 Content stored as structured JSON blocks, not HTML — *Proposed*
XSS safety, renderer control, versioning, highlight anchoring; cost: need renderer + migration tooling for schema versions.

## ADR-009 Visualizations are deterministic traces, not AI-generated — *Accepted by brief*

## ADR-010 Highlights anchored by block + offset + quoted text — *Proposed*
Graceful orphaning when content changes.

## ADR-011 Postgres FTS + pg_trgm for search initially — *Proposed*
Revisit OpenSearch at scale/relevance needs.

## ADR-012 Spring Modulith + ArchUnit added to the stack — *Proposed* (justification in doc 12).

## ADR-013 Own product identity/content — *Accepted (2026-11-23)*
Brand: **EngineerHub**. Reference product is a real third-party commercial platform; we reproduce neither content nor branding. Original/licensed content only; see doc 20.

## ADR-014 Razorpay-only payments behind a provider interface — *Accepted (2026-11-23)*
**Decision:** `PaymentProvider` interface with a single `RazorpayProvider`; provider-neutral schema; Stripe deferred. Webhook = source of truth; client signature verification only for UX. MVP purchase model recommended as fixed-term passes (Orders) with auto-renew Subscriptions as a later mode (pending Q-A). GST/invoicing config-driven. Details: doc 21.

## ADR-015 Self-hosted Keycloak — *Accepted (2026-11-23)*
**Decision:** run Keycloak ourselves (ECS Fargate, dedicated Postgres schema/DB, realm-as-code, HA ≥2 nodes in prod). **Consequences:** no per-MAU SaaS cost; we own upgrades, backups, theming, patching. Mitigations: pin versions, realm export in git, upgrade runbook, staging rehearsal, backups of its DB, admin console not publicly exposed. See doc 08 §5.

## ADR-016 Progressive delivery, content as a first-class track — *Accepted (2026-11-23)*
MVP = Phases 0–5; AI/mock/community/newsletter post-MVP; content production runs in parallel with tooling built first. See doc 16.

## ADR-017 Free/open-source-first infrastructure — *Accepted (2026-11-23)*
Default to OSS (Keycloak, Postgres+pgvector, Redis, RabbitMQ, isolate, MinIO dev, Prometheus/Grafana/OTel); paid services only where self-hosting is clearly worse (cloud compute, SES, Razorpay fees). Open: Amazon MQ vs self-run RabbitMQ and managed vs self-run Redis (cost vs ops) — default self-run RabbitMQ in containers for MVP to avoid a fixed monthly broker cost (OQ-10 revised).

## ADR-018 Unified billing core: AccessPeriods fed by mode-specific adapters — *Accepted (2026-11-23)*
**Context:** owner wants monthly/annual passes **and** auto-renewing monthly/annual subscriptions. **Decision:** every successful payment (order or subscription charge) is handled by one `PaymentCapturedHandler` that appends an immutable `AccessPeriod`; entitlements derive only from periods; `RenewalPolicy` strategies and Razorpay adapters hold all mode-specific logic. **Alternatives:** separate flows per mode (duplicated logic, divergent bugs); storing a mutable `expires_at` on user (loses history, hard to refund/audit). **Consequences:** refunds/revocation/overlap rules are uniform; small extra table; recurring can ship after passes without redesign. Pending: P-1…P-8 (doc 21 §11). Supersedes the earlier "fixed-term first" recommendation in ADR-014.

## ADR-019 Portable, cloud-agnostic deployment — *Accepted (2026-11-23)*
**Decision:** Docker images + env config; cloud-shaped capabilities behind interfaces (`ObjectStorage` S3-compatible, `MailSender` SMTP, `SecretProvider`); CDN/WAF optional; provider IaC isolated under `infra/<provider>/` after H-1. AWS docs 03/04/13 are a *reference deployment*. **Consequences:** slight abstraction cost; lets us start on cheap VMs and move later. See doc 15.

## ADR-020 Solo-developer delivery model — *Accepted (2026-11-23)*
Small milestones with strict definition of done, automation-first workflow, tests/security over speed, content built via CMS by the owner with Claude assistance (doc 20 §8), no feature marked complete before implemented and tested. See doc 16.

## ADR-021 Tax-inclusive pricing with configurable, un-defaulted GST — *Accepted (2026-11-23)*
Displayed price = final payable; tax is extracted per configured rules; no rate assumed; `UNCONFIGURED` blocks live charges; invoices store immutable snapshots. See doc 21 §5.

## ADR-022 Live payment readiness gate — *Accepted (2026-11-23)*
Code-enforced block on live Razorpay charging until legal entity, KYC, tax mode, policies and real seller data are recorded. See doc 21 §9.

## ADR-023 Dev-stack component substitutions — *Accepted (2026-11-23)*
**MailHog → Mailpit** (MailHog unmaintained). **MinIO → RustFS** for the local S3-compatible store: MinIO no longer provides community container images (verified: pulls unavailable), RustFS is Apache-2.0 and actively developed. **Consequence:** RustFS is young; the app must use only standard S3 operations behind the `ObjectStorage` interface (ADR-019) so Garage/SeaweedFS/cloud S3 can replace it; presigned URLs to be verified when first used. Dev host ports for Postgres/Redis default to 5440/6380 to avoid clashing with native installs.

## ADR-024 CI & security tooling choices — *Accepted (2026-11-23)*
**Decisions:** (1) **Contract-first API gate:** a test compares the generated OpenAPI spec with the committed `openapi/engineerhub-v1.yaml`; the TS client is generated from it and a second check fails on drift. (2) **Scanners run as pinned CLI/containers, not marketplace actions** (gitleaks, OSV-Scanner, actionlint); third-party actions are pinned to commit SHAs. (3) **SCA = OSV-Scanner on a CycloneDX SBOM + npm lockfile**: scanning `pom.xml` alone silently skipped Boot-managed dependencies. (4) **Trivy deferred** (doc 09 originally named it): there are no Dockerfiles/images to scan yet, and I recall reports of a supply-chain compromise of Trivy's GitHub Action — I could not verify details here, so re-evaluate (and verify release integrity) when container images exist. (5) **Local git hook** (`make hooks`) instead of the Python `pre-commit` framework — no extra tool for a solo developer. (6) API docs/Swagger UI are **off by default** and only enabled in the `local` profile. **Alternatives:** OWASP Dependency-Check (needs NVD key, slow), Snyk (paid tiers), pre-commit framework. **Consequence:** GitHub-side behaviour (workflows, Dependabot, CodeQL, branch protection) is unverified until a repository exists.

## ADR-025 Method security as the single enforcement point; permissive URL layer guarded by a build-time rule — *Accepted (2026-11-23, M0.4)*
**Context:** public endpoints (course catalogue, health) must coexist with protected ones. **Decision:** the filter chain authenticates (stateless JWT) but authorizes nothing at URL level (`anyRequest().permitAll()`); every handler method must carry `@PreAuthorize` or `@PermitAll`, enforced by `EndpointPolicyArchTest` (with a negative control proving the rule can fail). Anonymous calls to protected methods → 401, wrong role → 403, both problem+json. **Alternatives:** URL-pattern allowlists in the security config (duplicated, drifts from controllers); `anyRequest().authenticated()` plus a public-path list (same drift). **Consequences:** one place to reason about access; the safety net is a CI test — a developer who bypasses CI could ship an open endpoint, so the test must stay mandatory. CSRF is disabled because the API only accepts bearer tokens and keeps no session (browser CSRF defences live in the BFF).

## ADR-026 Identity-provider outage is a 503, not a 500/401 — *Accepted (2026-11-23, M0.4)*
Found by an integration test: with Keycloak unreachable, token validation threw an unhandled exception (500, non-problem body). `ResilientJwtDecoder` maps decoder-initialisation and JWKS-retrieval failures to `IdentityProviderUnavailableException` → `503` problem+json with `Retry-After: 5`; genuinely invalid tokens stay 401. The decoder is lazy, so the API boots while Keycloak is down (public endpoints keep working) and recovers by itself (verified live).

## ADR-027 Structured JSON logs with correlation ids — *Accepted (2026-11-23, M0.4)*
Spring Boot's built-in ECS structured logging (no extra library) is the default; the `local` profile uses readable text. `CorrelationIdFilter` (highest precedence) accepts an inbound `X-Correlation-Id` only if it matches `[A-Za-z0-9._-]{8,64}`, otherwise generates a UUIDv7, puts it in the MDC, echoes it in the response header and in every problem+json. **Known risk:** unexpected exceptions are logged with message + stack trace; code must never put secrets/PII in exception messages (log scrubbing is future work).

## ADR-028 Cache Components OFF; per-request CSP nonce on dynamically rendered pages — *Accepted (2026-11-23, M0.5)*
**Context:** the Next.js 16.4 scaffold enabled `cacheComponents`/`partialPrefetching`, which prerender a static shell. A nonce-based CSP needs the nonce in the HTML, i.e. per-request rendering; prerendered HTML cannot carry it (Next docs, CSP guide). **Decision:** disable Cache Components, render every page per request (`await connection()` in the root layout) and set `Content-Security-Policy` with a fresh nonce + `strict-dynamic` in `proxy.ts` (Next 16's replacement for middleware). Production CSP has no `unsafe-inline`/`unsafe-eval` (verified: no violations in Chromium on tested pages). **Consequence / open question:** public, cacheable content pages (lessons/problems for SEO, Phase 2) cannot use per-request nonces; they will need a different strategy (hash-based CSP/SRI, or a separate route group served with a static CSP) — decide when those pages are built.

## ADR-029 BFF session design: `openid-client` + node-redis, server-side encrypted sessions — *Accepted (2026-11-23, M0.5)*
**Decision:** certified OIDC library `openid-client` v6 (discovery, PKCE S256, state/nonce, ID-token validation, refresh, RP-initiated logout); sessions in Redis: browser holds only a 256-bit opaque id in an `HttpOnly; SameSite=Lax` cookie (`__Host-` prefixed + `Secure` over HTTPS); Redis key = HMAC(id) (a key dump yields no usable cookies); value = AES-256-GCM ciphertext bound to its key via AAD (tokens unreadable from a Redis dump; values cannot be swapped between sessions); keys derived by HKDF from `SESSION_SECRET`; sliding idle timeout + absolute lifetime; new id at every login (no fixation); logout destroys the server-side session before ending the Keycloak SSO session. **Alternatives:** Auth.js v5 (beta; default JWT/cookie sessions would put tokens in the browser, DB-adapter mode adds a schema we don't need), iron-session (tokens inside the cookie), hand-rolled OAuth with `oauth4webapi` (more code to get wrong). **Consequences:** Redis is now a hard dependency of login (its outage = users cannot be authenticated; sessions are not in Postgres); rotating `SESSION_SECRET` logs everyone out (no key-rotation scheme yet).

## ADR-030 Token refresh, CSRF and proxy rules — *Accepted (2026-11-23, M0.5)*
(1) **Refresh is serialised per session** with a Redis lock because Keycloak rotates refresh tokens and treats re-use as theft: concurrent refreshes would revoke the session (tested: 8 parallel requests, 2 rounds). (2) **Only a definitive IdP refusal (`invalid_grant`, `invalid_client`, `unauthorized_client`) ends a session**; network errors/5xx keep it and yield `503 + Retry-After` (found in review: the first version destroyed sessions on any failure, which would have logged everyone out during a Keycloak outage). (3) **CSRF for unsafe BFF requests = `Origin` must equal our origin AND per-session token (`X-CSRF-Token`/form field)**, on top of SameSite=Lax; logout is POST-only. (4) **Proxy** forwards only `/api/v1/<validated path>`, allowlists request/response headers (never cookies/Set-Cookie/WWW-Authenticate), caps bodies at 1 MiB, refuses redirects, sets `Authorization` itself, and ignores any client-supplied one. (5) Login CSRF defence: the `state` is also stored in a short-lived HttpOnly cookie that must match.
