#!/usr/bin/env bash
# Dumps the LOCAL dev databases (application data and Keycloak identities) to ./backups/ in pg_dump custom format.
# Backups can contain personal data (even fake dev users): ./backups is git-ignored; never commit or publish it.
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; . ./.env; set +a
mkdir -p backups
stamp=$(date +%Y%m%d-%H%M%S)
for spec in "${APP_DB_NAME}:${APP_DB_USER}:${APP_DB_PASSWORD}" "${KEYCLOAK_DB_NAME}:${KEYCLOAK_DB_USER}:${KEYCLOAK_DB_PASSWORD}"; do
  IFS=: read -r db user pw <<<"$spec"
  out="backups/${db}-${stamp}.dump"
  docker compose exec -T -e PGPASSWORD="$pw" postgres pg_dump -U "$user" -d "$db" -Fc --no-owner --no-privileges > "$out"
  echo "wrote $out ($(du -h "$out" | cut -f1))"
done
