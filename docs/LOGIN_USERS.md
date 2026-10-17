# UCMS — Login Users

## Access URLs

### Server (169.58.12.239)

| | URL | Notes |
|---|---|---|
| Frontend | `http://169.58.12.239:8091` | `ucms-web` container, exposed directly (plain HTTP, no domain/TLS yet) — no SSH tunnel needed |
| Backend (Gateway) | `http://localhost:8090` (via tunnel) | still loopback-only — see below |
| Jenkins | `http://localhost:8080` (via tunnel) | still loopback-only — multibranch pipeline job at `/job/UCMS/` |

The frontend's container port was moved from `127.0.0.1:8091` to `8091` (all interfaces) and
`ufw allow 8091/tcp` was opened on the host, so it no longer needs a tunnel. The Gateway and
Jenkins are still loopback-only — ports 80/443 are already owned by an unrelated project's
nginx, so nothing else has a public port yet. Reach those two through an SSH tunnel:

```bash
ssh -L 8090:127.0.0.1:8090 -L 8080:127.0.0.1:8080 root@169.58.12.239
```

Jenkins requires login — it already had an admin account configured before this project
touched the box, and **that login is not known/available here**; the `UCMS` job was set up by
writing its `config.xml` directly under `JENKINS_HOME/jobs/` over SSH, not through the web UI.
Ask whoever administers this Jenkins instance for a login, or have them create one for you.

The individual backend services (identity-access, reference-data, academic-structure) are
**not** published with their own ports on the server — only reachable from inside the Docker
network, through the gateway.

Snapshot of `GET /users` against the `ucms` database (`identity_access` schema),
taken 2026-10-17. Source of truth is the database — regenerate this list with:

```bash
TOKEN=$(curl -s -X POST http://localhost:8082/api/identity/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@ucms.local","password":"<current admin password>"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")

curl -s http://localhost:8082/api/identity/users -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

## Current users

| Name | Email | Role | Status |
|---|---|---|---|
| Seed Administrator | `admin@ucms.local` | ADMIN | ACTIVE |

Only the seed admin account exists right now — no other users have been created
in the `ucms` database since the schema-per-service migration.

## Credentials

| Email | Password |
|---|---|
| `admin@ucms.local` | `ChangeMe123!` |

This is the one seeded, documented password
(`services/identity-access-service/src/main/resources/db/migration/V2__seed_data.sql`).
**Rotate it** — it's a known default, not a secret.

Every other user is admin-created via `POST /users` with no password field: the
backend generates a one-time temporary password and returns it exactly once in
the creation response (`temporaryPassword`). It is never stored in plaintext or
retrievable again — if it's lost, reset the account's status/role is not enough;
there is currently no "reset password" endpoint, only account creation.
