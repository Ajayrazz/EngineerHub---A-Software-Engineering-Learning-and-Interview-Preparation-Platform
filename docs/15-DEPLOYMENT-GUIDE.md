# 15 — Deployment, Hosting Options & Portability

Status: **Hosting provider = Pending Decision (H-1).** Nothing here is deployable yet and no step has been verified. This document defines portability rules, hosting requirements, options, and rough cost ranges. **Prices are order-of-magnitude estimates from general knowledge, not quotes — re-check provider pricing before deciding.**

## 1. Portability rules (binding on all code)
1. **Everything ships as OCI/Docker images** (web, api/worker, keycloak config, judge-worker image/VM bootstrap). Local `docker compose` is the reference runtime; any host that can run Compose or a container orchestrator can run EngineerHub.
2. **12-factor configuration:** all settings via environment variables (see `.env.example`); no provider SDK needed to boot.
3. **Cloud-agnostic interfaces** for anything provider-shaped (ADR-019):
   - `ObjectStorage` — S3-compatible API (works with AWS S3, Cloudflare R2, Backblaze B2, MinIO, Wasabi…); presigned URLs through that interface, not CloudFront-specific.
   - `MailSender` — plain SMTP (SES/Brevo/Mailgun/Postfix all speak SMTP).
   - `SecretProvider` — env/Docker secrets by default; cloud secret managers optional adapters.
   - Metrics/logs/traces — OpenTelemetry + Prometheus formats; no vendor agent required.
   - Queue/cache/DB — stock RabbitMQ, Redis (or Valkey), PostgreSQL; no cloud-proprietary variants.
4. **CDN/WAF are optional layers**, not dependencies (Cloudflare free tier, CloudFront, Bunny, or none initially). App-level rate limiting exists regardless of edge.
5. **IaC is a convenience, not coupling:** provider-specific IaC (Terraform/OpenTofu) lives in `infra/<provider>/` and is added only after H-1 is decided; Compose files and Ansible/shell bootstrap are provider-neutral.
6. **No AWS-only services in application code** (no SQS/SNS/DynamoDB/Lambda dependencies).

## 2. Hosting requirements

| Component | Needs | Notes |
|---|---|---|
| web (Next.js) | Node runtime container, ~512 MB–1 GB RAM | SSR + BFF |
| api + worker (Spring Boot) | JVM, ~1–2 GB RAM each at start (can co-run) | Memory is the main constraint on tiny VPSs |
| PostgreSQL (+pgvector later) | 1–2 GB RAM min; SSD; **backups + PITR (WAL archiving)** | Self-run needs disciplined backups/restore drills |
| Redis | ~256–512 MB | Rebuildable data only |
| RabbitMQ | ~512 MB | Persistent volume + definitions backup |
| Keycloak | **~1–2 GB RAM** (JVM) | Heavy for tiny hosts; own DB |
| Object storage | S3-compatible | Media, test-case bundles (private bucket), certificates, invoices |
| **Judge VM** | **Linux, root, cgroup v2, dedicated, no egress, KVM not required for isolate** | **Must be a VM or dedicated server, not a shared container PaaS**; separate from the app hosts |
| TLS / DNS | Let's Encrypt via reverse proxy (Caddy/Traefik/Nginx) | |
| Email | SMTP provider w/ SPF/DKIM/DMARC | |

Rough total for early beta: **~6–8 GB RAM across app services (can be one 8 GB VM) + a separate 2–4 vCPU judge VM**.

## 3. Deployment options (trade-offs)

| Option | Shape | Rough monthly cost (order of magnitude, verify) | Pros | Cons / risks |
|---|---|---|---|---|
| **A. Two cheap VMs (recommended for dev/beta)** | VM1: Compose (proxy, web, api+worker, keycloak, pg, redis, rabbit, observability-lite). VM2: judge. Object storage + SMTP from any provider | ~US$20–50 total (e.g. an 8 GB VM + a 2–4 vCPU VM at EU/IN-region budget hosts) | Cheapest, simple, portable, full control, judge isolation is real | No HA; self-managed backups/patching; single point of failure; you are ops |
| **B. Managed data + VMs** | A, but PostgreSQL (and maybe Redis) managed | ~US$60–150 | Backups/PITR handled; fewer ops risks | Higher cost; some lock-in to provider's Postgres features (minimal) |
| **C. Container PaaS for web/api + VM for judge** | Render/Fly/Railway/etc. + managed DB + separate judge VM | ~US$50–150+ | Easy deploys | Keycloak/RabbitMQ awkward or extra cost; cost grows; judge still needs a VM; PaaS differences hurt portability |
| **D. AWS (reference design in docs 03/04/13)** | ECS Fargate + RDS + ElastiCache + S3/CloudFront + EC2 judge | ~US$200–500+ at minimum prod shape | HA, managed services, scale | Most expensive; more moving parts for a solo dev; NAT/RDS Multi-AZ dominate cost |
| **E. Kubernetes (any provider)** | k3s / managed K8s | variable | Scale-out | Too much ops for solo dev now; **deferred** |

**Recommendation (not final — Pending Decision H-1):** develop locally with Compose; beta on **Option A**; move DB to managed (B) before taking real paid users if budget allows; revisit D/E only if scale or compliance demands. Region matters for latency to Indian users and for data-residency/legal questions (**H-2**).

## 4. Limitations of the cheap path (be honest)
- Single-VM means downtime during upgrades/failures; acceptable for beta, not for paid launch SLA claims.
- Self-hosted Postgres requires tested backups (daily base + WAL archive to object storage, restore drill monthly).
- Keycloak + JVMs on one small VM can OOM; size RAM generously, set container memory limits, add swap carefully.
- Judge VM compromise must not expose app secrets: **no shared credentials, no network path** from judge to DB/Redis; only RabbitMQ (restricted vhost) + test-bundle access.
- No WAF by default; use a free-tier edge (e.g. Cloudflare-style) if acceptable, plus app rate limits.

## 5. Release procedure (target, provider-neutral)
1. Merge to `main` → CI builds images, runs tests/scans, pushes to a registry (GHCR/Docker Hub/any).
2. Deploy to staging (Compose pull + up on a staging host, or same VM different project name).
3. DB migrations run via one-off container before app start (expand/contract).
4. Smoke tests + Playwright; manual approval; deploy prod; health checks.
5. Rollback: redeploy previous image tag; DB changes forward-fix only.
Automation: GitHub Actions + SSH/`docker compose` (or Watchtower-style pull) initially; no cloud-specific deploy tool required.

## 6. Disaster recovery (target)
Daily encrypted off-host backups (DB base + WAL, Keycloak realm export in git, object storage versioning/replication), documented restore runbook, restore drill before first paid user.

## 7. Pending decisions (hosting)
| ID | Question |
|---|---|
| H-1 | Final provider/option for beta and for production |
| H-2 | Region/data residency (India-hosted preferred for latency & compliance?) |
| H-3 | CDN/WAF provider (or none initially) |
| H-4 | SMTP/email provider |
| H-5 | Object storage provider |
