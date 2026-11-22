# 14 — Testing Strategy

A feature is "done" only when its acceptance criteria are covered by automated tests and the pipeline is green.

## Pyramid
| Layer | Tooling | Scope |
|---|---|---|
| Unit | JUnit5, Mockito; Vitest | Domain rules, state machines, policies, reducers |
| Slice | `@DataJpaTest`, `@WebMvcTest` + security | Repos with real PG (Testcontainers), controller validation/authz |
| Integration | `@SpringBootTest` + Testcontainers (PG, Redis, RabbitMQ, Keycloak) | Flows across modules, outbox→MQ→consumer |
| Architecture | Spring Modulith verify, ArchUnit | Module boundaries, layering, "no entity in controller" |
| Contract | OpenAPI diff in CI; generated client typecheck | Breaking-change detection |
| E2E | Playwright (+axe) | Critical journeys |
| Performance | k6 | Problem list, lesson fetch, submit burst, dashboard, webhook flood |
| Security | ZAP, Trivy, CodeQL, gitleaks, authZ matrix | See doc 09 §10 |
| Judge | Hostile-program corpus | Doc 10 §8 |
| Resilience | Toxiproxy / chaos in staging | DB/MQ/Redis outages, worker kills |

## Mandatory scenarios (from brief) → test mapping
| Scenario | Level |
|---|---|
| Unauthenticated access to protected APIs → 401 | WebMvc + E2E |
| Free user → premium content denied, body absent | Integration |
| User A accessing user B's submission/notes/progress → 404 | Integration (parameterized per id-route) |
| Admin-only operations rejected for others | Authz matrix |
| Expired/cancelled subscription loses entitlement (incl. cache invalidation) | Integration |
| Duplicate payment webhook processed once; forged signature rejected; out-of-order events | Integration |
| Failed queue processing → retry → DLQ; no duplicate state change | Integration |
| Concurrent submissions (same user/problem), idempotency key reuse | Integration + k6 |
| Code execution timeout/memory/fork bomb | Judge corpus |
| DB TX failure rolls back with no orphan outbox row / no lost event | Integration w/ fault injection |

## Quality gates
Coverage floors (domain/service ≥ 80 % line; mutation testing with PIT on critical modules — authz, payment, judge state machine); no flaky-test tolerance (quarantine + fix SLA); every bug fix ships with a regression test; performance budgets tracked per release.

## Test data
Builders/fixtures, deterministic seeds, synthetic problems with known verdicts, Keycloak test realm. No production data in non-prod.

## Implemented so far (M0.3 + M0.4, 2026-11-23)
- Unit/slice: `SystemControllerTest` (incl. "API docs not exposed by default"). Architecture: `ModularityTests` (Spring Modulith). Contract: `OpenApiContractTest` (+ frontend `api:check`). Integration: `PostgresContainerIT` (Testcontainers 2.0.5 + Postgres 18). Stack: `make dev-verify` (24 checks).
- Convention: `*Test` = surefire (fast, no Docker); `*IT` = failsafe in `verify` (Docker allowed).
- Gate sanity: each gate was shown to fail when it should (tampered OpenAPI spec, stale TS client, planted secret via hook and scanners, broken workflow, vulnerable dependency).
- **Not yet in place:** frontend unit/component tests (Vitest + React Testing Library — added with the first real UI in M0.5), Playwright E2E, k6, ZAP, mutation testing, coverage thresholds.

**M0.4 additions:** 31 unit tests (`IdsTest`, `CorrelationIdFilterTest`, `TokenRulesTest` audience/roles/CORS, `ResilientJwtDecoderTest`, `SecurityPropertiesTest` fail-fast, `EndpointPolicyArchTest` + negative control, `ModularityTests`) and 35 integration tests against real PostgreSQL 18 (`SecurityIT` 13, `ErrorModelIT` 11, `PersistenceIT` 8 incl. a 16-thread idempotency race and rollback behaviour, `OpenApiContractIT` 2, `StructuredLoggingIT` 1). Conventions: `*Test` fast/no Docker; `*IT` annotated `@IntegrationTest` (shared Testcontainers Postgres, profile `test`). Live: `scripts/verify-api-auth.sh` (33 checks) and the 24-check stack script. **Bugs these tests found during M0.4:** IdP outage returned 500 (now 503, ADR-026); unreadable body mapped to the wrong error code.
**Limits:** signature verification is not covered by Java tests (tokens are injected); it is covered by the live script. No coverage thresholds yet.

**M0.5 additions:** 84 Vitest unit tests (crypto, session store incl. TTL/absolute expiry/tamper/AAD-swap, refresh lock & outage semantics against a rotation-enforcing fake IdP, open-redirect/CSRF/path validators) and 35 Playwright E2E tests against the real stack (login via the real Keycloak page, token-hiding assertions, Redis-at-rest encryption, CSP, CSRF, login-CSRF with an attacker's valid callback, replay, fixation, forged cookies, traversal/SSRF, body cap, logout + replay after logout, token refresh + rotation, parallel refresh, rejected refresh, Redis tampering, and a Keycloak outage/recovery test). **Practices adopted:** E2E starts from an empty `bff:*` Redis namespace (guarded to localhost); security tests are validated by **mutation** (break the control, watch the test fail). **Bugs these tests/reviews found in M0.5:** refresh failure destroyed sessions during an IdP outage (fixed, ADR-030); my first login-CSRF test did not isolate its control (rewritten); several of my own flaky assertions (padding bits in signatures were an M0.4 lesson; random base64 containing `eyJ` here).

**M0.6 additions:** 98 backend tests in total (39 unit, 59 integration). Messaging: `EventCodecTest` (validation, forward compatibility, no content echo), `RabbitConnectionGuardTest` (fail-fast config), and integration tests on real PostgreSQL 18 + RabbitMQ 4.3.8: `OutboxIT` (12: invisible until commit, rollback leaves nothing, publish outside a transaction refused, message shape/persistence/correlation, ordering, **6 concurrent relays × 200 events with zero duplicates**, broker outage keeps events pending with no credential leak and recovers, relay backoff, unroutable counting, retention purge), `OutboxFaultInjectionIT` (**crash after broker confirm but before commit → 2 deliveries, 1 effect**), `IdempotentConsumerIT` (20 duplicates → 1 effect, retry then success, permanent failure → DLQ after exactly 3 attempts with the original message preserved, 5 kinds of poison messages → DLQ without reaching the handler, good event after poison still processed), `MessagingTopologyIT` (durable topic exchange, durable quorum queues, DLX wiring — read back from the broker's management API), `MessagingConfigurationIT` (resolved reliability settings). **Mutation-validated:** ignoring the idempotency ledger, dropping `SKIP LOCKED`, marking failed publishes as published, and allowing publish outside a transaction were each caught by the intended tests. **Live:** `scripts/verify-messaging.sh` (20 checks incl. stopping the RabbitMQ container mid-publish: nothing lost, no duplicates, relay reconnects by itself, API stays up, no credentials in `last_error` or logs). **Bugs found by these tests/reviews:** JSON `null` payload slipped through the codec (Jackson 3 `NullNode`); silently ignored `max-attempts` (4 attempts instead of 3); relay retried and logged on every poll during an outage (now backoff: 37 → 2 log lines); my own backoff edit silently didn't apply (a test caught it).
