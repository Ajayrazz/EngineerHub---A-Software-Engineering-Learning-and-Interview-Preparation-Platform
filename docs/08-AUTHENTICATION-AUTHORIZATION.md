# 08 — Authentication & Authorization

## 1. Components
- **Keycloak**: users' credentials, MFA (TOTP/WebAuthn), social IdP brokering (Google, GitHub), email verification, password policy, brute-force detection, session/refresh policy.
- **Next.js BFF**: confidential OIDC client; holds tokens server-side; browser gets an opaque `HttpOnly; Secure; SameSite=Lax` session cookie (`__Host-` prefix).
- **Spring Boot**: OAuth2 Resource Server (stateless JWT validation); app-level permissions and entitlements.

## 2. Authentication decisions
| Topic | Decision |
|---|---|
| Flow | Authorization Code + PKCE (S256), `state` + `nonce` validated |
| Token storage | Server-side only (Redis-backed BFF session). Never localStorage/sessionStorage |
| Access token | 5 min TTL |
| Refresh token | Rotating, reuse detection → revoke session family; idle 30 d / max 90 d (tunable) |
| Admin | MFA required (Keycloak required action + conditional flow); step-up re-auth for destructive ops (`acr`/`auth_time` check) |
| Password policy | Min length 12 for staff, ≥10 users; breached-password check; no composition-rule theatre; Argon2id/PBKDF2 per Keycloak support |
| Brute force | Keycloak lockout + WAF/IP rate limits + CAPTCHA escalation |
| Account linking | Only when provider email is verified and matches; otherwise require login to existing account first |
| Logout | RP-initiated logout; revoke refresh token; clear BFF session |
| CSRF | SameSite=Lax + double-submit/custom-header token on state-changing BFF routes; Origin check |
| Clickjacking | CSP `frame-ancestors 'none'` (except intentional embeds) |

Alternative considered: Spring Authorization Server / custom auth. Rejected: MFA, social login, account console and abuse protections are non-trivial to build safely; Keycloak ships them. Trade-off: operating Keycloak (HA, upgrades, theming) — see risks.

## 3. Authorization model (RBAC + scoped grants + entitlements)

Three independent axes, evaluated server-side:
1. **Role/permission** — what *staff capabilities* the user has.
2. **Scope** — which resources those capabilities apply to.
3. **Entitlement** — what *content/features* the user has paid for.

`PREMIUM_USER`/`FREE_USER`/`USER` are derived states (see ADR-006), not stored roles; the roles list in the brief is satisfied as computed authorities.

### Authorization matrix (initial; ✓ = allowed, S = scoped to assigned resources, — = no)

| Capability | USER/FREE | PREMIUM | INSTRUCTOR | PROBLEM_SETTER | CONTENT_MGR | MODERATOR | ADMIN | SUPER_ADMIN |
|---|---|---|---|---|---|---|---|---|
| Read free content | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Read premium content | — | ✓ | S | S | ✓ | — | ✓ | ✓ |
| Submit code | ✓ (quota) | ✓ (higher) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Use AI assistant | limited | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Edit course content | — | — | S | — | ✓ | — | ✓ | ✓ |
| Publish course | — | — | — | — | ✓ | — | ✓ | ✓ |
| Create/edit problems & tests | — | — | — | S/✓ | — | — | ✓ | ✓ |
| Publish problem | — | — | — | needs review | — | — | ✓ | ✓ |
| Moderate comments/reports | — | — | — | — | — | ✓ | ✓ | ✓ |
| Manage users (suspend) | — | — | — | — | — | partial (mute) | ✓ | ✓ |
| Grant roles | — | — | — | — | — | — | non-admin roles | ✓ |
| Manage plans/refunds | — | — | — | — | — | — | ✓ | ✓ |
| View audit logs | — | — | — | — | — | — | ✓ | ✓ |
| Platform settings / flags | — | — | — | — | — | — | partial | ✓ |

Matrix is encoded as seed data (`roles`, `permissions`, `role_permissions`) and covered by a parameterized authorization test suite generated from this table (each cell tested for allow/deny).

## 4. Rules
1. Deny by default; every controller method has an explicit policy annotation (CI check fails on unannotated endpoints).
2. Object-level checks on every id-bearing route (IDOR): owner from security context.
3. Privilege escalation guard: users cannot grant roles ≥ their own; SUPER_ADMIN grants are dual-approved (proposed).
4. Role changes take effect on next token refresh at most; sensitive revocation also bumps a `token_version`/denylist in Redis checked by the API for admin-class tokens.
5. Entitlement check at the service layer next to the data fetch, not only at the edge.
6. Service-to-service: separate Keycloak client credentials with narrow scopes (judge, AI); mTLS inside VPC where feasible.

