# 16 — Development Roadmap (Solo developer + Claude)

Rewritten 2026-11-23 for the confirmed team structure: **one developer/project owner + Claude (via Antigravity)**. The earlier 2–4 engineer phase estimates are withdrawn.

## 1. Planning principles
1. **Small, finishable milestones** (target ≤ 1–2 weeks of focused work each), each ending in something running, tested and documented.
2. **A milestone is not "done" until:** implemented → tests written and passing → security checklist (doc 09 §8) reviewed → docs updated → `19-PROJECT-PROGRESS.md` updated. No placeholders presented as features.
3. **Foundation before features:** security, auth, CMS, CI, backups come before breadth.
4. **One deployable backend (modular monolith)** a single person can hold in their head; no microservices. Judge worker is the only extra process, because it must be isolated.
5. **Automate the repetitive:** CI on every push, auto-formatting, dependency updates, generated API client, DB migration checks, doc-update checklist in PR template, scripted local setup, content-validation harness.
6. **Content is the product's other half** and is done by you with Claude (§4); the CMS is built early so content work can start while engineering continues.
7. **Free/OSS-first, portable, Docker-based** (docs 13/15, ADR-017/019).
8. **Cut scope, not quality:** if time slips, drop features from later phases, never skip tests/security.

## 2. Time model (honest estimating)
Hours per week available is **Pending Decision (T-1)**. Estimates are expressed as **focused effort-weeks (≈ 25–30 productive hours with Claude assistance)**; convert to calendar time as `effort-weeks × 30 / your weekly hours`. Estimates are ranges and will be re-baselined at the end of Phase 0 and after each phase. Claude accelerates scaffolding, tests and docs, but review, debugging, security validation, and content judgment remain yours — so I do not assume more than ~1.5× speedup over an unassisted solo developer.

| Scope | Effort-weeks (range) | At 20 h/wk | At 40 h/wk |
|---|---|---|---|
| **Foundation + MVP (Phases 0–6)** | ~30–42 | ~11–15 months | ~5–7 months |
| Post-MVP core (Phases 7–9) | ~14–20 | +5–7 months | +2.5–3.5 months |
| Advanced (AI, mock interviews, community, newsletter) | ~16–24 | +6–9 months | +3–4 months |

These are planning numbers, not commitments. Content writing time is **in addition** (§4).

## 3. Phases and milestones

### Phase 0 — Foundations (≈3–4 effort-weeks) ✅ COMPLETE (2026-11-23)
- **M0.1 ✅ (2026-11-23)** Monorepo scaffold (`backend/ frontend/ judge-worker/ infra/ docs/`), version-compatibility matrix (Spring Boot/Java 21/Hibernate/Security/Modulith/Next/React/Tailwind/shadcn), pinned versions, `CLAUDE.md` (project rules/commands for Claude), PR template with doc/test/security checklist.
- **M0.2 ✅ (2026-11-23)** Docker Compose dev stack (Postgres, Redis, RabbitMQ, Keycloak with realm-as-code, MailHog, MinIO), `.env` workflow, Makefile.
- **M0.3 ✅ (locally verified 2026-11-23; GitHub-side pending repo)** CI (GitHub Actions): build, unit/integration tests (Testcontainers), lint/format, gitleaks, dependency + image scan, OpenAPI → TS client generation.
- **M0.4 ✅ (2026-11-23)** Spring Boot skeleton: error model (problem+json), security config, health, Flyway baseline, Modulith verification test, structured logging.
- **M0.5 ✅ (2026-11-23)** Spike: Keycloak + Next.js BFF login → call Spring API (documented ADR for chosen OIDC library).
- **M0.6 ✅ (2026-11-23)** Spike: outbox → RabbitMQ → idempotent consumer.
Exit: authenticated request flows through BFF → API → DB in CI-green repo.

### Phase 1 — Identity & platform core (≈3–4)
Users/profile (provision on first login), roles/permissions + seed + authz matrix tests, admin shell, audit log, rate limiting, MFA for admin, email via MailHog/SMTP. Exit: role-based admin access proven by tests.

### Phase 2 — CMS & Learning reader (≈5–6) — *content work can begin*
Taxonomy (31 DSA topics seed), courses/sections/lessons/blocks, draft→publish (+content-source metadata, doc 20), **admin CMS you author in** (markdown/TipTap, preview, reorder, validation), reader UI (sidebar, TOC, progress, complete/resume), public SSR pages, free/premium flag (gating enforced server-side, using a stub entitlement until Phase 6).
Exit: you can author and publish a lesson end-to-end and a learner can read it.

### Phase 3 — Problems & practice (≈3–4)
Problem model, tags (topics/patterns/companies), **problem authoring UI with test-case management**, **validation harness design** (reference solution + brute force + generator), listing/filter/search, per-user status, bookmarks. (Execution arrives in Phase 4.)

