# 05 — Low-Level Design

## 1. Backend package layout

```
backend/
  pom.xml
  src/main/java/com/engineerhub/
    EngineerHubApplication.java
    shared/            # kernel: ApiError, ProblemDetails, Ids(UuidV7), Clock, outbox, idempotency, security utils
    identity/          # api/ internal/ (keycloak admin client, external identities, sessions view)
    user/              # profiles, preferences
    catalog/           # courses, sections, lessons, content blocks, taxonomy
    learning/          # enrollments, progress, notes, highlights, bookmarks
    roadmap/
    problem/           # problems, tests (metadata), tags, editorials
    submission/        # submissions, results, status machine
    judge/             # job dispatch, result consumer, language registry, worker auth
    quiz/
    interview/         # companies, tracks, mock sessions
    analytics/         # activity, aggregates, streaks, achievements, leaderboards
    subscription/      # plans, subscriptions, entitlements
    payment/           # provider adapters, webhooks, invoices, refunds
    community/
    notification/
    admin/             # audit log, settings, feature flags
  src/main/resources/db/migration/   # Flyway V{n}__{module}_{desc}.sql
```

Inside a module:
```
<module>/
  api/        # public facade interfaces, DTOs used by other modules, published events
  web/        # REST controllers, request/response DTOs, mappers
  internal/   # entities, repositories, services, policies  (package-private by default)
```

## 2. Layering rules
Controller → Application service (transaction boundary, authorization via policy) → Domain/repo. Controllers hold no logic. Entities are never returned from controllers (DTOs + MapStruct). Request DTOs are explicit (no binding to entities → prevents mass assignment).

## 3. Authorization model

- **Authorities** from Keycloak realm roles (coarse: `ROLE_ADMIN`, …) + app-side permission table for granular, scoped grants.
- `permissions`: `(code)` e.g. `course.edit`, `problem.publish`, `user.ban`.
- `role_permissions`: role → permission.
- `scoped_grants`: `(user_id, permission, resource_type, resource_id)` e.g. instructor ↔ course.
- `PermissionEvaluator` bean: `@PreAuthorize("@authz.can(#courseId,'course','edit')")` evaluating role perms ∪ scoped grants. Deny by default.
- Entitlements: `EntitlementService.has(userId, 'course:{id}' | 'feature:ai')` — computed from active subscription → plan → entitlements; cached in Redis with event-driven invalidation (TTL ≤ 5 min as safety net).
- Ownership rule: repositories for user-owned data always take `userId` from the **security context**, never from the request body.

Authorization matrix: `08-AUTHENTICATION-AUTHORIZATION.md`.

## 4. Content block model
`content_blocks(id, lesson_id, position, type, schema_version, data jsonb, status)`; `type` registry in code (`RICH_TEXT`, `IMAGE`, `VIDEO`, `CODE`, `VIZ`, `QUIZ_REF`, `ATTACHMENT`, `EMBED`, `CALLOUT`, `PROBLEM_REF`). Each type has a Java validator + JSON Schema; unknown types rejected on write, ignored-safely on read. Rich text stored as **sanitized structured JSON (TipTap/ProseMirror doc)**, rendered server-side — no raw HTML stored or rendered (XSS).

Highlights anchor to `(block_id, block_content_hash, start, end, quoted_text)` so edits degrade gracefully ("orphaned" highlight shown with quoted text).

## 5. Submission state machine
```
QUEUED → RUNNING → {ACCEPTED | WRONG_ANSWER | TIME_LIMIT | MEMORY_LIMIT | COMPILE_ERROR | RUNTIME_ERROR | INTERNAL_ERROR}
INTERNAL_ERROR → QUEUED (bounded retries, attempt++)  |  → FAILED_FINAL (after N)
```
Transitions enforced in one `SubmissionStateMachine`; update uses `WHERE id=? AND status IN (allowed_from)` (optimistic) so duplicate/late results are no-ops. Result rows unique on `(submission_id, attempt, test_case_id)`.

## 6. Events (published via outbox, versioned)
`UserRegistered`, `EmailVerified`, `LessonCompleted`, `CourseCompleted`, `SubmissionCreated`, `SubmissionJudged`, `ProblemFirstSolved`, `QuizAttempted`, `SubscriptionActivated`, `SubscriptionCancelled`, `SubscriptionExpired`, `PaymentSucceeded`, `PaymentFailed`, `ContentPublished`, `CommentReported`, `AchievementUnlocked`.
Envelope: `{eventId(UUID), type, version, occurredAt, aggregateId, correlationId, payload}`.

## 7. Error handling
Global `@RestControllerAdvice` → RFC 9457 `application/problem+json` with stable `code` (e.g. `ENTITLEMENT_REQUIRED`, `RATE_LIMITED`). No stack traces/SQL in responses. Not-found vs forbidden: return **404** for resources the caller may not know exist (anti-enumeration), 403 where existence is public (e.g. premium lesson teaser).

## 8. API conventions
`/api/v1/...`, plural nouns, `Idempotency-Key` header on POST for payments/submissions, ETag on cacheable GETs, pagination `?page&size` (offset, capped at 100) for admin lists and `?cursor` for feeds/submissions, sort allowlists, filter allowlists.

## 9. Frontend (see `11-FRONTEND-ARCHITECTURE.md`)
## 10. Judge worker internals (see `10-CODE-EXECUTION-ARCHITECTURE.md`)

## Addendum (M0.6) — events as implemented
Emit with `OutboxPublisher.publish(type, version, aggregateType, aggregateId, payload)` inside the business transaction; consume with `IdempotentEventHandler.handle(consumerName, message, event -> …)` from a `@RabbitListener`. Envelope: `{eventId, type, version, occurredAt, aggregateType, aggregateId, correlationId, payload}`; `type` = dotted lower-case routing key (validated `^[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+$`), payload must be a JSON **object**, bodies ≤ 256 KiB. The correlation id of the originating request travels in the envelope and AMQP headers and is restored in the consumer's logging context. See ADR-031/032.
