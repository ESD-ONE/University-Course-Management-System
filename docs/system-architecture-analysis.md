# UCMS — System Architecture

**Date:** 2026-10-26 · **State:** branch `docs/system-audit-phase1` (not yet merged or deployed)
**Method:** every statement below was checked against the code and a running instance of
the full stack — see `test-execution-report.md` (216 automated tests, 55 end-to-end API
steps, 27 browser checks, all passing).

---

## 1. The seven microservices — all built

| # | Service | Port | Schema | Owns |
|---|---|---|---|---|
| 1 | identity-access-service | 8081 | `identity_access` | Login/JWT, logout revocation, users, roles & permission matrix, RBAC scope assignments, profile |
| 2 | academic-structure-service | 8084 | `academic` | Academic years & semesters, degree programs, curriculum, course catalog, courses and their per-year offerings |
| 3 | enrollment-service | 8085 | `enrollment` | Program registrations, course enrollments, waitlist, registration log, rosters |
| 4 | assignment-grading-service | 8086 | `assignment_grading` | Assignments, submissions (files in MinIO), grades, grade history/GPA |
| 5 | notification-service | 8087 | `notification` | In-app notifications, email, live push, notification preferences |
| 6 | analytics-reporting-service | 8088 | `analytics` | Role dashboards, dashboard snapshots, report library (CSV/PDF in MinIO), scheduled reports |
| 7 | base-service *(renamed from reference-data-service)* | 8083 | `base` | Faculties, departments, grading scales, course categories |
| — | gateway | 8080 | — | Path routing, CORS, JWT signature/expiry backstop, logout-revocation check |
| — | web | 80 | — | Angular SPA served by nginx; `/api/**` proxied to the Gateway |

Each service is its own Maven module and container, with its own schema-scoped Postgres
role (it has no grants outside its schema), its own Flyway migrations, and its own JWT
validation. No service reads another service's tables. Cross-service references are plain
UUIDs, resolved by calling the owning service's API.

## 2. Deployment topology

```
Browser ──HTTPS──▶ nginx (TLS, shared host) ──▶ ucms-web (nginx: SPA + /api proxy)
                                                     │
                                                     ▼
                                      ucms-gateway  ── JWT backstop + revocation (Redis)
       ┌───────────┬────────────┬──────────┼──────────┬────────────┬─────────────┐
       ▼           ▼            ▼          ▼          ▼            ▼             ▼
   identity     base      academic   enrollment   grading   notification   analytics
       │           │            │          │          │            │             │
       └───────────┴────────────┴─── PostgreSQL "ucms" (7 schemas) ─┴─────────────┘
   identity ──publish──┐    enrollment ─publish─┐   grading ─publish─┐  analytics ─publish─┐
                       ▼                        ▼                    ▼                     ▼
                              RabbitMQ topic exchange "ucms.events"
                                 │                               │
                     notification.events (+DLQ)        analytics.events (+DLQ)
   Redis: revoked-token list (identity writes, gateway reads)
   MinIO: buckets ucms-assignments (submissions, briefs) and ucms-reports
   SMTP:  notification-service email (Mailpit locally)
```

CI/CD: one Jenkins multibranch pipeline builds and tests all 9 modules and the frontend on
every branch. On `develop`/`release`/`main` it deploys all eight backend containers and the
web container via `infra/docker-compose.deploy.yml`.

## 3. Communication

### Synchronous (REST/JSON)
Browser → Gateway → service. Service-to-service calls carry a short-lived **service
token** (signed with the shared `JWT_SECRET`, role ADMIN, GLOBAL Administrator
assignment). Calls made *on a user's behalf* forward that user's own token instead, so the
callee applies its own ownership rules.

| Caller → callee | Why |
|---|---|
| academic → identity | role/permission catalog, polled every 60s (PermissionResolver) |
| base → academic | department-delete dependents check (fails closed) |
| enrollment → academic | offering open?/capacity/lecturer; program and intake-year validation (flow-01, flow-03) |
| enrollment → identity | is the target a student, is the account active; names for rosters |
| grading → academic | offering's lecturer (ownership), credits, term |
| grading → enrollment | "is this student enrolled" before submit and grade (flow-02); rosters; seat counts |
| grading → base | grading-scale bands for letter grades |
| notification → enrollment / identity | assignment fan-out recipients; recipient email addresses |
| analytics → identity / academic / enrollment / grading | dashboard and report data |

