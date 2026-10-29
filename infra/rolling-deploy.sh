#!/usr/bin/env bash
# Zero-downtime redeploy of the application stack (docker-compose.deploy.yml).
#
# For each service in dependency order: build, start the new replicas next to the old
# ones, wait until every new one is healthy, give the load balancers a moment to see
# them, then stop the old ones (each finishes its in-flight requests first — graceful
# shutdown, stop_grace_period 30s). A service with nothing running yet is simply started.
# The web container is a single instance and is recreated last (a ~1 second blip).
#
# Usage, on the server:  cd /opt/ucms-app/infra && ./rolling-deploy.sh [service ...]
# With service names, only those are rebuilt and rolled (web only if named).
#   ./rolling-deploy.sh --check-networks [service ...]
# only checks (and repairs) the running containers' network attachments — run it after
# any plain `docker compose up`.
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE=(docker compose --env-file .env -f docker-compose.deploy.yml)
SERVICES=(identity-access-service base-service academic-structure-service enrollment-service
          assignment-grading-service notification-service analytics-reporting-service gateway)
CHECK_ONLY=0
if [ "${1:-}" = --check-networks ]; then
  CHECK_ONLY=1
  shift
fi
WITH_WEB=1
if [ $# -gt 0 ]; then
  WITH_WEB=0
  SERVICES=()
  for name in "$@"; do
    if [ "$name" = web ]; then WITH_WEB=1; else SERVICES+=("$name"); fi
  done
fi
DISCOVERY_SETTLE_SECONDS=${DISCOVERY_SETTLE_SECONDS:-20}

replicas_of() {
  "${COMPOSE[@]}" config --format json | python3 -c '
import json, sys
service = json.load(sys.stdin)["services"][sys.argv[1]]
print((service.get("deploy") or {}).get("replicas", 1))' "$1"
}

networks_of() {
  "${COMPOSE[@]}" config --format json | python3 -c '
import json, sys
config = json.load(sys.stdin)
for key in (config["services"][sys.argv[1]].get("networks") or {}):
    print(config["networks"][key]["name"])' "$1"
}

# Some Docker Compose releases (seen with v2.19) occasionally attach a container to only
# some of its networks. A gateway missing ucms_app still passes its health check but can
# reach neither Redis nor the services — so check, and connect whatever is missing.
ensure_networks() {
  local svc=$1; shift
  local wanted id have net
  wanted=$(networks_of "$svc")
  for id in "$@"; do
    have=$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}' "$id")
    for net in $wanted; do
      if [[ " $have " != *" $net "* ]]; then
        echo "!! $svc ${id:0:12} was not on $net — connecting it"
        docker network connect --alias "$svc" "$net" "$id"
      fi
    done
  done
}

running_ids() {
  "${COMPOSE[@]}" ps -q "$1" | sort
}

wait_healthy() {
  for id in "$@"; do
    for attempt in $(seq 1 72); do
      status=$(docker inspect -f '{{.State.Health.Status}}' "$id" 2>/dev/null || echo missing)
      [ "$status" = healthy ] && break
      if [ "$attempt" = 72 ] || [ "$status" = unhealthy ] || [ "$status" = missing ]; then
        echo "!! $id did not become healthy ($status) — old replicas left running" >&2
        docker logs --tail 40 "$id" >&2 || true
        exit 1
      fi
      sleep 5
    done
  done
}

if [ "$CHECK_ONLY" = 1 ]; then
  targets=(${SERVICES[@]+"${SERVICES[@]}"})
  if [ "$WITH_WEB" = 1 ]; then targets+=(web); fi
  for svc in ${targets[@]+"${targets[@]}"}; do
    # shellcheck disable=SC2046
    ensure_networks "$svc" $("${COMPOSE[@]}" ps -q "$svc")
  done
  echo "== networks checked"
  exit 0
fi

echo "== building images"
if [ ${#SERVICES[@]} -gt 0 ]; then "${COMPOSE[@]}" build "${SERVICES[@]}"; fi
if [ "$WITH_WEB" = 1 ]; then "${COMPOSE[@]}" build web; fi

for svc in ${SERVICES[@]+"${SERVICES[@]}"}; do
  want=$(replicas_of "$svc")
  old=$(running_ids "$svc")
  if [ -z "$old" ]; then
    echo "== $svc: starting $want replica(s)"
    "${COMPOSE[@]}" up -d --no-deps --scale "$svc=$want" "$svc"
    # shellcheck disable=SC2046
    ensure_networks "$svc" $(running_ids "$svc")
    # shellcheck disable=SC2046
    wait_healthy $(running_ids "$svc")
    continue
  fi
  n_old=$(echo "$old" | wc -l | tr -d ' ')
  echo "== $svc: starting $want new replica(s) next to $n_old old"
  "${COMPOSE[@]}" up -d --no-deps --no-recreate --scale "$svc=$((n_old + want))" "$svc"
  new=$(comm -13 <(echo "$old") <(running_ids "$svc"))
  # shellcheck disable=SC2086
  ensure_networks "$svc" $new
  # shellcheck disable=SC2086
  wait_healthy $new
  sleep "$DISCOVERY_SETTLE_SECONDS"
  echo "== $svc: retiring old replica(s)"
  # shellcheck disable=SC2086
  docker stop -t 30 $old >/dev/null
  # shellcheck disable=SC2086
  docker rm $old >/dev/null
done

if [ "$WITH_WEB" = 1 ]; then
  echo "== web"
  "${COMPOSE[@]}" up -d --no-deps web
  ensure_networks web "$("${COMPOSE[@]}" ps -q web)"
fi
echo "== done"
"${COMPOSE[@]}" ps
