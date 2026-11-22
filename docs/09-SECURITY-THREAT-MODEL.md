# 09 — Security Threat Model

Method: STRIDE per trust boundary, mapped to OWASP ASVS chapters. Initial version — to be reviewed before Phase 1 (auth) and Phase 4 (judge).

## 1. Assets
Credentials/sessions; PII; payment state & entitlements; hidden test cases & editorials (IP); premium content/video; admin capabilities; source of platform; judge hosts; LLM API budget; email reputation.

## 2. Actors
Anonymous attacker; malicious registered user; malicious/compromised staff account; compromised dependency/CI; malicious submitted code; prompt-injecting content; payment-fraud actor; scraper.

## 3. Trust boundaries
Internet↔Edge · Edge↔Web/BFF · BFF↔API · API↔Data · API↔Judge · API↔AI↔LLM · API↔Payment providers · CI/CD↔AWS.

## 4. Threats & mitigations (top items)

| # | Threat (STRIDE) | Mitigation | ASVS |
|---|---|---|---|
| T1 | Credential stuffing / brute force (S) | Keycloak lockout, WAF rate rules, CAPTCHA escalation, breached-password check, login anomaly alerts | V2 |
| T2 | Token theft via XSS (S/I) | Tokens server-side only; strict CSP w/ nonces; no raw HTML rendering; sanitize rich text; Trusted Types | V3, V5 |
| T3 | CSRF on BFF (T) | SameSite + CSRF header token + Origin check | V4 |
| T4 | IDOR / broken access control (E) | Ownership from context; policy per endpoint; authZ test matrix; opaque 404s | V4 |
| T5 | Privilege escalation via mass assignment (E) | Explicit DTOs; no entity binding; role-grant guards | V5 |
| T6 | SQLi (T) | JPA/parameterized only; ban string-concat queries (static analysis); least-priv DB user | V5 |
| T7 | SSRF (I) | No user-supplied URL fetching; if needed (link previews/avatars) allowlist + egress proxy + block metadata IP; IMDSv2 | V12 |
| T8 | Malicious code escapes sandbox (E) | §5 below | custom |
| T9 | Hidden test case leakage (I) | Tests only in private store, judge-only access; outputs of hidden tests never returned; error output truncated/sanitized; side-channel limiting (rate limits, verdict-only) | V4 |
| T10 | Payment webhook forgery / replay (S/T) | Signature verification w/ raw body, timestamp tolerance, event-id uniqueness, server-side fetch of payment status for critical transitions | V13 |
| T11 | Double processing (T) | Idempotency keys, unique constraints, state machines | V1 |
| T12 | Entitlement bypass (E) | Server-side checks; premium bodies not serialized; signed, short-lived media URLs; no premium content in public ISR/CDN cache | V4 |
| T13 | Scraping premium/problem bank (I) | Rate limits, bot management (WAF), watermarking of premium text/video (later), ToS | V11 |
| T14 | Prompt injection / data exfil via AI (T/I) | Retrieved text as data; no tools with side-effects; entitlement-filtered retrieval; output filtering; per-user quotas; no secrets in prompts | V5 |
| T15 | LLM cost abuse (D) | Per-user/tier quotas, global budget cap, kill-switch, anomaly alerts | V11 |
| T16 | DoS on submissions (D) | Per-user concurrency & rate limits, queue length caps, backpressure, WAF | V11 |
| T17 | Upload abuse (T/D) | Presigned URLs with content-type/size conditions, malware scan, no execution, separate domain for user content | V12 |
| T18 | Supply-chain compromise (T) | Pinned versions/lockfiles, Dependabot/Renovate, SCA + Trivy gates, signed images, minimal base images, GitHub Actions pinned by SHA, OIDC to AWS (no long-lived keys) | V14 |
| T19 | Secret leakage (I) | Secrets Manager, gitleaks pre-commit + CI, no secrets in images/logs | V2, V14 |
| T20 | Admin account takeover (S/E) | Mandatory MFA, step-up auth, IP/device alerts, short sessions, audit log | V2 |
| T21 | Log injection / PII in logs (I/R) | Structured logging, redaction filters, no tokens/source code in logs | V7 |
| T22 | Repudiation of admin actions (R) | Append-only audit log w/ actor, before/after, trace id | V7 |
| T23 | Email abuse (newsletter subscribe bombing) (D) | Double opt-in, CAPTCHA, per-IP/email rate limit | V11 |
| T24 | Open redirect / account-link takeover (S) | Redirect URI exact-match allowlist; verified-email linking only | V3 |
| T25 | Clickjacking / header weaknesses | CSP frame-ancestors, HSTS preload, nosniff, Referrer-Policy, Permissions-Policy | V14 |