### Phase 4 — Online judge: Java, Python, C++ (≈6–8) ⚠ highest risk
- **M4.1** Linux VM dev setup + `isolate` spike with hostile-program corpus; re-validate ADR-005 assumptions (go/no-go gate; fallback evaluated: nsjail / Judge0).
- **M4.2** Worker: compile + run + limits + verdict mapping for C++.
- **M4.3** Java and Python language profiles.
- **M4.4** Checker stage; stdin/stdout test execution; hidden-test handling.
- **M4.5** RabbitMQ jobs/results, idempotency, retries, DLQ, stuck-job sweeper.
- **M4.6** API: run & submit, quotas, SSE status; submission history.
- **M4.7** Monaco UI, results panel.
- **M4.8** Hostile corpus in CI; written sandbox threat review. **No public exposure before M4.8 + an external/second review.**

### Phase 5 — Progress & dashboard basics (≈2–3)
Solved counts, daily activity, streaks, course progress counters via events; user dashboard.

### Phase 6 — Billing, all four purchase models (≈7–9; Razorpay test mode)
- **M6.1** Plans/prices (tax-inclusive), entitlement model, `AccessPeriod`, EntitlementService + gating everywhere (replaces stub).
- **M6.2** Razorpay provider adapter (test mode), quote → order → checkout, webhook verify/dedupe, `PaymentCapturedHandler` — **monthly & annual passes working end-to-end**.
- **M6.3** Billing page (validity, status, payment history) and in-app renewal reminder — **done**. Expiry sweeper and e-mail reminders deliberately not built (ADR-051); they need mail infrastructure and your decision.
- **M6.4** Recurring subscriptions (monthly/annual): create/authenticate, `subscription.charged` into the same handler, cancel-at-cycle-end, failed payment → PAST_DUE → grace → expiry. — **done on our side, NOT verified against Razorpay** (ADR-052); the grace period is not built (P-2).
- **M6.5** Tax config model (`UNCONFIGURED/DISABLED/ENABLED`), invoices/receipts PDF, credit notes.
- **M6.6** Refund requests + admin review/process + auto-flags + configurable policy text.
- **M6.7** Reconciliation job, Live Readiness Gate, full test matrix (doc 21 §10).
Exit: all four options work in **test mode**; live charging impossible until the Gate (legal entity/KYC/accountant — pending).
*Contingency:* if schedule tightens, passes (M6.2–6.3, 6.5–6.7 for passes) ship first and recurring (M6.4) follows — design already supports both.

> **MVP / closed beta = Phases 0–6 + seed content (§4).** Public paid launch additionally requires: Live Readiness Gate complete, legal pages, backup/restore drill, sandbox review done, monitoring + alerts, load test.

### Phase 7 — Quizzes & visualizations (≈5–7)
Quiz engine (MCQ/multi/TF first), embeds in lessons; visualization framework + first 8–10 deterministic visualizations.

### Phase 8 — Roadmaps, notes/highlights, company-wise & interview content structure, notifications (≈5–6)

### Phase 9 — Hardening & launch readiness (≈3–4)
Performance tests (k6), pentest/ZAP, accessibility audit, DR drill, runbooks, SLO dashboards, hosting decision executed (H-1).

### Later (post-launch; each re-scoped when reached)
Leaderboards & badges · community & moderation · newsletter · certificates · AI assistant (RAG) · mock interviews · function-signature problems · more judge languages · Stripe/international.

## 4. Content track (you + Claude)
**You own content authorship; Claude assists with research, drafting, structuring, review and validation. You are the final reviewer of everything published.** Workflow and rules in doc 20 §8.

| Stage | When | Target (adjustable) |
|---|---|---|
| C0 Tooling | Phases 2–3 | CMS + problem validation harness + templates for lessons/problems/quizzes |
| C1 Seed library for beta | Starts after M2; runs alongside Phases 3–6 | Outline for all 31 DSA topics; **10–15 full lessons**; **60–100 original problems** with validated tests & editorials; ~100 quiz questions |
| C2 Breadth | Phases 7–9 and after | ~300 problems, lessons for all topics, first visualizations |
| C3 System design / LLD / interview | After launch | Original lessons and case studies |

Content effort is **not included** in the effort-week estimates above. A realistic pace is roughly **1–3 reviewed problems/day or 1–2 lessons/week** alongside engineering — confirm your own pace after the first batch (T-2).

## 5. Solo-workflow automation checklist (build in Phase 0 and keep)
- Pre-commit hooks: format, lint, gitleaks.
- CI required to merge (even solo): tests, scans, migration check, OpenAPI diff.
- Dependabot/Renovate for dependency PRs; weekly review.
- PR/commit template with "docs updated? tests? security checklist?" boxes.
- `docs/19-PROJECT-PROGRESS.md` updated at the end of every milestone (Claude does this as part of its definition of done).
- Scripted `make dev`, `make test`, `make seed`, `make backup-test`.
- Content-validation harness runs in CI against all problems on every change to test data.

## 6. Risks specific to solo development
Burnout/scope creep → hard MVP fence and milestone cadence · bus factor → docs/ADRs/runbooks kept current · security review is self-review → external review scheduled before public judge/payments launch · single point of failure on ops → automate backups/deploys, keep the stack small.

## 7. Pending decisions (roadmap)
| ID | Question |
|---|---|
| T-1 | Weekly hours available (drives calendar dates) |
| T-2 | Realistic content-authoring pace after first batch |
| T-3 | Target beta date / is there an external deadline? |
