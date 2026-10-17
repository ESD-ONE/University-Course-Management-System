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

| File | Service | Tables |
|---|---|---|
| `tables-01-identity-access.mmd/png` | Identity & Access | users, roles, permissions, role_permissions, user_sessions |
| `tables-02-academic-structure.mmd/png` | Academic Structure | academic_years, semesters, degree_programs, course_catalog, program_courses, courses |
| `tables-03-enrollment.mmd/png` | Enrollment | student_program_enrollments, course_enrollments, registration_log |
| `tables-04-assignment-grading.mmd/png` | Assignment & Grading | assignments, submissions, grades |
| `tables-05-notification.mmd/png` | Notification | notifications, notification_preferences |
| `tables-06-analytics-reporting.mmd/png` | Analytics & Reporting | dashboard_snapshots, reports, scheduled_reports |
| `tables-07-reference-data.mmd/png` | Reference Data | faculties, departments, grading_scales, course_categories |

## 3. Flow-level (how a request actually moves, step by step)

Sequence diagrams — each service gets its own vertical lane, and the arrows show the real order of calls, including what happens when something fails (the `alt`/`else` blocks).

| File | Flow |
|---|---|
| `flow-01-student-enrollment.mmd/png` | Student enrolls in a course — sync capacity check, async confirmation email + live push; includes the "course full" failure path |
| `flow-02-grading.mmd/png` | Lecturer grades a submission — sync enrollment check, then Notification and Analytics consume the same event independently |
| `flow-03-admin-registration.mmd/png` | Admin registers a student into a program — the exact same Enrollment write path as self-service, just a different actor; this is the flow-level proof of the Section 6 "why Administration isn't a service" call in the architecture report |
| `flow-04-login.mmd/png` | Login — three-way branch: valid credentials, wrong credentials, suspended account |

## How to read them together

1. **Start with `microservices-architecture.png`** to see which services talk to which, and whether that call is synchronous or event-driven, as a static map.
2. **Open the matching `flow-0N-*.png`** to see that same relationship play out as an actual sequence of calls for a real scenario — this is where "synchronous" and "asynchronous" stop being a line style and become a concrete order of events.
3. **Open the matching `tables-0N-*.png`** to see exactly what each service in that flow stores — real primary keys, real foreign keys, and real relationships, but only *within* that service's own schema.
4. **A field like `lecturer_id "ref Identity and Access, no FK"`** is not a mistake — it's the point. Schema-per-service (one shared database, `ucms`, but a dedicated schema and database role per service) means no table ever has a real foreign key into another service's schema. Cross-service references are IDs resolved through that other service's API, never a SQL join — which is exactly what you see happening, call by call, in the flow diagrams.

**Why one database with 7 schemas, not 7 databases:** the diagrams originally showed a separate database per service — functionally equivalent for the "no cross-service SQL join" invariant, but operationally heavier for this project's actual scale (one Postgres instance to run, back up, and monitor instead of seven). A schema boundary plus a schema-scoped database role gives the same isolation guarantee — a service's credentials simply have no grants outside its own schema — without the extra instances. Revisit if a service's data genuinely needs to scale or fail independently of the others.

## Regenerating

- `python3 render_mermaid.py` — the 2 system-level diagrams
- `python3 render_tables.py` — the 7 per-service ER diagrams
- `python3 render_flows.py` — the 4 sequence/flow diagrams

All three re-render from the `.mmd` source via headless Chrome + Mermaid.js. Edit the `.mmd` files directly — they're the source of truth; the `.png` files are a build artifact.

**If a diagram ever looks cut off at the bottom:** the render script takes a viewport screenshot, not a full-page one, so a diagram taller than the configured window height gets silently clipped rather than erroring. This actually happened to `tables-04-assignment-grading` once the audit columns were added (its 3-table chain grew past 1700px) — the GRADES table was clipped off, and it looked fine until checked against the `.mmd` source. The render scripts now use a generously tall fixed viewport (`6500px`) specifically to avoid this; if a diagram grows even taller than that in future, bump the height in the relevant `render_*.py` rather than trusting the output size alone.
