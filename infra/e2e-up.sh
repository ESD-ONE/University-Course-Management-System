#!/usr/bin/env bash
# Builds and starts the end-to-end test stack (docker-compose.e2e.yml) and waits until
# every container is healthy. The app is then at https://localhost:8091 (with a
# certificate made for this stack, which a browser will warn about).
#
#   ./e2e-up.sh            build what is missing, then start
#   ./e2e-up.sh --rebuild  rebuild the service jars and the frontend first
#
#   E2E_WEB_PORT=8191 ./e2e-up.sh   use another port when 8091 is taken
#
# Needs Docker, openssl, JDK 17+ and Node 20+ (or Docker alone for the frontend build).
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

mkdir -p .e2e

# A certificate for https://localhost, for this stack only. Throwaway: it is not trusted
# by anything, and the tests are told to accept it.
if [ ! -f .e2e/cert.pem ] || [ ! -f .e2e/key.pem ]; then
    echo "==> Making a certificate for the test stack"
    openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
        -subj "/CN=localhost" -addext "subjectAltName=DNS:localhost,IP:127.0.0.1" \
        -keyout .e2e/key.pem -out .e2e/cert.pem 2>/dev/null
    # The web container's nginx starts without the right to read other users' files.
    chmod 644 .e2e/key.pem
fi

# The web container starts with the real nginx.conf, with two changes (see the "web"
# service in docker-compose.e2e.yml for why): it also serves HTTPS on 443, and its
# per-IP limits are raised.
sed -E -e 's#^( *)listen 80;#\1listen 80; listen 443 ssl; ssl_certificate /etc/nginx/e2e/cert.pem; ssl_certificate_key /etc/nginx/e2e/key.pem;#' \
       -e 's#(limit_req_zone .* rate=)[0-9]+r/[sm];#\11000r/s;#' \
       -e 's#(limit_conn sse_conn )[0-9]+;#\11000;#' \
       "$ROOT/web/nginx.conf" > .e2e/nginx.conf

echo "==> Starting the stack (first start takes a few minutes)"
"${COMPOSE[@]}" up -d --build --wait --wait-timeout 900

# The realm only lets the web app sign in from the addresses it lists, and this stack's
# HTTPS address is not one of them. Add it to this stack's own Keycloak (never to the realm
# file, which production imports too).
echo "==> Allowing sign-in from https://localhost:$E2E_WEB_PORT"
"${COMPOSE[@]}" exec -T -e HOME=/tmp ucms-keycloak bash -c '
    set -e
    kc=/opt/keycloak/bin/kcadm.sh
    $kc config credentials --server http://localhost:8080/auth --realm master \
        --user admin --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
    id=$($kc get clients -r ucms -q clientId=ucms-web --fields id --format csv --noquotes)
    $kc update "clients/$id" -r ucms -s "redirectUris=[\"'"https://localhost:$E2E_WEB_PORT"'/*\"]" \
        -s "attributes.\"post.logout.redirect.uris\"='"https://localhost:$E2E_WEB_PORT"'/*"
'

echo "==> Ready: https://localhost:$E2E_WEB_PORT"