### Asynchronous (RabbitMQ, topic exchange `ucms.events`)
Every event is a JSON envelope `{eventId, type, occurredAt, data}`, routing key = type,
published **after the producing transaction commits**, fire-and-forget.

| Event | Producer | Consumed by |
|---|---|---|
| `identity.user-created` | identity | notification (welcome), analytics |
| `enrollment.student-enrolled` / `student-waitlisted` / `waitlist-promoted` / `course-dropped` / `program-registered` | enrollment | notification, analytics |
| `grading.assignment-published` / `submission-received` / `grade-posted` | grading | notification, analytics |
| `analytics.report-generated` (scheduled reports) | analytics | notification (emails recipients) |

Consumers retry a failing message, then dead-letter it (`*.events.dlq`). Notification
dedupes by (event id, user), so a redelivered message never creates duplicates.

### Live push
notification-service streams new notifications to the browser over **Server-Sent
Events** (`GET /api/notification/notifications/stream`). This fills the role of the
design's "WebSocket Gateway". SSE was chosen because the push is server→browser only: it
needs no protocol upgrade, and it carries the normal `Authorization` header, so the
Gateway's JWT and revocation checks apply to it unchanged.

## 4. Security model

- **Authentication**: identity-access issues an HS-signed JWT (`sub`, `role`,
  `assignments`). Every service validates it statelessly. The Gateway also rejects
  missing, invalid, expired, or **revoked** tokens before routing; logout writes the token
  hash to Redis with a TTL equal to its remaining life.
- **Authorization**: the flat role gates each endpoint, plus service-layer ownership —
  a student acts only for themselves, a lecturer only on offerings they teach, an admin
  anywhere. Degree Program and Course writes in academic-structure also use the
  scope-based `PermissionResolver` (DEPARTMENT-scoped grants, per-assignment overrides),
  which is what gives a "Department Head" real department-wide authority. The frontend
  reads the user's scope assignments from `/users/me` to show them the matching screens.

## 5. Data model highlights

- **enrollment**: `student_program_enrollments` (at most one ACTIVE per student — DB
  partial unique index), `course_enrollments` (ENROLLED / WAITLISTED / DROPPED; dropping
  soft-deletes; at most one live row per student per offering), `registration_log`
  (append-only).
- **assignment_grading**: `assignments` → `submissions` (one per student, replaced on
  resubmit) → `grades` (one per submission). File bytes live in MinIO; only keys and names
  are stored in the database.
- **notification**: `notifications` (unique per source event + user),
  `notification_preferences` (one per user — now the source of truth; seeded on first use
  from the legacy `notify_*` columns in identity_access).
- **analytics**: `dashboard_snapshots` (JSON metric values, unique per role + key),
  `reports` (6 seeded), `scheduled_reports` (frequency spec + next run time).
- Course **offerings** (academic `course_academic_years`) are the unit students enroll in,
  lecturers grade, and analytics reports on. Enrollment and grading key their records by
  offering id.
- **Seat safety**: enrollment serializes seat-taking per offering with a Postgres
  transaction-scoped advisory lock, so two students can't both take the last seat.

## 6. Business lifecycles (all implemented and tested end to end)

1. **Admin registration** (flow-03): admin → Registrations → program registration → log
   entry → `program-registered` event → student notified.
2. **Student enrollment** (flow-01): Browse Courses → enroll → seat check under lock →
   `student-enrolled` → notification + email + live push. When full: 409 `COURSE_FULL` →
   join the waitlist → automatic promotion when a seat frees.
3. **Assignment to grade** (flow-02): lecturer creates and publishes → enrolled students
   notified → student uploads (MinIO) → lecturer grades (enrollment re-confirmed) →
   `grade-posted` → student notified; analytics recomputes the class average.
4. **Reporting**: dashboards per role; reports generated on demand or on schedule, stored
   in MinIO, downloadable as CSV or PDF; scheduled runs are emailed via notification.

## 7. Related documents

`api-inventory.md` (endpoints), `implementation-change-log.md` (what changed and how to
deploy it), `test-execution-report.md` (evidence), `remaining-issues.md` (what's still
open), `reference-data-vs-base-analysis.md` (why the rename stayed a rename).
