#!/usr/bin/env bash
# Copies a backup (see db-backup.sh) into a REMOTE (or any other) PostgreSQL database.
# The target is given ONLY through the environment so it never lands in shell history or git:
#   REMOTE_DATABASE_URL='postgresql://user:password@host:5432/dbname?sslmode=require' \
#     scripts/db-push-remote.sh backups/engineerhub-<stamp>.dump --yes
# Existing objects in the target are dropped and recreated (--clean), so the target must be an EngineerHub database
# you own. Prints only the host and database name, never the password.
set -euo pipefail
cd "$(dirname "$0")/.."
dump="${1:?usage: REMOTE_DATABASE_URL=... db-push-remote.sh <dump file> --yes}"
[[ "${2:-}" == "--yes" ]] || { echo "refusing without --yes (this REPLACES the contents of the target database)"; exit 2; }
set -a; [ -f .env ] && . ./.env; set +a
url="${REMOTE_DATABASE_URL:-${NEON_DIRECT_URL:-}}"
[ -n "$url" ] || { echo "set NEON_DIRECT_URL in .env (or REMOTE_DATABASE_URL), the direct/unpooled postgresql:// URL"; exit 2; }
shown=$(printf '%s' "$url" | sed -E 's#(postgres(ql)?://)[^@]*@#\1***@#')
echo "target: $shown"
docker compose exec -T postgres pg_restore --dbname "$url" --no-owner --no-privileges --clean --if-exists --exit-on-error < "$dump"
echo "restored $dump"
docker compose exec -T postgres psql "$url" -At -c "select 'flyway migrations applied: ' || count(*) from flyway_schema_history where success"