## 5. Self-hosted Keycloak (confirmed 2026-11-23)

| Topic | Plan |
|---|---|
| Hosting | Container on ECS Fargate (prod: ≥2 tasks behind ALB, sticky not required with Infinispan/JDBC-ping clustering per Keycloak docs); local: Compose |
| Storage | Dedicated PostgreSQL database/schema **separate from app DB** (own credentials, own backups) |
| Version | Pin to a current stable Keycloak release at scaffold time; upgrade runbook rehearsed in staging; subscribe to security advisories |
| Config as code | Realm exported to `infra/keycloak/` (clients, roles, flows, required actions, token lifespans, password policy, brute-force settings); applied via import/`keycloak-config-cli` in CI — no manual console drift |
| Clients | `engineerhub-web` (confidential, BFF, code+PKCE), `engineerhub-api` (resource server, bearer-only semantics via audience), `judge-worker` & `ai-service` (client-credentials, narrow scopes), `admin-tools` (if needed) |
| Roles | Realm roles = staff roles only (SUPER_ADMIN, ADMIN, CONTENT_MANAGER, PROBLEM_SETTER, MODERATOR, INSTRUCTOR); granular + scoped permissions live in the app DB (doc 05 §3). Premium/free is **not** a Keycloak role (ADR-006) |
| Token claims | `sub`, `email`, `email_verified`, realm roles, `aud=engineerhub-api`; keep tokens small; no entitlement data in tokens |
| User sync | First authenticated request provisions `identity.users` row keyed by `sub` (idempotent upsert); optional event listener later. Email/username changes sync on login |
| Hardening | Admin console on a private/management hostname (VPN/IP-restricted), no default admin account, MFA for admins, brute-force detection on, CORS/redirect URI exact allowlists, themes minimal, HTTPS only, hostname strict, health/metrics endpoints on management port |
| Email | SMTP via SES (prod) / MailHog (dev) for verification & reset |
| Social login | Google, GitHub IdPs configured with our OAuth apps; verified-email linking rule (§2) |
| Backups/DR | DB PITR; realm export in git; documented restore + key-rotation procedure |
| Limitations/cost | We carry patching, upgrades and uptime; Fargate+RDS cost for the IdP; mitigation = IaC + runbooks. Revisit managed hosting only if ops burden proves too high |
| Integration notes | Next.js BFF: standard OIDC library (e.g. Auth.js/openid-client — choice recorded in Phase 1 ADR) with server-side session store; Spring: `spring-boot-starter-oauth2-resource-server`, custom `JwtAuthenticationConverter` mapping roles. No Keycloak adapters (deprecated) |

## 6. API-side implementation status (M0.4, verified 2026-11-23)

**Implemented and verified (tests + live against dev Keycloak):** stateless OAuth2 resource server; JWT signature via JWKS discovered from the issuer; issuer, expiry and **audience** (`engineerhub-api`) validation; Keycloak realm roles → `ROLE_*` through an **explicit allowlist** (SUPER_ADMIN, ADMIN, CONTENT_MANAGER, PROBLEM_SETTER, MODERATOR, INSTRUCTOR — premium/free never become roles, ADR-006); method security with the mandatory-policy build rule (ADR-025); 401/403 as problem+json (`WWW-Authenticate: Bearer`, only a standard error code, never exception text); 503 + `Retry-After` when the IdP is unreachable (ADR-026); strict response headers (CSP `default-src 'none'`, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, `no-store`); CORS = exact-origin allowlist, empty by default, `*` refused at start-up; only `/actuator/health` exposed (no details); fail-fast config (no DB/issuer/audience → refuses to start).

**Live checks (`scripts/verify-api-auth.sh`, 33 checks with real RS256 tokens):** garbage token, **tampered signature**, **a learner token whose payload was edited to claim SUPER_ADMIN (privilege escalation)**, **valid token with the wrong audience**, **token from a different realm** → all 401; learner/moderator → 403 on admin endpoint; admin/superadmin → 200; Keycloak built-in roles filtered; no tokens/passwords in logs.

**Dev-only Keycloak clients** (in the dev realm export only; must not exist in staging/prod realms): `engineerhub-dev-cli` (password grant, used by the verification script) and `engineerhub-dev-noaud` (negative control issuing tokens without the API audience).

**Not implemented yet (tracked):** refresh/revocation checks for admin-class tokens (`token_version`/denylist, §4 rule 4); rate limiting and brute-force at API level (Phase 1); request-size limits (Phase 1, together with rate limiting); permission/scoped-grant tables and `@authz.can(...)` evaluator (Phase 1); BFF session/PKCE login (M0.5); admin MFA and step-up auth (Phase 1); separate management port for actuator.
