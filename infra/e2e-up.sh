#!/usr/bin/env bash
# Builds and starts the end-to-end test stack (docker-compose.e2e.yml) and waits until
# every container is healthy. The app is then at http://localhost:8091.
#
#   ./e2e-up.sh            build what is missing, then start
#   ./e2e-up.sh --rebuild  rebuild the service jars and the frontend first
#
#   E2E_WEB_PORT=8191 ./e2e-up.sh   use another port when 8091 is taken
#
# Needs Docker, JDK 17+ and Node 20+ (or Docker alone for the frontend build).
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(cd .. && pwd)"
REBUILD="${1:-}"
COMPOSE=(docker compose --env-file e2e.env -f docker-compose.e2e.yml)
export E2E_WEB_PORT="${E2E_WEB_PORT:-$(grep '^E2E_WEB_PORT=' e2e.env | cut -d= -f2)}"

missing_jar=false
for svc in identity-access-service base-service academic-structure-service enrollment-service \
           assignment-grading-service notification-service analytics-reporting-service gateway; do
    ls "$ROOT/services/$svc/target/$svc-"*.jar >/dev/null 2>&1 || missing_jar=true
done
if [ "$REBUILD" = "--rebuild" ] || [ "$missing_jar" = true ]; then
    echo "==> Building the service jars"
    (cd "$ROOT" && ./mvnw -q -B package -DskipTests)
fi

if [ "$REBUILD" = "--rebuild" ] || [ ! -f "$ROOT/web/dist/web/browser/index.html" ]; then
    echo "==> Building the frontend"
    if command -v npm >/dev/null 2>&1; then
        (cd "$ROOT/web" && npm ci && npm run build -- --configuration production)
    else
        docker run --rm -v "$ROOT/web:/app" -w /app node:20-alpine \
            sh -c "npm ci && npm run build -- --configuration production"
    fi
fi

# All test browsers reach the web container from one address, so its per-IP limits
# (sign-in, token exchange, API, live streams) would treat a test run as one abusive
# client. Start it with the real nginx.conf, with only those limits raised.
mkdir -p .e2e
sed -E -e 's#(limit_req_zone .* rate=)[0-9]+r/[sm];#\11000r/s;#' \
       -e 's#(limit_conn sse_conn )[0-9]+;#\11000;#' \
       "$ROOT/web/nginx.conf" > .e2e/nginx.conf

echo "==> Starting the stack (first start takes a few minutes)"
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 900

# The realm only lets the web app sign in from the addresses it lists, and 8091 is the
# only local one that fits this stack. On any other port, add this stack's address to
# its own Keycloak (never to the realm file, which production imports too).
if [ "$E2E_WEB_PORT" != "8091" ]; then
    echo "==> Allowing sign-in from port $E2E_WEB_PORT"
    "${COMPOSE[@]}" exec -T -e HOME=/tmp ucms-keycloak bash -c '
        set -e
        kc=/opt/keycloak/bin/kcadm.sh
        $kc config credentials --server http://localhost:8080/auth --realm master \
            --user admin --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
        id=$($kc get clients -r ucms -q clientId=ucms-web --fields id --format csv --noquotes)
        $kc update "clients/$id" -r ucms -s "redirectUris=[\"'"http://localhost:$E2E_WEB_PORT"'/*\"]" \
            -s "attributes.\"post.logout.redirect.uris\"='"http://localhost:$E2E_WEB_PORT"'/*"
    '
fi

echo "==> Ready: http://localhost:$E2E_WEB_PORT"
