#!/bin/bash
# Runs once, on first initialisation of the data volume (docker-entrypoint-initdb.d).
# Creates separate databases + least-privilege login roles for the app and for Keycloak.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE ROLE ${APP_DB_USER} LOGIN PASSWORD '${APP_DB_PASSWORD}';
CREATE DATABASE ${APP_DB_NAME} OWNER ${APP_DB_USER};
CREATE ROLE ${KEYCLOAK_DB_USER} LOGIN PASSWORD '${KEYCLOAK_DB_PASSWORD}';
CREATE DATABASE ${KEYCLOAK_DB_NAME} OWNER ${KEYCLOAK_DB_USER};
REVOKE ALL ON DATABASE ${APP_DB_NAME} FROM PUBLIC;
REVOKE ALL ON DATABASE ${KEYCLOAK_DB_NAME} FROM PUBLIC;
SQL
