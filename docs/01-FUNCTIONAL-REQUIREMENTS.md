# 01 — Functional Requirements (PRD)

Legend — **Source**: `PDF` = visible in reference; `PDF*` = implied by reference; `PROMPT` = specified in the brief; `PROPOSED` = our recommendation.
**Priority**: P0 = MVP, P1 = release 1.x, P2 = later.

---

## Part A — PDF Analysis

### A.1 Explicitly visible in the PDF (page refs)

| Area | Observed | Page |
|---|---|---|
| Landing | Dark theme hero, "Start Free"/"Get Premium" CTAs, company logo strip | 1 |
| Landing | Course explorer cards (DSA, System Design Fundamentals, System Design Interviews, LLD, Concurrency, Behavioral/STAR) | 1 |
| Landing | Interactive reader showcase: Listen mode, Ask AI, Track progress, Highlights + inline notes (color options, "Ask AI" on selection), Course notebook, Interactive quizzes, Customizable reading | 2 |
| Landing | Patterns showcase (60+ coding patterns/600+ problems; 20+ system-design patterns/55+ problems) | 2 |
| Landing | Practice vs Mock Interview split (DSA, System Design, LLD, Concurrency counts; AI interviewer by company) | 3 |
| Landing | Social proof, community stats, testimonials, FAQ (incl. refunds/cancel), creator bio, newsletter signup, footer | 3–6 |
| Navigation | Mega-menu "Learn": Interview Prep, AI & ML, Backend, Fundamentals categories | 6 |
| Navigation | Top nav: Learn, Practice, Interview, Resources, Newsletter, mobile app, Premium, search (`F` shortcut), theme toggle, streak counter, Login | 6, 21–23 |
| Course reader | Left sidebar: back to home, course title, % progress, certificate (locked), x/772 counter, chapter search, filter, collapsible sections with per-section counts | 7, 15–19 |
| Course reader | Right rail: "On this page" TOC, reading-progress %, Premium upsell card | 7–14 |
| Course reader | Bottom bar: Notes, Star, Complete, Ask AI, prev/next, Book/layout toggle, font settings, fullscreen, Discord/"Vote/Request Content" | 7, 20 |
| Course reader | "Listen to this chapter" (audio, premium), premium video lock with duration, "High Priority" badge, read time, updated date | 20 |
| Course reader | Interactive animation player: tabs per approach, input selector, step label, play/prev/next/scrub/speed/fullscreen, synced code panel with language selector and copy/expand | 9, 10 |
| Course reader | Embedded compiler: problem card (difficulty/topics/companies tags), examples with visualization, constraints, "Try it yourself" editor with timer, Run/Evaluate/Submit, test-case tabs, Output/Feedback tabs, visualized output, hints (3) | 10–12 |
| Course reader | Chapter quizzes (MCQ with code, "Check answer") | 12 |
| Course reader | Author card, comments (sort, search, replies, likes, sign-in to comment) | 13, 14 |
| Curriculum | 31 sections with counts (total 772 items), e.g. DSA Warmup 65, Graphs 82, DP 92 | 15–19 |
| Language support | Java, Python, C++, C#, Go, JavaScript, TypeScript (7+) | 7, 16 |
| Practice | DSA patterns page: Course / Company-wise toggles, track tabs (75/150/300/600 → time-boxed roadmaps), progress by difficulty, filters/sort/random/starred, mock interview button, grouped problem table (status, difficulty, frequency bars, LeetCode link, solution (text/video), actions: notes/star) | 23, 24 |
| Practice | Left icon rail: DSA, System Design, LLD, Concurrency, Interviews, AI, etc. | 23 |
| Interview menu | Coding, System Design, LLD, AI Coding Interview (New) | 21 |
| Practice menu | DSA Patterns, System Design, Concurrency, LLD, Company-wise Problems | 21 |
| Resources menu | Animations, Resume Builder, Roadmaps, Learn LLD, Curated Engineering Blogs, GitHub, Upcoming Courses | 22 |
| Newsletter menu | All Articles, Premium Articles | 23 |
| Monetization | Premium badge, limited-time offer banner, locked video/audio/problems | 1, 20 |
| Page 24 note | "Along with this we have a lot more modules to do. This is just for reference." | 24 |

### A.2 Implied by the PDF
- Per-lesson read-time, last-updated metadata, priority tagging (PDF*).
- Frequency score per problem and company tags (PDF*).
- Certificate on course completion (locked indicator) (PDF*).
- Content request/voting (PDF*).
- Course filter (completed / premium / difficulty) (PDF*).
- Multi-language code snippets per lesson with per-user preferred language (PDF*).
- Mobile app (App Store / Play Store links) — **deferred**, PWA/responsive first.

