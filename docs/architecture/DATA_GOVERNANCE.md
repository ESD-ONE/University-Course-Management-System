# UCMS Data Governance & Auditing Standard

Applies to every table across all 7 services' ER diagrams (`tables-01` through
`tables-07`). This resolves a gap explicitly flagged in the earlier
architecture report (`UCMS_Microservices_Architecture_Report.docx`, Section
9, "Audit logging for administrative registrations") — every mutable table
now carries who changed it and when natively, not just the Enrollment
service's registration log.

## The standard audit block

Every **mutable business table** carries these seven columns, always in this
order, at the end of the table definition:

| Column | Type | Purpose |
|---|---|---|
| `version` | `int` | Optimistic concurrency control (below) |
| `created_at` | `timestamptz` | When the row was first inserted |
| `created_by` | `uuid` | Who inserted it |
| `updated_at` | `timestamptz` | When the row was last modified |
| `updated_by` | `uuid` | Who last modified it |
| `deleted_at` | `timestamptz`, nullable | When the row was soft-deleted — `NULL` means active |
| `deleted_by` | `uuid`, nullable | Who soft-deleted it |

### Why `timestamptz`, not `timestamp`

Every time-of-event column in every diagram uses `timestamptz` (timezone-aware), not the bare `timestamp` type. UCMS has students, lecturers, and admins who are not guaranteed to be in the same timezone as the database server; storing a naive timestamp makes "due at 23:59" ambiguous the moment the deployment spans more than one timezone. This was tightened retroactively across existing business-time fields too (`due_date`, `submitted_at`, `enrollment_open_at`, etc.), not just the new audit columns.

## Optimistic concurrency control (`version`)

Every update statement increments `version` and includes it in the `WHERE` clause:

```sql
UPDATE courses
SET capacity = $1, updated_at = now(), updated_by = $2, version = version + 1
WHERE id = $3 AND version = $4;
```

If zero rows are affected, someone else modified the row first — the
application re-reads the current version and either retries or surfaces a
conflict to the user ("This was changed by someone else — refresh and try
again"), instead of silently overwriting their change. This is the same
pattern already used by every write operation in the functional prototype's
own data layer (`if_version` on writes), applied here at the database level
it was always implicitly assuming.

Two places in this schema call it out explicitly because the business risk
is concrete, not hypothetical:
- `courses.version` — guards two concurrent admin edits to capacity from clobbering each other, which matters directly for the seat-capacity invariant discussed in the microservices architecture report (Section 5.3).
- `role_permissions.version` — guards two admins toggling the same permission checkbox at the same moment.

## Soft delete

Business data is never hard-deleted. A `DELETE` becomes
`UPDATE ... SET deleted_at = now(), deleted_by = $1`, and every default query
filters `WHERE deleted_at IS NULL`. This preserves academic history (a
dropped course, a removed program) for compliance and for the audit trail
itself to stay meaningful — a hard delete would make `created_by` on
dependent rows point at nothing.

## The `created_by` / `updated_by` FK rule

This follows the same database-per-service rule as every other field in
these diagrams (see the main `README.md`):

- **Inside the Identity & Access service** (`roles`, `permissions`, `role_permissions`, and `users` itself), `created_by` / `updated_by` / `deleted_by` are real, enforceable foreign keys to `users.id` — marked `FK` in the diagrams, because both tables live in the same `auth_db`.
- **In every other service**, the same columns hold a `uuid` with no `FK` marker and a `"ref Identity and Access, no FK"` comment — the value is a user ID resolved through the Identity & Access API, not a database-level join, because that user lives in a different database entirely.

`users.created_by` is a deliberate exception within that rule: it
self-references `users.id` and is nullable, because the very first user in
the system (the seed admin account) cannot have been created by another
user row that doesn't exist yet.

## Deliberate exceptions — not every table gets the full block

Applying `version` / `updated_by` / soft-delete to an append-only log or an
ephemeral session is not "more enterprise" — it's modelling a fact table as
if it were a mutable record, which is the wrong pattern. Three exceptions,
each with a reason:

| Table | What it gets instead | Why |
|---|---|---|
| `registration_log` (Enrollment) | `created_at` + `actor_id` only | It's already an immutable audit record by design — its own fields *are* its audit trail. Adding `updated_by` to a log entry would imply log entries can be edited, which they must never be. |
| `dashboard_snapshots` (Analytics) | `computed_at` only | A point-in-time computed fact, regenerated, never edited in place. |
| `user_sessions` (Identity & Access) | `created_at` only | Ephemeral — expires or is hard-deleted on logout. Versioning a session row or asking "who deleted this session" has no business meaning. |
| `notifications` (Notification) | `created_at` + `updated_at` only, no `version`/`created_by`/soft-delete | System-generated, not user-authored, so `created_by` is meaningless; `read_at` changing is the only mutation, with no realistic multi-actor contention to guard against. |

Every other table — all 23 remaining — carries the full standard block.

## What this changes in the diagrams

All 7 `tables-0N-*.mmd` / `.png` files were regenerated with this standard
applied. Re-render with `python3 render_tables.py` after any further schema
edit — and note the render scripts now use a tall fixed viewport
(`2200x6500`) specifically because the denser tables briefly exceeded the
old viewport height and were silently clipped before this fix.
