#!/usr/bin/env bash
# Verifies the local dev stack end to end (what `make dev-verify` runs).
# Exits non-zero if any check fails. Dev-only: uses the fake values from .env.
set -u
cd "$(dirname "$0")/.." || exit 1
[ -f .env ] || { echo "no .env — run: make dev-env"; exit 2; }
set -a; . ./.env; set +a

PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
check(){ local name="$1"; shift; if "$@" >/dev/null 2>&1; then ok "$name"; else bad "$name"; fi; }
jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print($1)"; }

dc() { docker compose "$@"; }
psql_as() { # user password db sql
  dc exec -T -e PGPASSWORD="$2" postgres psql -h 127.0.0.1 -U "$1" -d "$3" -tAc "$4"
}

echo "== PostgreSQL"
check "app user can query app database"            psql_as "$APP_DB_USER" "$APP_DB_PASSWORD" "$APP_DB_NAME" "select 1"
check "keycloak user can query keycloak database"  psql_as "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD" "$KEYCLOAK_DB_NAME" "select 1"
if psql_as "$APP_DB_USER" "$APP_DB_PASSWORD" "$KEYCLOAK_DB_NAME" "select 1" >/dev/null 2>&1; then bad "isolation: app user must NOT reach keycloak DB"; else ok "isolation: app user cannot reach keycloak DB"; fi
VER=$(psql_as "$APP_DB_USER" "$APP_DB_PASSWORD" "$APP_DB_NAME" "show server_version" 2>/dev/null); echo "        server_version=$VER"

echo "== Redis"
check "authenticated PING"        dc exec -T redis sh -c 'redis-cli -a "$REDIS_PASSWORD" --no-auth-warning ping | grep -q PONG'
if dc exec -T redis redis-cli ping 2>&1 | grep -q PONG; then bad "unauthenticated access must be refused"; else ok "unauthenticated access refused"; fi

echo "== RabbitMQ"
check "management API reachable with credentials" curl -sf -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" http://localhost:15672/api/overview
check "vhost '$RABBITMQ_VHOST' exists"            curl -sf -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" "http://localhost:15672/api/vhosts/$RABBITMQ_VHOST"
if curl -sf -u guest:guest http://localhost:15672/api/overview >/dev/null 2>&1; then bad "default guest account must be disabled"; else ok "default guest/guest rejected"; fi  # gitleaks:allow (negative test: default creds must be rejected)

echo "== Keycloak"
DISC=$(curl -sf http://localhost:8081/realms/engineerhub/.well-known/openid-configuration)
[ -n "$DISC" ] && ok "OIDC discovery served for realm 'engineerhub'" || bad "OIDC discovery"
echo "$DISC" | jget "'S256' in d.get('code_challenge_methods_supported',[])" | grep -q True && ok "PKCE S256 supported" || bad "PKCE S256 supported"
echo "$DISC" | jget "d['issuer']" | grep -qx "$KEYCLOAK_ISSUER_URI" && ok "issuer matches KEYCLOAK_ISSUER_URI" || bad "issuer matches KEYCLOAK_ISSUER_URI"
check "JWKS endpoint serves keys" sh -c "curl -sf http://localhost:8081/realms/engineerhub/protocol/openid-connect/certs | grep -q '\"keys\"'"

TOKEN=$(curl -sf -d "client_id=admin-cli" -d "grant_type=password" -d "username=$KEYCLOAK_BOOTSTRAP_ADMIN_USER" \
  --data-urlencode "password=$KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD" http://localhost:8081/realms/master/protocol/openid-connect/token | jget "d['access_token']" 2>/dev/null)
if [ -n "$TOKEN" ]; then
  ok "admin API token obtained"
  KC() { curl -sf -H "Authorization: Bearer $TOKEN" "http://localhost:8081/admin/realms/engineerhub$1"; }
  ROLES=$(KC /roles | jget "sorted(r['name'] for r in d)")
  echo "$ROLES" | grep -q "SUPER_ADMIN" && echo "$ROLES" | grep -q "INSTRUCTOR" && ok "staff realm roles imported: $ROLES" || bad "staff realm roles imported ($ROLES)"
  KC /roles | jget "[r['name'] for r in d]" | grep -qE "PREMIUM_USER|FREE_USER" && bad "premium/free must NOT be roles (ADR-006)" || ok "no PREMIUM_USER/FREE_USER roles (ADR-006)"
  KC /users?max=50 | jget "len(d)" | grep -qx 7 && ok "7 dev users imported" || bad "7 dev users imported"
  CID=$(KC "/clients?clientId=engineerhub-web" | jget "d[0]['id']")
  KC "/clients/$CID/client-secret" | jget "d['value']" | grep -qx "$KEYCLOAK_BFF_CLIENT_SECRET" && ok "client secret placeholder substituted from env" || bad "client secret substitution"
  KC "/clients/$CID" | jget "(d['standardFlowEnabled'], d['directAccessGrantsEnabled'], d['implicitFlowEnabled'], d['attributes'].get('pkce.code.challenge.method'))" | grep -qx "(True, False, False, 'S256')" && ok "client: code flow + PKCE S256, no password/implicit grants" || bad "client flow settings"
  KC "" | jget "(d['bruteForceProtected'], d['accessTokenLifespan'], d['revokeRefreshToken'])" | grep -qx "(True, 300, True)" && ok "realm: brute-force on, 5-min access tokens, refresh rotation" || bad "realm security settings"
  # Real email path: Keycloak -> SMTP -> Mailpit
  UID_LEARNER=$(KC "/users?username=learner&exact=true" | jget "d[0]['id']")
  BEFORE=$(curl -sf http://localhost:8025/api/v1/messages | jget "d['total']")
  curl -sf -X PUT -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '["UPDATE_PASSWORD"]' \
    "http://localhost:8081/admin/realms/engineerhub/users/$UID_LEARNER/execute-actions-email" >/dev/null 2>&1
  AFTER=$BEFORE
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    AFTER=$(curl -sf http://localhost:8025/api/v1/messages | jget "d['total']"); [ "$AFTER" -gt "$BEFORE" ] && break; sleep 1
  done
  [ "$AFTER" -gt "$BEFORE" ] && ok "Keycloak delivered an email through Mailpit" || bad "Keycloak email delivery via Mailpit"
else
  bad "admin API token obtained"
fi

echo "== Mailpit"
check "web/API up"  curl -sf http://localhost:8025/api/v1/info
check "SMTP port 1025 open" bash -c "exec 3<>/dev/tcp/127.0.0.1/1025"

echo "== Object storage (S3-compatible)"
S3LOG=$(dc logs s3-init 2>&1)
echo "$S3LOG" | grep -q "$S3_BUCKET_MEDIA"    && ok "bucket $S3_BUCKET_MEDIA exists"    || bad "bucket $S3_BUCKET_MEDIA"
echo "$S3LOG" | grep -q "$S3_BUCKET_TESTDATA" && ok "bucket $S3_BUCKET_TESTDATA exists" || bad "bucket $S3_BUCKET_TESTDATA"

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
