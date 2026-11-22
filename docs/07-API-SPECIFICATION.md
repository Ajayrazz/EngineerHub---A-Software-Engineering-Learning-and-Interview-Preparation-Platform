# 07 — API Specification (Outline)

Base: `/api/v1`. Auth: Bearer JWT (via BFF). Errors: RFC 9457. The authoritative contract will be generated OpenAPI (springdoc) and committed as `openapi/engineerhub-v1.yaml`; the frontend client is generated from it. This file is the planning-level surface; **no endpoint is implemented yet**.

Legend: 🔓 public · 🔑 authenticated · 💎 entitlement-gated · 🛡 permission-gated

## Identity / User
- `GET 🔑 /me`, `PATCH 🔑 /me/profile`, `PUT 🔑 /me/preferences`, `GET 🔑 /me/sessions`, `DELETE 🔑 /me/sessions/{id}`, `POST 🔑 /me/delete-request`
- `GET 🔓 /users/{username}` (public profile, respects visibility)
- Registration/login/reset are **Keycloak flows**; API exposes only `POST 🔓 /auth/post-registration` hook-less sync via first-login provisioning.

## Catalog / Learning
- `GET 🔓 /courses?category&difficulty&tier&q&page`, `GET 🔓 /courses/{slug}` (metadata + outline)
- `GET 🔓/💎 /courses/{slug}/lessons/{lessonSlug}` (body only if entitled/free)
- `POST 🔑 /courses/{id}/enroll`, `GET 🔑 /me/enrollments`, `GET 🔑 /me/courses/{id}/progress`
- `PUT 🔑 /lessons/{id}/progress` `{status, scrollPct}`, `PUT/DELETE 🔑 /lessons/{id}/bookmark`
- `GET/POST/PATCH/DELETE 🔑 /lessons/{id}/notes`, `/highlights`
- `GET 🔑 /courses/{id}/certificate`
- `GET 🔓 /search?q&type`

## Problems / Submissions
- `GET 🔓 /problems?difficulty&topic&pattern&company&status&language&freq&tier&q&sort&cursor`
- `GET 🔓/💎 /problems/{slug}` (statement + samples; hidden tests never)
- `GET 💎 /problems/{slug}/editorial`, `/solutions`
- `POST 🔑 /problems/{slug}/run` `{language, code, input?}` → `202 {runId}` (rate-limited, ephemeral)
- `POST 🔑 /problems/{slug}/submissions` `{language, code}` + `Idempotency-Key` → `202 {submissionId}`
- `GET 🔑 /submissions/{id}` (owner only) · `GET 🔑 /submissions/{id}/events` (SSE) · `GET 🔑 /me/submissions?problem&cursor`
- `PUT/DELETE 🔑 /problems/{id}/bookmark`, `GET 🔑 /problems/random?filters`
- `GET 🔓 /companies`, `/companies/{slug}/problems`, `/patterns`, `/topics`

## Roadmaps / Quizzes / Interviews
- `GET 🔓 /roadmaps`, `/roadmaps/{slug}`, `POST 🔑 /roadmaps/{id}/start`, `GET 🔑 /me/roadmaps/{id}/next`
- `GET 🔑 /quizzes/{id}` (no answers), `POST 🔑 /quizzes/{id}/attempts`, `POST 🔑 /quiz-attempts/{id}/submit`, `GET 🔑 /quiz-attempts/{id}` (review)
- `POST 💎 /interviews/sessions`, `POST 💎 /interviews/sessions/{id}/turns` (SSE), `POST …/finish`, `GET …/report`

## Analytics
- `GET 🔑 /me/stats/summary`, `/me/stats/activity?from&to`, `/me/streak`, `GET 🔓 /leaderboards/{board}`

## Community
- `GET 🔓 /comments?targetType&targetId&sort&cursor`, `POST 🔑 /comments`, `PATCH/DELETE 🔑 /comments/{id}` (owner/mod), `PUT 🔑 /comments/{id}/like`, `POST 🔑 /reports`

## AI
- `POST 💎 /ai/chat` (SSE), `POST 💎 /ai/explain-code`, `GET 🔑 /me/ai/usage`

## Newsletter
- `POST 🔓 /newsletter/subscribe` (captcha + rate limit), `GET 🔓 /newsletter/confirm?token`, `POST 🔓 /newsletter/unsubscribe` (RFC 8058 one-click), `GET 🔓 /articles`

## Billing
- `GET 🔓 /plans`, `POST 🔑 /checkout` `{planPriceId, couponCode?}`, `POST 🔓 /webhooks/{provider}` (signature-verified, no auth header), `GET 🔑 /me/subscription`, `POST 🔑 /me/subscription/cancel`, `GET 🔑 /me/invoices`