### A.3 Challenges surfaced
Video/audio premium gating, reader layout settings, synced visualization+code, inline highlights anchoring to content blocks (anchoring is hard when content is edited — see ADR-010 plan), per-language reference solutions, large curriculum counts requiring denormalized progress counters.

---

## Part B — Modules, Requirements, Acceptance

Format: `ID | Requirement | Src | Pri`. Each module lists user stories (US), business rules (BR), error cases (ERR), permissions (PERM) and future extensions (FUT). Acceptance criteria (AC) are listed for P0 items; others get ACs when scheduled.

### MODULE 1 — Identity & User Management (AUTH / USER)

Objective: secure identity and account lifecycle. Users: everyone.

| ID | Requirement | Src | Pri |
|---|---|---|---|
| AUTH-001 | Register with email+password; email verification required before full access | PROMPT | P0 |
| AUTH-002 | Login/logout via OIDC Authorization Code + PKCE (Keycloak) | PROMPT | P0 |
| AUTH-003 | Forgot/reset password with single-use, expiring token | PROMPT | P0 |
| AUTH-004 | Google and GitHub social login; account linking by verified email only | PROMPT | P0 |
| AUTH-005 | Session management: list active sessions, revoke | PROMPT | P1 |
| AUTH-006 | MFA (TOTP) mandatory for admin-class roles | PROMPT | P0 |
| AUTH-007 | Brute-force/credential-stuffing protection, login anomaly logging | PROMPT | P0 |
| AUTH-008 | Account deletion (soft-delete → hard purge after retention window) and recovery | PROMPT | P1 |
| USER-001 | Profile: username (unique), name, avatar, bio, social links, website | PROMPT | P0 |
| USER-002 | Preferences: languages, learning prefs, theme, reader settings | PROMPT | P0 |
| USER-003 | Public profile page with privacy toggles | PROMPT | P1 |
| USER-004 | Data export (GDPR-style) | PROPOSED | P2 |
| RBAC-001 | Roles: SUPER_ADMIN, ADMIN, CONTENT_MANAGER, PROBLEM_SETTER, MODERATOR, INSTRUCTOR, PREMIUM_USER, FREE_USER, USER | PROMPT | P0 |
| RBAC-002 | Granular permissions + resource-scoped grants (e.g. instructor↔course) | PROMPT | P0 |
| RBAC-003 | All checks enforced server-side (method-level + resource-level) | PROMPT | P0 |

Note: `PREMIUM_USER`/`FREE_USER` are **entitlement states, not roles**, derived from subscription. We model them as computed authorities to avoid drift (ADR-006).

US: As a new user I can sign up, verify email and land on onboarding. BR: usernames case-insensitively unique; social-link account merge only on verified emails. ERR: expired/used token → 400 with generic message; lockout → 429/423. AC (AUTH-001): unverified user cannot enroll/submit; verification link works once, expires ≤24h.

### MODULE 2 — Learning Management System (LMS)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| LMS-001 | Course entity: title, slug, description, thumbnail, banner, category, difficulty, duration, instructors, languages, status, access tier, prerequisites, objectives | PROMPT | P0 |
| LMS-002 | Hierarchy Course→Section→Chapter(Lesson)→ContentBlock, orderable | PROMPT | P0 |
| LMS-003 | Extensible content-block registry (rich text, image, video, code, visualization, quiz, attachment, embed, callout) | PROMPT | P0 |
| LMS-004 | Enrollment, resume-at-last-position, mark complete, course progress | PROMPT | P0 |
| LMS-005 | Bookmarks (star), notes, highlights with colors, inline notes | PDF/PROMPT | P1 |
| LMS-006 | Chapter search (sidebar) and full-text lesson search | PDF | P0 / P1 |
| LMS-007 | Sidebar filters (completed, incomplete, premium, starred) | PDF* | P1 |
| LMS-008 | "On this page" TOC, reading-progress %, reader settings (font, layout, theme) | PDF | P1 |
| LMS-009 | Prerequisites and related lessons | PROMPT | P1 |
| LMS-010 | Certificates on completion (async PDF generation, verifiable ID) | PROMPT | P1 |
| LMS-011 | Listen mode (TTS audio, premium) | PDF | P2 |
| LMS-012 | Premium video with signed delivery | PDF | P1 |
| LMS-013 | Content request/vote | PDF* | P2 |
| LMS-014 | Course discussions (comments) | PDF | P1 |
| LMS-015 | Authoring: draft/review/publish workflow, version history, preview unpublished, archive | PROMPT | P0 (draft/publish), P1 (review/versions) |
| LMS-016 | Course instructors scoped permissions | PROMPT | P0 |

