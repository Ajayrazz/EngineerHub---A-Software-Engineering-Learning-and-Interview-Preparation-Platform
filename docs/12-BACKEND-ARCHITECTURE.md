# 12 — Backend Architecture

## 1. Stack
Java 21 LTS · Spring Boot **4.1.x** (selected and verified in M0.1; Boot 3.5 is end-of-life — see `22-VERSION-COMPATIBILITY.md`; original compatibility concern of Boot ↔ Spring Security ↔ Hibernate ↔ Spring Modulith ↔ springdoc ↔ Testcontainers ↔ Keycloak adapter-free resource server) · Spring Web/Validation/Security (OAuth2 Resource Server)/Data JPA/AMQP/Actuator · Flyway · Maven · springdoc-openapi · MapStruct · Spring Modulith (module verification + events) · Micrometer + OpenTelemetry.

Additions beyond the brief (explanations required by Rule 4):
| Library | Why | Alternative |
|---|---|---|
| Spring Modulith | Enforce/verify module boundaries, event publication registry | ArchUnit only (less ergonomic) |
| MapStruct | Safe DTO mapping, compile-time | Manual mappers (verbose) |
| Bucket4j (Redis) | Distributed token-bucket rate limiting | Custom Lua |
| Testcontainers | Real PG/Redis/RabbitMQ in tests | H2 (unfaithful) — rejected |
| ArchUnit | Layer rules | — |
| jOOQ (not adopted) | Considered for complex reporting; JPA + native SQL projections suffice initially | — |

## 2. Process model
One codebase → `api` profile (HTTP) and `worker` profile (consumers, relay, schedulers). Schedulers use ShedLock (Redis/JDBC) to avoid duplicate runs.

## 3. Persistence
HikariCP pool sized per instance (`pool × instances ≤ DB max_connections` budget; consider RDS Proxy later). `open-in-view=false`. Explicit fetch plans (EntityGraph/projections) to avoid N+1; Hibernate statistics asserted in tests on hot endpoints. Optimistic locking on editable aggregates. Batch inserts where needed.

## 4. Transactions & consistency
TX at application-service methods. State change + outbox row in one TX. Cross-module side effects only via events (eventual consistency) — the UI handles "pending" states (e.g. progress counters). Pessimistic `SKIP LOCKED` for outbox/job pickups.

## 5. Caching
Redis cache-aside for: published course outlines, topic/pattern/company lists, problem list facets, entitlement snapshots, leaderboards, dashboard aggregates. Keys namespaced+versioned (`v1:course:{id}`); explicit evict on `ContentPublished`; TTL safety nets; stampede protection (single-flight/lock) on hot keys.

## 6. Messaging
Spring AMQP, publisher confirms, durable topology declared in code (`@Bean` Declarables) per environment, retry via delay queues, DLQs, consumer concurrency tuned per queue, `processed_messages` idempotency. JSON payloads with schema version.

## 7. Security config
Stateless; JWT decoder with issuer/audience validation; `@EnableMethodSecurity`; CORS allowlist (only the BFF origin); security headers; request size limits; global rate-limit filter; actuator on separate management port, locked down.

## 8. Observability
Micrometer → Prometheus; OTel traces (HTTP, JDBC, AMQP) with trace id in logs (JSON, Logback); RED metrics per endpoint; business metrics (submissions/sec, verdict mix, enrollments, subscription conversions); health groups (liveness/readiness) incl. DB/Redis/RabbitMQ.

## 9. Testing layers
Unit (JUnit5/Mockito) → slice (`@DataJpaTest`, `@WebMvcTest` + security) → integration (`@SpringBootTest` + Testcontainers) → Modulith module tests → contract tests (OpenAPI diff) → authZ matrix tests → perf (k6).

## 10. Coding standards
Constructor injection; records for DTOs/events; no Lombok on entities (explicit equals/hashCode on id) — Lombok optional elsewhere; `Optional` returns not params; checked domain exceptions mapped centrally; Google-Java-Format/Spotless + Checkstyle + ErrorProne/SpotBugs in CI.