## Admin (all 🛡, audited) — `/admin/...`
users, roles & grants, courses/sections/lessons/blocks (+reorder, publish, revisions), problems/tests/editorials (+validate, publish, rejudge), taxonomy, quizzes, articles/newsletter, plans/coupons, refunds, moderation queue, audit-log search, settings/flags, analytics.

## Internal (service-to-service; not routed publicly)
- Judge: `GET /internal/judge/problems/{id}/tests?version` (worker mTLS/JWT; or pre-staged bundles in S3), result delivered via RabbitMQ not HTTP.
- AI: `POST /internal/ai/...` with service credentials.

## Conventions
Pagination envelope `{items, nextCursor|page, total?}`; `Idempotency-Key` on submissions/checkout; `429` with `Retry-After`; `ETag`/`If-None-Match` on public GETs; field allowlists for sort/filter; max body sizes (code ≤ 64 KB, JSON ≤ 1 MB, uploads via presigned URLs).

## Addendum A (2026-11-23) — Billing endpoints per doc 21
`GET 🔓 /plans` · `POST 🔑 /billing/quote {planPriceId, couponCode?, state?}` → final payable + tax breakdown · `POST 🔑 /billing/purchases {planPriceId}` + `Idempotency-Key` → provider order/subscription for Checkout · `POST 🔑 /billing/purchases/{id}/confirm` (client signature; UX only) · `GET 🔑 /me/billing` (validity, renewal date, status, mode) · `POST 🔑 /me/billing/subscription/cancel` (cancel at cycle end) · `GET 🔑 /me/billing/payments`, `/invoices`, `/invoices/{id}/download` · `POST 🔑 /me/billing/refund-requests`, `GET 🔑 /me/billing/refund-requests` · `POST 🔓 /webhooks/razorpay` (signature-verified).
Admin 🛡: `/admin/billing/payments`, `/admin/billing/needs-review`, `/admin/billing/refund-requests` (+ `/{id}/review|approve|reject|process`), `/admin/billing/settings` (tax mode, tax rules, seller details, refund policy), `/admin/billing/live-readiness`, `/admin/plans`.


## Addendum B (2026-11-23, M0.4) — implemented conventions & endpoints
**Errors:** RFC 9457 `application/problem+json` with members `type` (`urn:engineerhub:problem:<code>`), `title`, `status`, `detail`, `instance`, **`code`** (stable enum), **`correlationId`**, and `errors[]` (`{field,message}`, never the rejected value) for validation failures. Codes: VALIDATION_FAILED, MALFORMED_REQUEST, UNAUTHENTICATED, FORBIDDEN, ENTITLEMENT_REQUIRED, NOT_FOUND, METHOD_NOT_ALLOWED, CONFLICT, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, RATE_LIMITED, SERVICE_UNAVAILABLE, INTERNAL_ERROR. 401 carries `WWW-Authenticate: Bearer`; 503 carries `Retry-After`.
**Correlation:** every response has `X-Correlation-Id` (inbound value honoured only if `[A-Za-z0-9._-]{8,64}`).
**Implemented endpoints (diagnostic):** `GET /api/v1/system/ping` (public), `GET /api/v1/system/whoami` (authenticated: `{subject, roles}`), `GET /api/v1/system/admin/ping` (SUPER_ADMIN|ADMIN). Contract: `openapi/engineerhub-v1.yaml` (generated, drift-tested).

## Addendum C (M1.1) — implemented: identity
`GET /api/v1/me` (provisions on first call) · `PATCH /api/v1/me/profile` (`displayName`, `bio`, `website`, `githubUrl`, `linkedinUrl`; null = unchanged, "" = clear; https-only links) · `PUT /api/v1/me/preferences` (`timezone` IANA, `locale` en). Errors: `ACCOUNT_SUSPENDED` (403) on every authenticated endpoint for suspended/deleted accounts; anonymous POST/PUT/PATCH/DELETE → 401.

## Addendum D (M1.2–M1.3) — implemented: authorization and administration
`GET /api/v1/me/permissions` · **Admin** (permission in brackets): `GET /admin/users?q&status&page&size` [user.read], `GET /admin/users/{id}` [user.read], `PUT /admin/users/{id}/status` {status: active|suspended, reason ≥ 5 chars} [user.suspend; never self], `GET|POST /admin/users/{id}/grants`, `DELETE /admin/users/{id}/grants/{grantId}` [role.grant.standard; permission must be grantable and held by the caller; ≤ 1 year expiry], `GET /admin/roles` [user.read], `GET /admin/audit-logs?actor&action&resourceType&resourceId&from&to&page&size` and `GET /admin/audit-logs/verify` [audit.read]. List endpoints cap `size` at 100; search is a prefix match with LIKE wildcards escaped.
