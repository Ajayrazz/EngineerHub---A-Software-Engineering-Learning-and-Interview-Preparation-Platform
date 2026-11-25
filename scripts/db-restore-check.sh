#!/usr/bin/env bash
# Proves a backup is restorable: restores a dump into a THROWAWAY database, compares row counts with the live
# database, then drops the scratch database. It never touches the live database.
# Usage: scripts/db-restore-check.sh backups/engineerhub-<stamp>.dump
set -euo pipefail
cd "$(dirname "$0")/.."
dump="${1:?usage: db-restore-check.sh <dump file>}"
set -a; . ./.env; set +a
scratch="restore_check_$$"
SU=${POSTGRES_SUPERUSER}; export PGPASSWORD="${POSTGRES_SUPERUSER_PASSWORD}"
psql_su() { docker compose exec -T -e PGPASSWORD="$PGPASSWORD" postgres psql -U "$SU" -v ON_ERROR_STOP=1 "$@"; }
trap 'psql_su -d postgres -c "drop database if exists ${scratch}" >/dev/null 2>&1 || true' EXIT
psql_su -d postgres -c "create database ${scratch}" >/dev/null
docker compose exec -T -e PGPASSWORD="$PGPASSWORD" postgres pg_restore -U "$SU" -d "$scratch" --no-owner --exit-on-error < "$dump"
q="select table_schema||'.'||table_name from information_schema.tables where table_schema not in ('pg_catalog','information_schema') and table_type='BASE TABLE' order by 1"
live_db="${APP_DB_NAME}"; [[ "$dump" == *keycloak* ]] && live_db="${KEYCLOAK_DB_NAME}"
mism=0
for t in $(psql_su -d "$live_db" -At -c "$q"); do
  a=$(psql_su -d "$live_db" -At -c "select count(*) from ${t}")
  b=$(psql_su -d "$scratch" -At -c "select count(*) from ${t}")
  [[ "$a" == "$b" ]] || { echo "MISMATCH $t live=$a restored=$b"; mism=1; }
done
[[ $mism == 0 ]] && echo "OK: every table of ${live_db} restored with identical row counts" || exit 1