BR: a lesson's access tier overrides course tier only upward-restrictive or explicit "free preview" flag. Progress counts only published lessons. ERR: access to premium lesson without entitlement → 403 with upsell payload but **no content body**.

### MODULE 3 — DSA Curriculum & Roadmaps (ROAD)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| ROAD-001 | Topic taxonomy (topic→subtopic), managed dynamically; seeded with the 31 reference sections | PDF/PROMPT | P0 |
| ROAD-002 | Roadmaps as ordered steps referencing topics/lessons/problems with prerequisites (DAG, cycle-checked) | PROMPT | P1 |
| ROAD-003 | Time-boxed tracks (e.g. 75/150/300/600 problems) | PDF | P1 |
| ROAD-004 | Next-topic recommendation (rule-based first, AI later) | PROMPT | P1 |
| ROAD-005 | Personal learning plans (user-created) | PROMPT | P2 |

### MODULE 4 — Problem Management (PROB)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| PROB-001 | Problem entity per prompt §Module 4 (statement, constraints, I/O format, examples, limits, hints, editorial, approaches, complexities, tags, status) | PROMPT | P0 |
| PROB-002 | Topics, patterns, companies as normalized many-to-many | PROMPT | P0 |
| PROB-003 | Test cases: sample (public) vs hidden; stored/served separately; versioned | PROMPT | P0 |
| PROB-004 | Search & filter: difficulty, topic, pattern, company, status, language, frequency, free/premium | PROMPT | P0 |
| PROB-005 | Per-user status: not attempted / attempted / solved / bookmarked; full submission history kept | PROMPT | P0 |
| PROB-006 | Random problem pick with filters | PDF | P1 |
| PROB-007 | Problem set curation (company lists, pattern lists, tracks) | PDF | P1 |
| PROB-008 | Problem authoring with validation harness (reference solution must pass all tests before publish) | PROPOSED | P0 |
| PROB-009 | Problem discussions and solutions threads | PROMPT | P1 |

BR: a problem is "solved" if ≥1 Accepted submission; this flag never reverts. Editing test cases bumps a `testcase_version`; old submissions retain the version they ran against. Whether to rejudge is an admin action.

### MODULE 5 — Online Judge (JUDGE)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| JUDGE-001 | Monaco editor with language select, theme, formatting, custom input | PROMPT/PDF | P0 |
| JUDGE-002 | Run (sample/custom input, ephemeral) vs Submit (hidden tests, persisted) | PDF | P0 |
| JUDGE-003 | Languages **P0 (confirmed): Java, Python, C++**, each fully functional (edit, compile where applicable, run, stdin/stdout, tests, RE/CE reporting, TL/ML enforcement). Later (data-driven via `languages` table): C, C#, Go, JavaScript, TypeScript | DECISION | P0/P2 |
| JUDGE-004 | Verdicts: AC, WA, TLE, MLE, CE, RE, IE (+ OLE proposed) | PROMPT | P0 |
| JUDGE-005 | Async lifecycle via RabbitMQ; status polling/SSE; idempotent result ingestion | PROMPT | P0 |
| JUDGE-006 | Isolation sandbox, resource limits, no network, cleanup | PROMPT | P0 |
| JUDGE-007 | Per-test results, time & memory; hidden test inputs/outputs never revealed | PROMPT | P0 |
| JUDGE-008 | Submission history & auditability | PROMPT | P0 |
| JUDGE-009 | Submission rate limits & quotas by tier | PROPOSED | P0 |
| JUDGE-010 | **stdin/stdout problem format is P0 (confirmed).** Function-signature problems with generated drivers are a later phase (P2); architecture is signature-ready (doc 10 §1.5) | DECISION | P0 (stdio) / P2 (signature) |
| JUDGE-011 | Special judge / checker support for multi-answer problems | PROPOSED | P1 |
| JUDGE-012 | Rejudge | PROPOSED | P1 |

