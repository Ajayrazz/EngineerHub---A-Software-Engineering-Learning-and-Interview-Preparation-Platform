#!/usr/bin/env bash
# Live end-to-end check of the API's authentication & authorization against the REAL dev Keycloak
# (real RS256 signatures, issuer, audience, role mapping). Prereqs: `make dev-up` and the API running
# on :8080 with the local profile (`make backend-run`). Dev-only: uses the fake users from the realm.
#   API_URL=http://localhost:8080 API_LOG=/path/to/api.log ./scripts/verify-api-auth.sh
set -u
cd "$(dirname "$0")/.." || exit 1
[ -f .env ] && { set -a; . ./.env; set +a; }
API="${API_URL:-http://localhost:8080}"
KC="${KEYCLOAK_ISSUER_URI:-http://localhost:8081/realms/engineerhub}"
PW='Dev-Only-Pass-1!'

PASS=0; FAIL=0
ok()  { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad() { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
jget(){ python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }

token() { # client user  -> access token (empty on failure)
  curl -s -d "client_id=$1" -d grant_type=password -d "username=$2" --data-urlencode "password=$PW" \
    "$KC/protocol/openid-connect/token" | jget "d.get('access_token','')"
}
call() { # token path -> "status|body"   (empty token = no Authorization header)
  local t="$1" p="$2"
  if [ -n "$t" ]; then
    curl -s -w '|%{http_code}' -H "Authorization: Bearer $t" "$API$p"
  else
    curl -s -w '|%{http_code}' "$API$p"
  fi
}
status() { call "$1" "$2" | sed 's/.*|//'; }
body()   { call "$1" "$2" | sed 's/|[0-9]*$//'; }
expect() { # name expected_status token path
  local got; got=$(status "$3" "$4")
  [ "$got" = "$2" ] && ok "$1 -> $2" || bad "$1 (expected $2, got $got)"
}

curl -sf "$API/actuator/health" >/dev/null || { echo "API not reachable at $API (start it: make backend-run)"; exit 2; }

echo "== Obtain real tokens from Keycloak"
LEARNER=$(token engineerhub-dev-cli learner);       [ -n "$LEARNER" ]  && ok "learner token"      || bad "learner token"
ADMIN=$(token engineerhub-dev-cli admin);           [ -n "$ADMIN" ]    && ok "admin token"        || bad "admin token"
SUPER=$(token engineerhub-dev-cli superadmin);      [ -n "$SUPER" ]    && ok "superadmin token"   || bad "superadmin token"
MODERATOR=$(token engineerhub-dev-cli moderator);   [ -n "$MODERATOR" ] && ok "moderator token"   || bad "moderator token"
NOAUD=$(token engineerhub-dev-noaud admin);         [ -n "$NOAUD" ]    && ok "token WITHOUT api audience (negative control)" || bad "noaud token"
MASTER=$(curl -s -d client_id=admin-cli -d grant_type=password -d "username=$KEYCLOAK_BOOTSTRAP_ADMIN_USER" \
  --data-urlencode "password=$KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD" "${KC%/realms/*}/realms/master/protocol/openid-connect/token" | jget "d.get('access_token','')")
[ -n "$MASTER" ] && ok "token from a DIFFERENT realm (master) (negative control)" || bad "master token"

echo "== Public endpoints"
expect "GET /system/ping without token" 200 "" /api/v1/system/ping
expect "GET /actuator/health without token" 200 "" /actuator/health

echo "== Authentication"
expect "whoami without token" 401 "" /api/v1/system/whoami
expect "whoami with garbage token" 401 "not.a.jwt" /api/v1/system/whoami
# Deterministic tampering (flipping only the LAST base64 char can alter just padding bits and leave the
# signature bytes unchanged, which would legitimately still verify).
tamper() { # mode token
  python3 - "$1" "$2" <<'PY'
import sys, json, base64
mode, tok = sys.argv[1], sys.argv[2]
h, p, sg = tok.split('.')
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=').decode()
unb64 = lambda s: base64.urlsafe_b64decode(s + '=' * (-len(s) % 4))
if mode == 'signature':          # alter real signature bytes (middle of the signature)
    i = 20
    sg = sg[:i] + ('A' if sg[i] != 'A' else 'B') + sg[i+1:]
elif mode == 'escalate':         # keep the genuine signature, claim SUPER_ADMIN in the payload
    d = json.loads(unb64(p))
    d['realm_access'] = {'roles': ['SUPER_ADMIN']}
    p = b64(json.dumps(d).encode())
print('.'.join([h, p, sg]))
PY
}
expect "whoami with tampered signature" 401 "$(tamper signature "$ADMIN")" /api/v1/system/whoami
expect "admin/ping with LEARNER token whose payload was edited to claim SUPER_ADMIN" 401 "$(tamper escalate "$LEARNER")" /api/v1/system/admin/ping
expect "whoami with valid token but WRONG AUDIENCE" 401 "$NOAUD" /api/v1/system/whoami
expect "whoami with token from a different realm/issuer" 401 "$MASTER" /api/v1/system/whoami
expect "whoami with learner token" 200 "$LEARNER" /api/v1/system/whoami
HDR=$(curl -s -D - -o /dev/null "$API/api/v1/system/whoami")
echo "$HDR" | grep -qi '^www-authenticate: Bearer' && ok "401 carries WWW-Authenticate: Bearer" || bad "WWW-Authenticate header"
echo "$HDR" | grep -qi '^x-correlation-id:' && ok "responses carry X-Correlation-Id" || bad "X-Correlation-Id header"
curl -s "$API/api/v1/system/whoami" | jget "d['code']=='UNAUTHENTICATED' and d['type'].startswith('urn:engineerhub:problem:')" | grep -qx True \
  && ok "401 body is problem+json with stable code" || bad "401 problem body"

echo "== Role mapping from real tokens"
body "$LEARNER" /api/v1/system/whoami | jget "d['roles']==[]" | grep -qx True && ok "learner has no staff roles" || bad "learner roles"
body "$ADMIN" /api/v1/system/whoami | jget "d['roles']==['ADMIN']" | grep -qx True && ok "admin -> ['ADMIN'] (Keycloak built-in roles filtered out)" || bad "admin roles: $(body "$ADMIN" /api/v1/system/whoami)"
body "$SUPER" /api/v1/system/whoami | jget "d['roles']==['SUPER_ADMIN']" | grep -qx True && ok "superadmin -> ['SUPER_ADMIN']" || bad "superadmin roles"

echo "== Authorization (admin endpoint)"
expect "admin/ping: no token" 401 "" /api/v1/system/admin/ping
expect "admin/ping: learner (no staff role)" 403 "$LEARNER" /api/v1/system/admin/ping
expect "admin/ping: moderator" 403 "$MODERATOR" /api/v1/system/admin/ping
expect "admin/ping: admin" 200 "$ADMIN" /api/v1/system/admin/ping
expect "admin/ping: superadmin" 200 "$SUPER" /api/v1/system/admin/ping
body "$LEARNER" /api/v1/system/admin/ping | jget "d['code']" | grep -qx FORBIDDEN && ok "403 body is problem+json code FORBIDDEN" || bad "403 problem body"

echo "== Identity (/me): just-in-time provisioning, validation, anonymous writes"
ME=$(body "$LEARNER" /api/v1/me)
echo "$ME" | jget "d['username']=='learner' and d['status']=='active' and d['roles']==[]" | grep -qx True && ok "/me provisions + returns the learner (no staff roles)" || bad "/me learner: $ME"
echo "$ME" | jget "__import__('uuid').UUID(d['id']).version==7" | grep -qx True && ok "user id is a UUIDv7" || bad "user id version"
ME2=$(body "$LEARNER" /api/v1/me); [ "$(echo "$ME" | jget "d['id']")" = "$(echo "$ME2" | jget "d['id']")" ] && ok "repeat call returns the same user (idempotent)" || bad "/me not idempotent"
body "$ADMIN" /api/v1/me | jget "d['roles']==['ADMIN']" | grep -qx True && ok "/me shows the admin role from the token" || bad "/me admin roles"
expect "/me without token" 401 "" /api/v1/me
for m in POST PUT PATCH DELETE; do
  c=$(curl -s -o /dev/null -w '%{http_code}' -X "$m" -H 'content-type: application/json' -d '{bad json' "$API/api/v1/me/profile"); [ "$c" = 401 ] && ok "anonymous $m with a broken body -> 401 (not 400/404/405)" || bad "anonymous $m -> $c"
done
c=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $LEARNER" -H 'content-type: application/json' -d '{"website":"javascript:alert(1)"}' "$API/api/v1/me/profile"); [ "$c" = 400 ] && ok "javascript: link in the profile -> 400" || bad "javascript: link -> $c"
c=$(curl -s -o /dev/null -w '%{http_code}' -X PATCH -H "Authorization: Bearer $LEARNER" -H 'content-type: application/json' -d '{"displayName":"Dev Learner","status":"suspended","email":"evil@x.test"}' "$API/api/v1/me/profile"); [ "$c" = 200 ] && ok "valid PATCH accepted; extra fields ignored" || bad "valid PATCH -> $c"
body "$LEARNER" /api/v1/me | jget "d['status']=='active' and d['email']!='evil@x.test'" | grep -qx True && ok "mass assignment (status/email) had no effect" || bad "mass assignment!"

echo "== Staff role assignment through the REAL Keycloak admin API"
LEARNER_ID=$(body "$LEARNER" /api/v1/me | jget "d['id']")
ADMIN_ID=$(body "$ADMIN" /api/v1/me | jget "d['id']")
SUPER_ID=$(body "$SUPER" /api/v1/me | jget "d['id']")
put_roles() { # token user-id json-roles -> status
  curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $1" -H 'content-type: application/json' \
    -d "{\"roles\":$3,\"reason\":\"live verification\"}" "$API/api/v1/admin/users/$2/roles"
}
has_role() { # user role -> True/False from a freshly issued token's claims
  token engineerhub-dev-cli "$1" | python3 -c "
import sys,json,base64
t=sys.stdin.read().strip(); c=json.loads(base64.urlsafe_b64decode(t.split('.')[1]+'=='))
print('$2' in c.get('realm_access',{}).get('roles',[]))" 2>/dev/null
}
c=$(put_roles "$LEARNER" "$ADMIN_ID" '["MODERATOR"]'); [ "$c" = 403 ] && ok "a learner cannot assign roles" || bad "learner assigning roles -> $c"
c=$(put_roles "$ADMIN" "$LEARNER_ID" '["ADMIN"]'); [ "$c" = 403 ] && ok "an admin cannot grant ADMIN" || bad "admin granting ADMIN -> $c"
c=$(put_roles "$ADMIN" "$LEARNER_ID" '["SUPER_ADMIN"]'); [ "$c" = 403 ] && ok "an admin cannot grant SUPER_ADMIN" || bad "admin granting SUPER_ADMIN -> $c"
c=$(put_roles "$SUPER" "$SUPER_ID" '[]'); [ "$c" = 403 ] && ok "nobody can change their own roles" || bad "self role change -> $c"
c=$(put_roles "$SUPER" "$LEARNER_ID" '["PROBLEM_SETTER"]'); [ "$c" = 200 ] && ok "super admin assigns PROBLEM_SETTER (Keycloak updated)" || bad "assign -> $c"
[ "$(has_role learner PROBLEM_SETTER)" = True ] && ok "the learner's NEW token carries PROBLEM_SETTER" || bad "role missing from the new token"
c=$(put_roles "$ADMIN" "$LEARNER_ID" '["PROBLEM_SETTER","MODERATOR"]'); [ "$c" = 200 ] && ok "an admin can add a non-administrator role" || bad "admin adding MODERATOR -> $c"
c=$(curl -s -o /dev/null -w '%{http_code}' -X PUT -H "Authorization: Bearer $ADMIN" -H 'content-type: application/json' -d '{"status":"suspended","reason":"admin acting on an admin"}' "$API/api/v1/admin/users/$SUPER_ID/status"); [ "$c" = 403 ] && ok "an admin cannot suspend a super admin" || bad "admin suspending super admin -> $c"
c=$(put_roles "$SUPER" "$LEARNER_ID" '[]'); [ "$c" = 200 ] && ok "roles removed again" || bad "remove -> $c"
[ "$(has_role learner PROBLEM_SETTER)" = False ] && [ "$(has_role learner MODERATOR)" = False ] && ok "the learner's next token no longer carries them" || bad "roles still in token after removal"
c=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $SUPER" "$API/api/v1/admin/audit-logs?action=user.roles.changed"); [ "$c" = 200 ] && ok "role changes are visible in the audit log" || bad "audit query -> $c"

echo "== Hardening"
curl -s -D - -o /dev/null "$API/api/v1/system/ping" | tr -d '\r' > /tmp/eh_hdr.$$ 2>/dev/null
grep -qi '^x-content-type-options: nosniff' /tmp/eh_hdr.$$ && ok "X-Content-Type-Options: nosniff" || bad "nosniff"
grep -qi '^x-frame-options: DENY' /tmp/eh_hdr.$$ && ok "X-Frame-Options: DENY" || bad "frame options"
grep -qi '^referrer-policy: no-referrer' /tmp/eh_hdr.$$ && ok "Referrer-Policy: no-referrer" || bad "referrer policy"
rm -f /tmp/eh_hdr.$$
curl -s -D - -o /dev/null -H "Origin: https://evil.example" "$API/api/v1/system/ping" | grep -qi '^access-control-allow-origin' \
  && bad "CORS header leaked to foreign origin" || ok "no CORS headers for a foreign origin"
[ "$(status "" /actuator/env)" = 404 ] && ok "/actuator/env not exposed" || bad "/actuator/env exposed"

echo "== Logs must not contain credentials"
if [ -n "${API_LOG:-}" ] && [ -f "$API_LOG" ]; then
  if grep -qF -e "$ADMIN" -e "$LEARNER" -e "$SUPER" "$API_LOG" || grep -qiF "$PW" "$API_LOG"; then
    bad "a token or password appears in the API log"
  else
    ok "no bearer tokens or passwords found in the API log"
  fi
else
  echo "  SKIP  (set API_LOG to the API's log file to check this)"
fi

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
