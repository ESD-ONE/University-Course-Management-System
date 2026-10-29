# UCMS Architecture Diagrams

Three layers, kept deliberately separate rather than one crowded diagram —
cramming 7 services × their tables × every connection into a single picture
was tried first and produced an unreadable tangle (Mermaid's auto-layout
degrades fast past ~10-12 nodes with cross-links). Splitting by layer is
the more reliable way to make each piece identifiable at a glance:

- **Static structure** — what services exist and what they're built of (sections 1–2)
- **Dynamic behaviour** — how a request actually moves through those services, step by step (section 3)

## 1. System-level (how services connect)

| File | Shows |
|---|---|
| `high-level-architecture.mmd` / `.png` | Client → Gateway → services layer → data layer → async backbone → delivery pipeline (container view) |
| `microservices-architecture.mmd` / `.png` | All 7 services, their schemas (one shared PostgreSQL instance and database — `ucms` — with a schema per service), and the actual calls between them — solid = synchronous REST, dotted = async event via the message broker (component view) |

## 2. Service-level (what each service's schema actually contains)

One ER diagram per service — small enough that Mermaid lays each one out cleanly, and focused enough that "which table belongs to which service" is never in question. Each service's tables live in their own PostgreSQL **schema** inside the one shared `ucms` database (not a separate database per service — see the note below and `DATA_GOVERNANCE.md`), with isolation enforced by giving each service its own database role scoped to its own schema. Every mutable table carries a standard audit block (`version`, `created_at`/`by`, `updated_at`/`by`, `deleted_at`/`by`) — see **`DATA_GOVERNANCE.md`** for the full standard, the optimistic-locking mechanics, and the deliberate exceptions (append-only logs and session data don't get versioned or soft-deleted, and the doc explains why).

| File | Service | Status | Tables |
|---|---|---|---|
| `tables-01-identity-access.mmd/png` | Identity & Access | **Built** | users, roles, permissions, role_permissions, user_sessions, role_scope_assignments, assignment_permission_overrides |
| `tables-02-academic-structure.mmd/png` | Academic Structure | **Built** | academic_years, semesters, degree_programs, degree_program_academic_years, course_catalog, program_courses, courses, course_academic_years |
| `tables-03-enrollment.mmd/png` | Enrollment | Planned only | student_program_enrollments, course_enrollments, registration_log |
| `tables-04-assignment-grading.mmd/png` | Assignment & Grading | Planned only | assignments, submissions, grades |
| `tables-05-notification.mmd/png` | Notification | Planned only | notifications, notification_preferences |
| `tables-06-analytics-reporting.mmd/png` | Analytics & Reporting | Planned only | dashboard_snapshots, reports, scheduled_reports |
| `tables-07-reference-data.mmd/png` | Reference Data | **Built** | faculties, departments, grading_scales, course_categories |

Of the 7 services, **3 are built** (Identity & Access, Academic Structure, Reference Data —
all with full CRUD, tested, and deployed) and **4 remain planned-only** (Enrollment,
Assignment & Grading, Notification, Analytics & Reporting) — their `tables-0N` diagrams below
are still the original design, not yet backed by any code. `degree_program_academic_years` and
`course_academic_years` are new since the services were first built: `degree_programs` and
`courses` are stable reference templates, and their actual per-academic-year behaviour
(status, capacity/lecturer overrides, which years they run in at all) lives in these two
junction tables — which is also why more than one `academic_years` row can be `ACTIVE`
simultaneously (a 3-4 year program spans several active years at once). `role_scope_assignments`
and `assignment_permission_overrides` are the real, data-driven RBAC layer described in
`docs/SYSTEM_ARCHITECTURE_REPORT.md` section 5 — fully implemented and API-complete, but with
no frontend screen managing them yet; every user today still has exactly one `GLOBAL`-scope
assignment, seeded 1:1 from their legacy flat `role`.

## 3. Flow-level (how a request actually moves, step by step)

Sequence diagrams — each service gets its own vertical lane, and the arrows show the real order of calls, including what happens when something fails (the `alt`/`else` blocks).

