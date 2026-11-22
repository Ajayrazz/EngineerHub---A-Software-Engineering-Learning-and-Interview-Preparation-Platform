# 00 — Product Vision

> Status: **Draft for approval** (planning milestone; no code exists yet)

## 1. What we are building

EngineerHub (working name; see Open Question OQ-01) is an all-in-one **Software Engineering Learning and Interview Preparation Platform**. It unifies:

- an interactive LMS (courses → modules → chapters → lessons → content blocks),
- a coding practice platform with a secure online judge,
- structured interview prep (coding, system design, low-level design, AI coding),
- an AI learning assistant (context-aware, RAG-based),
- progress analytics and gamification,
- community, newsletter, subscriptions and an admin/CMS console.

One identity, one profile, one permission model, one progress model, one entitlement model across all modules.

## 2. Reference material

`Basic Overview.pdf` (24 pages) shows screenshots of an existing third-party product, AlgoMaster. It is a **reference for product structure and UX direction only**. We are not copying its content, copy text, branding, logos, testimonials, creator identity or problem/lesson text. See OQ-01 and OQ-02. Our own design system and our own content are required.

## 3. Target users

| Persona | Needs |
|---|---|
| Learner (free) | Free lessons, limited practice, progress tracking |
| Learner (premium) | Full curriculum, video, AI, mock interviews, unlimited submissions |
| Interview candidate | Company-wise questions, mock interviews, timed practice, feedback |
| Instructor | Author/edit assigned courses, see engagement |
| Problem setter | Author problems, test cases, editorials |
| Content manager | Publish workflow, blogs, newsletter, roadmaps |
| Moderator | Handle reports, moderate community |
| Admin / Super admin | Users, roles, billing, settings, audit |

## 4. Product principles

1. **Content is data.** No course/lesson/problem is hardcoded in the frontend.
2. **Backend is authoritative** for authorization, entitlements, payments and judging.
3. **Security-first**; untrusted code never runs near the application.
4. **Modular monolith first**; microservice-ready boundaries.
5. **Interactive over static** wherever it teaches better.
6. **AI is assistance, not authority**; evaluations carry disclaimers.
7. **Deterministic visualizations**, not AI-generated animations.

## 5. Success definition

See the prompt's §15; operationalized as measurable NFRs in `02-NON-FUNCTIONAL-REQUIREMENTS.md`.

## 6. Out of scope for the first release (proposed)

Native mobile apps, live classes, marketplaces for third-party creators, contests with proctoring, multi-tenant/white-label. Revisit post-MVP.