## 5. Code execution threat model (summary; full in doc 10)
Assume submitted code is fully hostile: fork bombs, memory exhaustion, disk filling, network egress (exfil, internal scanning, crypto mining), reading judge secrets/hidden tests, kernel exploits, timing side channels, compiler abuse (macro/`#include "/etc/passwd"`, giant template expansion), zip/log bombs in output.
Defense in depth: dedicated hosts/VPC with **no egress and no IAM role**; microVM/gVisor isolation; seccomp + cgroups v2 + rlimits + read-only rootfs + tmpfs workdir; non-root unique UID per run; hidden tests injected only at execution time and never visible to the user process where avoidable (stdin pipe, not files); output size caps; wall-clock + CPU timeouts enforced from outside the sandbox; ephemeral instance per submission or per small batch of *same-trust* submissions; host rotation.

## 6. Data classification
| Class | Examples | Controls |
|---|---|---|
| Restricted | credentials, tokens, payment refs, hidden tests, API keys | Encrypted, least access, never logged |
| Confidential | PII, source code, interview transcripts, premium content | Encrypted at rest/transit, RBAC, retention limits |
| Internal | analytics, configs | Auth required |
| Public | free lessons, public problem statements | Integrity controls |

## 7. Secret management policy
Secrets via a secret store appropriate to the host (cloud secret manager, Docker/Compose secrets, or SOPS/age-encrypted files) — never plain env files committed; per-env; rotation (DB creds auto-rotation, API keys ≤ 90 d); IAM task roles, no static AWS keys; local dev uses `.env` (git-ignored) with fake values; gitleaks in pre-commit + CI.

## 8. Secure coding checklist (PR gate)
- [ ] Every endpoint has an explicit authz policy + negative tests
- [ ] DTO validation (size, range, regex allowlist); no entity binding
- [ ] No string-built SQL/JPQL; no `native` queries with concatenation
- [ ] Output encoding / sanitized rich text only
- [ ] Ownership derived from principal, not request
- [ ] Entitlement checked server-side
- [ ] No sensitive data in logs/URLs/error messages
- [ ] Rate limit class assigned
- [ ] Audit event for privileged/state-sensitive actions
- [ ] New dependency reviewed (license, maintenance, CVEs)
- [ ] Migration reviewed for lock/backward compat

## 9. Backup & DR
RDS automated backups + PITR (35 d), cross-region snapshot copy, quarterly restore drill; S3 versioning + replication for content; Redis is rebuildable (not backed up as truth); RabbitMQ durable + outbox guarantees replay; IaC for full environment rebuild; documented runbooks; RPO ≤ 5 min / RTO ≤ 1 h (proposed).

## 10. Security testing plan
SAST (CodeQL — needs a public repo or GitHub Advanced Security; Semgrep as fallback), SCA (OSV-Scanner on CycloneDX SBOM + Dependabot — see ADR-024), secrets scan (gitleaks, pre-commit + CI full history), container scan (Trivy or alternative — deferred until images exist, ADR-024), IaC scan (tfsec/Checkov), DAST (OWASP ZAP baseline in CI on staging, full scan nightly), authZ matrix tests, fuzzing of judge input paths, annual third-party pentest + dedicated **sandbox escape review** before judge goes public, bug bounty/VDP later.


## Addendum (M0.4): logging hygiene
Unexpected exceptions are logged server-side with message and stack trace (client sees only a generic problem + correlation id). Therefore: **never put secrets, tokens, SQL with parameters or personal data into exception messages**; review for this in PRs. Verified: API logs contain no bearer tokens or passwords after authenticated traffic. Future hardening: log scrubbing filter for known secret patterns.