### MODULE 6 — Visualization (VIZ)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| VIZ-001 | Deterministic step-trace player: play/pause/restart/step±/speed/scrub | PROMPT/PDF | P0 |
| VIZ-002 | Visualization = pure function of (algorithm, input) → ordered list of states; frontend renders; trace generated by vetted TS algorithm implementations (or authored JSON) | PROPOSED | P0 |
| VIZ-003 | Custom input with validation and size caps | PROMPT/PDF | P1 |
| VIZ-004 | Synchronized code highlight per step, multi-language | PDF | P1 |
| VIZ-005 | Compare approaches (tabs) | PDF | P1 |
| VIZ-006 | Catalog: sorting, binary search, two pointers, sliding window, LL, stack, queue, tree, graph BFS/DFS, DP, recursion, backtracking, heaps | PROMPT | P1 rolling |

### MODULE 7 — Quizzes (QUIZ)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| QUIZ-001 | Types: MCQ, multi-select, true/false, output prediction, complexity, fill-blank | PROMPT | P0 (MCQ, multi, T/F) / P1 rest |
| QUIZ-002 | Embeddable in lessons and standalone; chapter/topic quizzes | PROMPT/PDF | P0 |
| QUIZ-003 | Server-side grading; correct answers not sent before submission | PROPOSED | P0 |
| QUIZ-004 | Explanations, marks, difficulty, time limit, attempts, review wrong answers | PROMPT | P1 |
| QUIZ-005 | Analytics per question | PROMPT | P2 |

### MODULE 8 — Interview Prep (INT)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| INT-001 | Company entity & company-wise problem lists with frequency | PDF/PROMPT | P0 |
| INT-002 | Interview tracks: Coding, System Design, LLD, AI Coding, Behavioral (STAR) | PDF | P1 |
| INT-003 | System design content: lessons, diagrams, case studies, exercises, sample solutions | PROMPT | P1 |
| INT-004 | LLD content: OOD, SOLID, patterns, UML (rendered Mermaid/PlantUML-safe) | PROMPT | P1 |
| INT-005 | Concurrency practice | PDF | P2 |
| INT-006 | Timed coding sessions | PROMPT | P1 |

### MODULE 9 — Mock Interviews (MOCK)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| MOCK-001 | Session model: question(s), timer, editor, transcript, submissions, report | PROMPT | P1 |
| MOCK-002 | AI interviewer: follow-ups, hints, evaluation (with disclaimer) | PROMPT/PDF | P2 |
| MOCK-003 | Performance report (correctness, complexity, communication) | PROMPT | P2 |
| MOCK-004 | Retention + deletion controls for transcripts | PROPOSED | P2 |

### MODULE 10 — AI Assistant (AI)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| AI-001 | Ask AI from reader/problem with context (lesson content authorized for the user) | PDF/PROMPT | P1 |
| AI-002 | Explain selected text, explain code, hints (progressive, no spoilers by default) | PDF/PROMPT | P1 |
| AI-003 | Quotas per tier, cost metering, abuse prevention, kill-switch | PROMPT | P1 |
| AI-004 | Isolated FastAPI service; keys never in browser; prompt-injection defenses | PROMPT | P1 |
| AI-005 | RAG over course content with pgvector; respects entitlements | PROMPT | P1 |
| AI-006 | Quiz/practice generation → goes to moderation queue before publishing | PROMPT | P2 |
| AI-007 | Weakness analysis | PROMPT | P2 |

### MODULE 11 — Progress & Analytics (ANA)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| ANA-001 | Solved counts by difficulty, submissions, acceptance rate | PROMPT | P0 |
| ANA-002 | Daily activity, streaks (timezone-aware), course/lesson progress | PROMPT/PDF | P0 |
| ANA-003 | Topic proficiency, quiz performance, learning hours | PROMPT | P1 |
| ANA-004 | Badges, achievements, leaderboards (Redis ZSET), goals | PROMPT | P1 |
| ANA-005 | Precomputed aggregates via event consumers; no heavy aggregation on request | PROMPT | P0 |

### MODULE 12 — Community (COMM)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| COMM-001 | Threaded comments on lessons/problems; likes | PDF/PROMPT | P1 |
| COMM-002 | Posts/discussion threads, bookmarks | PROMPT | P2 |
| COMM-003 | Report → moderation queue → action → audit | PROMPT | P1 |
| COMM-004 | Spam prevention (rate limit, new-account limits, link policy) | PROMPT | P1 |
| COMM-005 | Content sanitization (no raw HTML; Markdown subset) | PROPOSED | P1 |

### MODULE 13 — Newsletter & Resources (NEWS)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| NEWS-001 | Subscribe (double opt-in), unsubscribe (one-click), preferences | PROMPT | P1 |
| NEWS-002 | Articles with categories, authors, scheduling, premium flag | PROMPT/PDF | P1 |
| NEWS-003 | Curated engineering blogs, resume builder, roadmaps pages | PDF | P2 |

