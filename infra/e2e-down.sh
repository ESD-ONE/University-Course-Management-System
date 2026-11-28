#!/usr/bin/env bash
# Stops the end-to-end test stack and deletes its data, so the next start is empty.
set -euo pipefail

cd "$(dirname "$0")"
export E2E_WEB_PORT="${E2E_WEB_PORT:-$(grep '^E2E_WEB_PORT=' e2e.env | cut -d= -f2)}"
docker compose --env-file e2e.env -f docker-compose.e2e.yml down --volumes --remove-orphans
