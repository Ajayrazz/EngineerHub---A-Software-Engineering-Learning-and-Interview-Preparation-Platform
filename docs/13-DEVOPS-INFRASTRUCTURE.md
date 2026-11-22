# 13 — DevOps & Infrastructure

> **Portability note (2026-11-23):** hosting provider is **Pending Decision**. AWS services named in this document (ECS, RDS, ElastiCache, S3, CloudFront, WAF, Secrets Manager, Amazon MQ, Terraform modules) are a *reference deployment*, not a requirement. The application must run from Docker images with environment-based configuration on any provider; see doc 15 and ADR-019.

## 1. Local development
`docker compose up`: Postgres (+pgvector image), Redis, RabbitMQ (mgmt UI), Keycloak (realm import JSON checked in, dev users), MailHog, MinIO (S3-compatible), optional Prometheus/Grafana/Jaeger. Judge worker locally runs with the **weaker dev sandbox** (clearly flagged "NOT SAFE FOR UNTRUSTED CODE"; disabled unless `JUDGE_DEV_UNSAFE=true`). Makefile/`justfile` for common tasks.

## 2. CI/CD (GitHub Actions)
PR pipeline: lint/format → unit tests → integration tests (Testcontainers) → frontend tests → build images → Trivy (image+fs) → SCA → SAST (CodeQL) → gitleaks → OpenAPI diff check → Terraform fmt/validate/plan (+tfsec).
Main pipeline: build + sign images (cosign) → push ECR → deploy staging (ECS rolling/blue-green via CodeDeploy) → smoke + Playwright + ZAP baseline → manual approval → prod. AWS auth via GitHub OIDC (no static keys). DB migrations run as a one-off ECS task before app rollout (expand/contract rule).

## 3. AWS topology (proposed)
- VPC: public (ALB, NAT), private-app, private-data, **isolated-judge** subnets; multi-AZ.
- Edge: CloudFront + AWS WAF (managed rule groups + rate rules + bot control), ACM certs, Route53.
- Compute: ECS Fargate for web/api/worker/keycloak/ai. **Judge workers on EC2** (ASG; `.metal` or nested-virt for Firecracker; gVisor/nsjail on standard instances) — Fargate can't run privileged sandboxes. Scale on RabbitMQ queue depth.
- Data: RDS PostgreSQL (Multi-AZ, encrypted KMS, PITR, TLS-only, `pgvector`), ElastiCache Redis (encryption in transit/at rest, auth), Amazon MQ for RabbitMQ (or self-hosted on ECS/EC2 — OQ-10), S3 (private buckets, versioning, SSE-KMS, separate buckets for media / test-data / backups), ECR.
- Secrets: Secrets Manager (+ rotation); config via SSM Parameter Store.
- Logging/monitoring: CloudWatch logs, Prometheus (AMP) + Grafana (AMG) or self-hosted, OTel collector, alarms → SNS/PagerDuty/Slack.
- Email: SES. DNS/SPF/DKIM/DMARC configured.

## 4. Terraform
Modules: `network`, `edge`, `ecs-service`, `rds`, `redis`, `mq`, `s3`, `judge-fleet`, `iam`, `observability`. Remote state (S3 + DynamoDB lock), per-env workspaces/accounts, policy-as-code checks, drift detection.

## 5. Environments & promotion
`local → ci → staging → prod`. Staging mirrors prod topology at small size; seeded synthetic data (never prod PII). Feature flags decouple deploy from release.

## 6. Cost notes (rough, to validate)
Dominant early costs: RDS Multi-AZ, NAT gateways, judge EC2 fleet, LLM usage. Mitigations: single-AZ staging, scheduled scale-down of non-prod, VPC endpoints instead of NAT for S3, LLM budget caps. Kubernetes deferred until ECS limits are hit.

## 6a. Free/OSS-first cost posture (decision 2026-11-23)
| Component | v1 choice | Licensing cost | Operational cost / limitation |
|---|---|---|---|
| Identity | Self-hosted Keycloak | $0 | Compute + we operate/patch it |
| DB | PostgreSQL (+pgvector) on RDS or self-run | $0 software | RDS is a paid service; Multi-AZ doubles it. Start single-AZ in staging; prod Multi-AZ recommended |
| Cache / MQ | Redis, RabbitMQ — self-run containers for MVP (managed ElastiCache/Amazon MQ later) | $0 | We handle HA/upgrades; persistence volumes + backups for RabbitMQ definitions |
| Judge | Custom worker + isolate | $0 | Dedicated Linux VM(s); small instance to start; scale by queue depth |
| Object storage | S3 + CloudFront (MinIO in dev) | pay-per-use | Low at MVP scale |
| Email | SES (transactional), MailHog dev | pay-per-use (very low) | Domain warm-up/reputation |
| Observability | Prometheus + Grafana + OTel (self-run) / CloudWatch basics | $0 / pay-per-use | Retention & storage sizing |
| Payments | Razorpay | per-transaction fees (no fixed fee) | Settlement timelines, KYC |
| AI (later) | Hosted LLM API; local OSS embeddings + pgvector | LLM is pay-per-token | Budget caps; AI is post-MVP |
| Cheapest viable MVP shape | 1 small VM running Compose (api, web, keycloak, pg, redis, rabbit) + 1 judge VM, **only for staging/early beta** | — | Not HA; move to ECS + managed data stores before real paid traffic. Judge VM stays separate regardless |

## 7. Operations
Runbooks: DLQ replay, stuck-submission sweep, webhook replay, judge host rotation, DB restore, key rotation, incident response. On-call and SLO dashboards defined before public launch.