Decision OQ-09: build own email pipeline vs. integrate a provider (SES) / reuse Substack.

### MODULE 14 — Subscriptions & Payments (PAY)  *(updated 2026-11-23; design in doc 21)*

| ID | Requirement | Src | Pri |
|---|---|---|---|
| PAY-001 | Plans and INR prices that are **tax-inclusive** final payable amounts; admin-editable; entitlements per plan | DECISION | P0 |
| PAY-002 | Razorpay only (test mode until Live Readiness Gate); provider abstraction; no Stripe in v1 | DECISION | P0 |
| PAY-003 | Four purchase options chosen by the user: monthly pass, annual pass, auto-renewing monthly, auto-renewing annual | DECISION | P0 (passes) / P1 (recurring) — same core |
| PAY-004 | Single billing core: every successful payment → `AccessPeriod`; entitlements derived only from periods; mode differences isolated in provider adapters + `RenewalPolicy` | DECISION | P0 |
| PAY-005 | Webhook signature verification, event-id idempotency, amount/currency validation, nightly reconciliation | PROMPT | P0 |
| PAY-006 | Server-side entitlement checks on every protected resource; expiry enforced at read time | PROMPT | P0 |
| PAY-007 | User billing page: validity, renewal date, payment status, cancel auto-renew, renew/rebuy, invoices, payment history | DECISION | P0 (passes) / P1 (recurring) |
| PAY-008 | Failed recurring payments: PAST_DUE state, notifications, configurable grace period, expiry | DECISION | P1 |
| PAY-009 | Quote shows final payable amount (and tax breakdown when tax enabled) before payment | DECISION | P0 |
| PAY-010 | Configurable GST (`DISABLED`/`ENABLED`/`UNCONFIGURED`), no default rate, invoice/receipt generation, immutable snapshots, credit notes | DECISION | P1 (config model P0) |
| PAY-011 | Refund requests by users; admin review/approve/reject/partial; second approver over threshold; refund records; provider refund; credit note | DECISION | P1 |
| PAY-012 | Configurable refund policy text/rules (versioned, audited) | DECISION | P1 |
| PAY-013 | Live Readiness Gate blocking live charges until legal/KYC/tax/policy items are completed with real data | DECISION | P0 |
| PAY-014 | Coupons (one-time passes only in v1) | PDF | P2 (**Pending P-5**) |
| PAY-015 | Duplicate-payment and paid-but-not-activated auto-flagging into the review queue | DECISION | P1 |

Pending Decisions: P-1…P-8 in doc 21 §11.

### MODULE 15 — Administration & CMS (ADM)

| ID | Requirement | Src | Pri |
|---|---|---|---|
| ADM-001 | Admin console for users, roles, courses, lessons, problems, tests, topics, patterns, companies, quizzes, blogs, newsletter, subscriptions, payments, moderation, settings, analytics | PROMPT | P0 core |
| ADM-002 | Immutable audit log for sensitive actions (who/what/when/before/after/ip) | PROMPT | P0 |
| ADM-003 | Draft → review → publish workflow and version history | PROMPT | P1 |
| ADM-004 | Feature flags and platform settings | PROPOSED | P1 |
| ADM-005 | Admin step-up auth (MFA re-prompt) for destructive actions | PROPOSED | P1 |

### Cross-cutting (XC)

| ID | Requirement | Pri |
|---|---|---|
| XC-001 | Global search (courses, lessons, problems) — start with Postgres FTS (`tsvector`/`pg_trgm`) | P1 |
| XC-002 | Notifications (in-app + email) with preferences | P1 |
| XC-003 | Responsive + accessible (WCAG 2.2 AA target) | P0 |
| XC-004 | i18n-ready strings (English first) | P2 |
| XC-005 | Public SEO pages (SSR) for free lessons/problems | P0 |

### Dependency map (summary)

```
Identity ─► everything
Identity+Payments ─► Entitlements ─► LMS/Problems/AI gating
LMS ─► Progress ─► Analytics ─► Gamification
Problems ─► Submissions ─► Judge workers ─► Analytics
LMS+Problems ─► AI (RAG index)
Problems+Judge+AI ─► Mock Interviews
```

---

## Part C — Open questions
See `19-PROJECT-PROGRESS.md` (Open Decisions) and the first-response summary (OQ-01…OQ-14).