| File | Flow |
|---|---|
| `flow-01-student-enrollment.mmd/png` | Student enrolls in a course — sync capacity check, async confirmation email + live push; includes the "course full" failure path |
| `flow-02-grading.mmd/png` | Lecturer grades a submission — sync enrollment check, then Notification and Analytics consume the same event independently |
| `flow-03-admin-registration.mmd/png` | Admin registers a student into a program — the exact same Enrollment write path as self-service, just a different actor; this is the flow-level proof of the Section 6 "why Administration isn't a service" call in the architecture report |
| `flow-04-login.mmd/png` | Login — three-way branch: valid credentials, wrong credentials, suspended account |

## 4. Proposed: Keycloak OIDC migration (not yet implemented)

See `docs/keycloak-migration-plan.md` for the full plan these diagrams illustrate.

| File | Shows |
|---|---|
| `keycloak-01-topology.mmd/png` | Every component's relationship to Keycloak at once — the SPA's PKCE client, the Gateway's client-less JWKS validation, each backend service's own scoped `client_credentials` client, and the Redis assignments cache |
| `keycloak-02-login-flow.mmd/png` | A user logging in via Authorization Code + PKCE against Keycloak's own hosted page — would supersede `flow-04-login.mmd` |
| `keycloak-03-service-to-service.mmd/png` | One concrete `client_credentials` call (Enrollment → Academic Structure), replacing today's self-minted blanket-admin service token |
| `keycloak-04-assignments-cache.mmd/png` | How the scope-based `assignments` claim survives without living in the Keycloak token, via a Redis cache invalidated at the write side |

## How to read them together

1. **Start with `microservices-architecture.png`** to see which services talk to which, and whether that call is synchronous or event-driven, as a static map.
2. **Open the matching `flow-0N-*.png`** to see that same relationship play out as an actual sequence of calls for a real scenario — this is where "synchronous" and "asynchronous" stop being a line style and become a concrete order of events.
3. **Open the matching `tables-0N-*.png`** to see exactly what each service in that flow stores — real primary keys, real foreign keys, and real relationships, but only *within* that service's own schema.
4. **A field like `lecturer_id "ref Identity and Access, no FK"`** is not a mistake — it's the point. Schema-per-service (one shared database, `ucms`, but a dedicated schema and database role per service) means no table ever has a real foreign key into another service's schema. Cross-service references are IDs resolved through that other service's API, never a SQL join — which is exactly what you see happening, call by call, in the flow diagrams.

**Why one database with 7 schemas, not 7 databases:** the diagrams originally showed a separate database per service — functionally equivalent for the "no cross-service SQL join" invariant, but operationally heavier for this project's actual scale (one Postgres instance to run, back up, and monitor instead of seven). A schema boundary plus a schema-scoped database role gives the same isolation guarantee — a service's credentials simply have no grants outside its own schema — without the extra instances. Revisit if a service's data genuinely needs to scale or fail independently of the others.

## Regenerating

```bash
npx -y @mermaid-js/mermaid-cli -i tables-01-identity-access.mmd -o tables-01-identity-access.png -w 1600 -H 1400 -b white
```

Same pattern for every other `.mmd` file — adjust `-w`/`-H` to the diagram's size (a diagram
taller or wider than the given viewport gets clipped, not scaled, so when in doubt go bigger
and crop later). Edit the `.mmd` files directly — they're the source of truth; the `.png`
files are a build artifact, committed so they render inline on GitHub without a Mermaid
plugin.

**If a diagram ever looks cut off at the bottom:** the render script takes a viewport screenshot, not a full-page one, so a diagram taller than the configured window height gets silently clipped rather than erroring. This actually happened to `tables-04-assignment-grading` once the audit columns were added (its 3-table chain grew past 1700px) — the GRADES table was clipped off, and it looked fine until checked against the `.mmd` source. The render scripts now use a generously tall fixed viewport (`6500px`) specifically to avoid this; if a diagram grows even taller than that in future, bump the height in the relevant `render_*.py` rather than trusting the output size alone.
