# 11 — Frontend Architecture

## 1. Stack
Next.js (App Router) · React · TypeScript (strict) · Tailwind · shadcn/ui · TanStack Query · Zustand · React Hook Form + Zod · Monaco · Recharts · Framer Motion · TipTap. Exact versions pinned at scaffold time after a compatibility check (Next/React/Tailwind/shadcn must be mutually compatible).

## 2. Structure (feature-oriented)
```
frontend/
  src/
    app/                      # routes only (thin): layouts, pages, route handlers
      (marketing)/            # landing, pricing, about  (SSR/ISR, SEO)
      (learn)/learn/[course]/[lesson]/
      (practice)/practice/...  problems/[slug]
      (interview)/interview/...
      (account)/dashboard, settings
      (admin)/admin/...        # separate layout, guarded
      auth/ (login, callback, logout)   # BFF route handlers
      api/proxy/[...path]/     # BFF → Spring API (adds Bearer, CSRF check)
    features/                 # auth, courses, reader, problems, editor, submissions, quizzes,
                              # visualizations, roadmaps, interviews, analytics, community,
                              # billing, ai-assistant, admin-*  (each: components/, hooks/, api/, schemas/, types.ts)
    components/ui/            # shadcn primitives
    components/shared/        # app-level composites
    lib/                      # api client (generated from OpenAPI), auth/session, utils, env (zod-validated)
    stores/                   # zustand (UI-only state: editor prefs, reader settings, sidebar)
    styles/ tokens.css        # design tokens
  e2e/                        # Playwright
```
Rules: features don't import each other's internals (only `index.ts` public exports); server state in TanStack Query, UI state in Zustand, form state in RHF; no business/authorization logic in the client (UI hides, **server enforces**).

## 3. Rendering strategy
| Page | Strategy |
|---|---|
| Landing, pricing, free lesson/problem pages | SSR/ISR + CDN cache (SEO) — never contain per-user or premium data |
| Premium lesson | Dynamic server render with session; body fetched with auth; teaser if not entitled |
| Dashboard/practice list | Server component shell + client islands with TanStack Query |
| Editor/visualizer/quiz | Client components (dynamic import, `ssr:false` for Monaco) |
| Admin | Client-heavy, behind route guard + server-verified permissions |

## 4. Reader (LMS) UX components
Resizable collapsible sidebar (virtualized for 700+ items, search/filter), "On this page" TOC with scrollspy, reading-progress bar, bottom action bar (notes/star/complete/ask AI/prev/next), reader settings (font, width, theme), selection toolbar (highlight colors / inline note / Ask AI), content-block renderer registry (`type → React component`, unknown type → safe fallback), comments panel.

## 5. Editor & judge UI
Monaco (lazy loaded, per-language models, vim/emacs keymap optional later), tabs for Test Cases / Output / Feedback, run vs submit, SSE hook `useSubmissionStatus(id)` with polling fallback, split-pane layout persisted per user, local draft autosave (IndexedDB) keyed by problem+language (clearable).

## 6. Visualization framework
`<Visualizer trace={Step[]} renderer={...}>`: step-trace contract `{state, highlightLines[], description}`; engine = reducer + timeline controls; renderers per data structure (array, linked list, tree, graph, matrix, dp-table). Traces produced by TS algorithm generators (pure/deterministic, unit-tested against reference outputs) or authored JSON. Canvas/SVG; respects `prefers-reduced-motion`.

## 7. Design system
Dark-first tokens (CSS variables) with light theme; brand/visual identity **original** (not AlgoMaster's mark/palette); type scale, spacing 4px grid; WCAG AA contrast; focus-visible rings; skeleton loaders; empty/error states; toast + inline errors; Storybook (optional, P1) for primitives.

## 8. Security (frontend)
Strict CSP with nonces (middleware); no `dangerouslySetInnerHTML` except through a single audited sanitizer component (DOMPurify) — preferably none because content is structured JSON; no tokens in JS-accessible storage; `Zod`-validated API responses at boundaries for critical flows; third-party scripts minimal and SRI'd; env var hygiene (`NEXT_PUBLIC_*` reviewed — never secrets).

## 9. Testing
Vitest + RTL for components/hooks, MSW for API mocks, Playwright E2E (login, enroll, read, submit, checkout-mock, admin publish), axe-core accessibility checks in E2E, visual regression optional.

## 10. Performance
Route-level code splitting, Monaco/visualizer lazy, image optimization via CDN, Query prefetch on hover, virtualization for long lists, bundle budget in CI.

## Addendum (M0.5, 2026-11-23) — what is actually implemented
- **Rendering:** Cache Components is OFF and all pages render per request so a CSP nonce applies (ADR-028). The "SSR/ISR + CDN cache for public pages" plan in §3 conflicts with nonce CSP and is **an open design question for Phase 2**.
- **Structure:** `src/lib/{env,session,auth,security}` (server-only logic; `session/` and `security/` are framework-free and unit-tested), `src/app/{auth,api}` (BFF route handlers), `src/proxy.ts` (CSP), `src/components`. Server-only modules import `server-only`.
- **BFF API surface:** `GET /auth/login`, `GET /auth/callback`, `POST /auth/logout`, `GET /api/session` (identity + CSRF token, never tokens), `ANY /api/proxy/<path>` → `<API>/api/v1/<path>`.
- **Next 16 specifics hit:** `proxy.ts` replaces `middleware.ts`; `cookies()`/route `params` are async; `RouteContext`/`LayoutProps`/`PageProps` types are generated by `next typegen` (run before `tsc`).
- **Testing:** Vitest for pure logic (84 tests); Playwright for the browser flows (35). React Testing Library/component tests are **not set up yet** — added when the UI has components worth testing in isolation.
