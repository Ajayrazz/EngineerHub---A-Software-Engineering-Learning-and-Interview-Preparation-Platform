# 02 — Non-Functional Requirements

Targets are **proposed initial SLOs**; confirm against budget and expected traffic (OQ-11).

| ID | Category | Requirement | Target |
|---|---|---|---|
| NFR-PERF-001 | Latency | Read APIs (cached) p95 | < 200 ms |
| NFR-PERF-002 | Latency | Write APIs p95 | < 400 ms |
| NFR-PERF-003 | Web | LCP on public lesson/problem pages (4G mid-tier) | < 2.5 s |
| NFR-PERF-004 | Judge | Submission queue-to-start p95 (normal load) | < 3 s |
| NFR-PERF-005 | Judge | Run (sample) end-to-end p95 for simple problems | < 5 s |
| NFR-SCAL-001 | Scale | Stateless API; horizontal scale behind ALB | required |
| NFR-SCAL-002 | Scale | Judge workers scale on queue depth independently | required |
| NFR-SCAL-003 | Scale | Design for 100k registered / ~5k concurrent initially; 10× headroom path documented | proposed |
| NFR-AVAIL-001 | Availability | API monthly availability | 99.9 % |
| NFR-AVAIL-002 | Degradation | Judge/AI outage must not break reading/browsing | required |
| NFR-DUR-001 | Durability | Postgres PITR; RPO ≤ 5 min, RTO ≤ 1 h | proposed |
| NFR-SEC-001 | Security | OWASP ASVS L2 baseline; L3 for judge/payments/auth where practical | required |
| NFR-SEC-002 | Security | No secrets in repo; secrets in AWS Secrets Manager | required |
| NFR-SEC-003 | Security | Dependency + image scanning gate in CI (fail on critical/high w/o waiver) | required |
| NFR-SEC-004 | Security | All admin actions audited, append-only | required |
| NFR-PRIV-001 | Privacy | Data classification, retention policy, deletion/export flows | required |
| NFR-OBS-001 | Observability | Structured JSON logs w/ trace & correlation IDs; metrics; traces | required |
| NFR-OBS-002 | Observability | Alerting on SLO burn, queue depth, DLQ > 0, judge error rate, payment webhook failures | required |
| NFR-MAINT-001 | Maintainability | Module boundaries enforced by tests (ArchUnit / Spring Modulith verification) | required |
| NFR-MAINT-002 | Maintainability | Test pyramid + coverage floor (domain/service ≥ 80 % line, critical security paths 100 % scenario-covered) | proposed |
| NFR-A11Y-001 | Accessibility | WCAG 2.2 AA for core flows | required |
| NFR-COMP-001 | Compat | Latest 2 versions of Chrome/Edge/Firefox/Safari; mobile Safari/Chrome | required |
| NFR-COST-001 | Cost | Per-user AI cost metered; hard budget caps with kill-switch | required |
| NFR-DATA-001 | Data | Schema changes only via Flyway, backward-compatible (expand/contract) for zero-downtime deploys | required |
| NFR-API-001 | API | Versioned (`/api/v1`), OpenAPI generated, pagination mandatory on lists | required |
| NFR-IDEMP-001 | Reliability | All queue consumers and webhook handlers idempotent | required |
