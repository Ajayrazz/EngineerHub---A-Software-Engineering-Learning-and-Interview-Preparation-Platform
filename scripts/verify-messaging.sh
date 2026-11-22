#!/usr/bin/env bash
# Live check of the transactional outbox against the REAL dev stack: the API's relay, PostgreSQL and the
# RabbitMQ container from docker-compose. Includes a broker outage in the middle of publishing.
# Prereqs: `make dev-up` and the API running on :8080 with the local profile (`make backend-run`).
# NOTE: this stops and restarts the RabbitMQ container. Dev-only.
set -u
cd "$(dirname "$0")/.." || exit 1
[ -f .env ] || { echo "no .env — run: make dev-env"; exit 2; }
set -a; . ./.env; set +a
API="${API_URL:-http://localhost:8080}"
MQ="http://localhost:15672/api"
VH="$RABBITMQ_VHOST"
SINK="eh.verify.sink"

PASS=0; FAIL=0
ok()  { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad() { echo "  FAIL  $1"; FAIL=$((FAIL+1)); }
jget(){ python3 -c "import sys,json;d=json.load(sys.stdin);print($1)" 2>/dev/null; }
mq()  { curl -s -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" -H 'content-type: application/json' "$@"; }
sql() { docker compose exec -T -e PGPASSWORD="$APP_DB_PASSWORD" postgres psql -h 127.0.0.1 -U "$APP_DB_USER" -d "$APP_DB_NAME" -tAc "$1"; }
pending() { sql "select count(*) from shared.outbox where published_at is null and event_type like 'verify.%'"; }
wait_until() { # description, command that must print the expected value, expected, timeout_s
  local end=$((SECONDS+$4)); while [ $SECONDS -lt $end ]; do [ "$(eval "$2")" = "$3" ] && return 0; sleep 1; done; return 1; }
# The management API's queue statistics lag by seconds, so count by actually fetching (and re-queueing).
sink_count() { mq -X POST "$MQ/queues/$VH/$SINK/get" -d '{"count":200,"ackmode":"ack_requeue_true","encoding":"auto"}' | jget "len(d)"; }
insert_events() { # n  — simulates n business transactions that each committed one event
  sql "insert into shared.outbox (id,event_type,event_version,aggregate_type,aggregate_id,payload,correlation_id,occurred_at,created_at)
       select gen_random_uuid(),'verify.ping',1,'verify','agg-'||g,jsonb_build_object('n',g,'secret','none'),'live-check-corr-01',now(),now()
       from generate_series(1,$1) g" >/dev/null
}

curl -sf "$API/actuator/health" >/dev/null || { echo "API not reachable at $API (start it: make backend-run)"; exit 2; }
curl -sf -u "$RABBITMQ_USER:$RABBITMQ_PASSWORD" "$MQ/overview" >/dev/null || { echo "RabbitMQ management API not reachable"; exit 2; }

echo "== Setup: a durable sink queue bound to the events exchange"
mq -X DELETE "$MQ/queues/$VH/$SINK" >/dev/null
sql "delete from shared.outbox where event_type like 'verify.%'" >/dev/null
code=$(mq -o /dev/null -w '%{http_code}' -X PUT "$MQ/queues/$VH/$SINK" -d '{"durable":true,"auto_delete":false,"arguments":{"x-queue-type":"quorum"}}')
[ "$code" = 201 ] || [ "$code" = 204 ] && ok "sink queue declared (quorum, durable)" || bad "declare sink queue ($code)"
code=$(mq -o /dev/null -w '%{http_code}' -X POST "$MQ/bindings/$VH/e/eh.domain.events/q/$SINK" -d '{"routing_key":"verify.#"}')
[ "$code" = 201 ] && ok "bound to eh.domain.events with verify.#" || bad "bind sink ($code)"

echo "== Topology created by the application at start-up"
mq "$MQ/exchanges/$VH/eh.domain.events" | jget "d['type']=='topic' and d['durable']" | grep -qx True && ok "exchange eh.domain.events is a durable topic exchange" || bad "events exchange"
mq "$MQ/exchanges/$VH/eh.dlx" | jget "d['type']=='direct' and d['durable']" | grep -qx True && ok "dead-letter exchange eh.dlx is durable" || bad "dlx"

echo "== Happy path: committed events are relayed to RabbitMQ"
insert_events 5
wait_until "pending" 'pending' 0 20 && ok "outbox drained (5 events published and marked)" || bad "outbox did not drain (pending=$(pending))"
sleep 1
COUNT=$(sink_count)
[ "$COUNT" = 5 ] && ok "5 messages arrived in the sink queue" || bad "sink queue has $COUNT messages (expected 5)"
MSG=$(mq -X POST "$MQ/queues/$VH/$SINK/get" -d '{"count":1,"ackmode":"ack_requeue_true","encoding":"auto"}')
echo "$MSG" | jget "d[0]['properties']['delivery_mode']==2" | grep -qx True && ok "messages are persistent (delivery_mode 2)" || bad "delivery mode"
echo "$MSG" | jget "d[0]['properties']['content_type']=='application/json'" | grep -qx True && ok "content type is application/json" || bad "content type"
echo "$MSG" | jget "d[0]['properties']['headers']['X-Correlation-Id']=='live-check-corr-01'" | grep -qx True && ok "correlation id carried into the message headers" || bad "correlation header"
echo "$MSG" | jget "(lambda e: e['type']=='verify.ping' and e['version']==1 and e['payload']['secret']=='none' and e['eventId']==d[0]['properties']['message_id'])(__import__('json').loads(d[0]['payload']))" | grep -qx True && ok "envelope is well-formed; eventId == AMQP message_id" || bad "envelope"

echo "== Broker outage in the middle of publishing"
mq -X DELETE "$MQ/queues/$VH/$SINK/contents" >/dev/null
docker compose stop rabbitmq >/dev/null 2>&1 && ok "RabbitMQ container stopped" || bad "stop rabbitmq"
insert_events 6
sleep 4
P=$(pending)
[ "$P" = 6 ] && ok "all 6 events stay safely pending in PostgreSQL while the broker is down" || bad "expected 6 pending, got $P"
[ "$(sql "select count(*) from shared.outbox where event_type like 'verify.%' and attempts > 0 and last_error is not null")" -ge 1 ] && ok "failure recorded on the outbox row (attempts/last_error)" || bad "failure not recorded"
sql "select last_error from shared.outbox where event_type like 'verify.%' and last_error is not null limit 1" | grep -qiE "hunter|password|$RABBITMQ_PASSWORD" && bad "credentials leaked into last_error" || ok "last_error contains no credentials"
curl -s -o /dev/null -w '%{http_code}' "$API/api/v1/system/ping" | grep -qx 200 && ok "API keeps serving requests during the broker outage" || bad "API unavailable during outage"
docker compose start rabbitmq >/dev/null 2>&1 && ok "RabbitMQ container started again" || bad "start rabbitmq"
wait_until "pending" 'pending' 0 120 && ok "relay reconnected on its own and drained the outbox (nothing lost)" || bad "outbox not drained after recovery (pending=$(pending))"
sleep 2
COUNT=$(sink_count)
[ "$COUNT" = 6 ] && ok "exactly 6 messages delivered after recovery (no loss, no duplicates)" || bad "sink queue has $COUNT messages (expected 6)"
DISTINCT=$(mq -X POST "$MQ/queues/$VH/$SINK/get" -d '{"count":50,"ackmode":"ack_requeue_true","encoding":"auto"}' | jget "len({m['properties']['message_id'] for m in d})")
[ "$DISTINCT" = 6 ] && ok "all 6 message ids are distinct" || bad "distinct ids: $DISTINCT"

echo "== Cleanup"
mq -X DELETE "$MQ/queues/$VH/$SINK" >/dev/null
sql "delete from shared.outbox where event_type like 'verify.%'" >/dev/null
ok "sink queue and test rows removed"

echo
echo "Result: $PASS passed, $FAIL failed"
[ "$FAIL" -eq 0 ]
