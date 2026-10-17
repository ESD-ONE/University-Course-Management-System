# UCMS — Login Users

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
