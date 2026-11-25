# 25 — Putting the database online (managed PostgreSQL)

Status: **prepared, not executed.** Hosting is still Pending Decision H-1 (docs/15); the owner chose *managed PostgreSQL* as the first online step. Nothing below has been run against a real provider (no account exists yet); the **scripts were tested against a scratch local database**.

## What is "real data" and where it lives
| Data | Where | Safe because |
|---|---|---|
| Courses, lessons, quizzes, problems, tests | PostgreSQL (`catalog`, `problems` schemas) **and** `content/*.json` in git | re-seedable with `make seed-content`; the JSON is the source of truth for the starter content |
| Users (identities, passwords) | Keycloak's own database `keycloak` | back up with the rest; **never** in git |
| Progress, bookmarks, payments, receipts, audit log | PostgreSQL (`learning`, `problems`, `billing`, `analytics`, `shared`) | exists only in the database: **back it up** |

## Step 0: pick a provider that offers PostgreSQL 18
Migrations call `uuidv7()`, which is **built into PostgreSQL 18**. Before creating anything, confirm the provider lists version 18 (check its current docs; managed services lag the latest major). On 17 or older the first migrations fail. Options to compare: Neon, Supabase, AWS RDS/Aurora, Google Cloud SQL, Azure, DigitalOcean/Aiven/Crunchy. Prefer: automatic daily backups with point-in-time recovery, TLS required, an EU/India region near your users, and a clear price for storage.

## Step 1: create the database and a least-privilege role (owner action)
In the provider console create a database named `engineerhub` and a login role for the application that **owns** it (Flyway needs DDL rights). Do not use the provider's admin role in the app. Copy the connection string (it must end with `?sslmode=require`).

## Step 2: keep the secret out of git
Put the connection details in `.env` or `.env.production` (both git-ignored), never in a file that is committed: `DB_URL=jdbc:postgresql://HOST:5432/engineerhub?sslmode=require`, `DB_USER=...`, `DB_PASSWORD=...` (the names the production profile reads, see `application.yml`). Treat the connection string like a password.

## Step 3: copy the current data up
```bash
make db-backup                                      # fresh local dump into ./backups (git-ignored)
REMOTE_DATABASE_URL='postgresql://USER:PASS@HOST:5432/engineerhub?sslmode=require' \
  scripts/db-push-remote.sh backups/engineerhub-<stamp>.dump --yes
```
The script refuses to run without `--yes`, prints only host and database (never the password), replaces the target's contents, and finally prints how many Flyway migrations are recorded, so the backend can later start against it and validate. Run `make db-restore-check DUMP=...` first if you want proof the dump itself restores.

## Step 4: point the backend at it and verify
Start the backend with `DB_URL`, `DB_USER`, `DB_PASSWORD` set; Flyway should report "schema up to date" and `/actuator/health` must be `UP`. The rest of the stack (Keycloak, Redis, RabbitMQ) stays local until the hosting decision for the whole platform is made.

## Backup policy once online
1. Turn on the provider's automatic backups and point-in-time recovery; note the retention period.
2. Weekly: `pg_dump` the online database to a bucket you control and **restore-test** it into a scratch database (the same row-count comparison `db-restore-check.sh` does).
3. Never run `make dev-reset` (it deletes the local volume) without a fresh `make db-backup`.

## Adding the ~700 problems
Problems go into this same database. The pipeline already exists: write each problem (statement, constraints, hints, tests) in `content/tools`, let the build refuse any problem whose reference and brute-force solutions disagree, then `make seed-content` loads it through the admin API; for the online database run the seed against the online API or restore a dump. Content must be **original** (docs/20): third-party problem statements cannot be copied in, whatever the platform.
