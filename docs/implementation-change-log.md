# UCMS — Implementation Change Log

**Date:** 2026-10-26
**Branch:** `docs/system-audit-phase1` (not merged, not pushed as of this writing — working
tree only, per the standing rule to confirm before any commit/push)
**Scope:** (1) fixes for 18 of the 20 gaps in `gap-analysis-report.md`; (2) a rename of
**reference-data-service → base-service** (infrastructure/package/schema/route only);
(3) **the full build-out** — the four planned services (Enrollment, Assignment & Grading,
Notification, Analytics & Reporting), the asynchronous event backbone, live push, Redis
token revocation, MinIO file storage, and every remaining wireframe screen. Part 3 closes
G-20 and finishes G-11. Every change was verified by an actual build/test run.

**Verification** (final): `./mvnw clean verify` across all 9 modules — **216/216 tests
pass**; end-to-end system test against the running stack — **55/55**; browser UI test in
headless Chrome — **27/27**, no console errors; `ng build` clean. See
`test-execution-report.md` and `docs/test-evidence/`.

---

## ⚠️ Required manual steps before deploying this to the live server

### A. New schema roles for the four new services

The live database already has the `enrollment`, `assignment_grading`, `notification` and
`analytics` schemas (created empty on day one), but **not** their login roles —
`init-schemas.sh` only runs against an empty data directory. Run once as the Postgres
superuser, choosing a password for each:

```sql
CREATE ROLE enrollment_svc LOGIN PASSWORD '...';
GRANT USAGE, CREATE ON SCHEMA enrollment TO enrollment_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA enrollment GRANT ALL PRIVILEGES ON TABLES TO enrollment_svc;
ALTER DEFAULT PRIVILEGES IN SCHEMA enrollment GRANT ALL PRIVILEGES ON SEQUENCES TO enrollment_svc;
-- repeat for: assignment_grading_svc / assignment_grading,
--             notification_svc / notification,
--             analytics_svc / analytics
```

### B. New variables in the deploy `.env` (`/opt/ucms-app/infra/.env`)

`ENROLLMENT_SVC_PASSWORD`, `ASSIGNMENT_GRADING_SVC_PASSWORD`, `NOTIFICATION_SVC_PASSWORD`,
`ANALYTICS_SVC_PASSWORD` (from A), plus `RABBITMQ_PASSWORD`, `REDIS_PASSWORD`,
`MINIO_ROOT_PASSWORD` (the values the infra stack already runs with). Optional, to send
real email: `UCMS_MAIL_ENABLED=true`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USER`,
`SMTP_PASSWORD`, `UCMS_MAIL_FROM`. Without them email is logged instead of sent; the in-app
channel works either way.

### C. Upload size on the shared HTTPS proxy

Submissions are up to 20 MB. `web/nginx.conf` now allows 25 MB, but requests to
`https://esd.azhosting.fun` first pass through the other project's nginx-proxy, which uses
nginx's 1 MB default. Add `client_max_body_size 25m;` to the `esd.azhosting.fun` server
block in `/root/travelSriLankaNow/nginx/default.conf`, and `proxy_buffering off;` for
`/api/notification/notifications/stream` (live push), then reload that container.

### D. The base-service schema/role rename (from Part 2)

**This is not optional and is not covered by the code changes below.** The live
Postgres database already has a `reference_data` schema and a `reference_data_svc`
role with real data in it (Faculties, Departments, Grading Scales, Course Categories).
`infra/init-schemas.sh` only runs once, against an empty data directory — it provisions
the renamed `base`/`base_svc` names for a **fresh** environment, but does nothing to the
already-provisioned live one. If the renamed `base-service` container is deployed as-is
against the current live database, it will fail to start (its configured role/schema,
`base_svc`/`base`, don't exist there yet) — or worse, if those names happen to half-exist
from a partial retry, Flyway could try to recreate tables from scratch in an empty schema,
not the live data.

Before deploying this change, run this once against the live database, as the Postgres
superuser (`POSTGRES_USER`), **not** as part of any automated pipeline:

```sql
ALTER SCHEMA reference_data RENAME TO base;
ALTER ROLE reference_data_svc RENAME TO base_svc;
```

And rename the corresponding secret in whatever `.env`/credential store the server's
`docker compose --env-file .env` deploy uses: `REFERENCE_DATA_SVC_PASSWORD` → `BASE_SVC_PASSWORD`
(same password value — only the variable name changes, since the role itself is being
renamed in place, not recreated).

This is a schema/role **rename**, not a data migration — no rows move, no data is at risk,
and it's reversible (`ALTER SCHEMA base RENAME TO reference_data;` undoes it). But it must
happen before the new container starts, and it's not something this change log automates,
per the decision to keep this rename to a branch+PR that a human reviews and deploys.

---

## Part 1 — Gap fixes

### G-01 — Department Head has no frontend surface (CRITICAL)
- **Backend** (`identity-access-service`): `ProfileResponse` now carries an `assignments`
  list (id/roleId/roleName/scopeType/scopeId, no override detail) populated on every
  `/users/me*` response — `web/dto/ProfileResponse.java`, `service/UserService.java`
  (new `assignmentsFor()` helper, wired into `getProfile`/`updateOwnName`/
  `updateOwnPassword`/`updateNotificationPreferences`).
- **Frontend**: `ProfileResponse` model gets the matching `assignments` field;
  `AuthService.hasDepartmentScope` computed signal; new `departmentScopedGuard`;
  `/app/program-management` route guard changed `adminGuard` → `departmentScopedGuard`;
  sidebar nav shows "Program Management" to non-admin DEPARTMENT-scoped users via a
  separate, narrower `@if` block (so no other admin screen's visibility widened).
  `/app/courses` (admin courses) deliberately left `adminGuard`-only for now — contained
  blast radius, Program Management is where Degree Program management actually lives.

### G-02 — Scope-RBAC only wired into one of three services (documentation)
- `identity-access-service` and `base-service`'s `SecurityConfig` classes now carry a
  Javadoc stating plainly: authorization here is flat-role-gated only; the scope-assignment
  data model is fully CRUD-able through these services' own APIs but not consulted for any
  authorization decision here — only by academic-structure-service's `PermissionResolver`.
  No behavior changed; this closes the "silent, confusing" half of the gap, not the
  architectural question (which the `reference-data-vs-base-analysis.md` evaluation
  explicitly declined to resolve by expanding either service's scope).

### G-03 / G-03b — Role kind-collision (HIGH)
- `RoleRepository`: added `existsByNameAndDeletedAtIsNull`; `findByKindAndDeletedAtIsNull`
  replaced with `findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc` (deterministic).
- `RoleService.createRole()` now rejects a duplicate name with a new
  `DuplicateRoleNameException` (409).
- Tests added: role sharing a kind with a seeded role no longer breaks user creation;
  duplicate role name rejected with 409.

### G-04 — Role change doesn't sync scope assignment (HIGH)
- `UserService.updateRole()` now also updates (or creates, if somehow missing) the user's
  GLOBAL `RoleScopeAssignment` to match the new role. Test added confirming the sync.

### G-05 — Department deletion has no cross-service dependents guard (HIGH)
- New `base-service` client `client/AcademicStructureClient.java` calls
  `GET {academic}/degree-programs?departmentId=` and `GET {academic}/course-catalog?departmentId=`
  (the latter is a new filter added to academic-structure-service as part of this same
  fix) before `deleteDepartment()` runs, using a self-minted service JWT mirroring
  academic-structure-service's existing `RoleCatalogClient` pattern. **Fails closed**: if
  the check call errors or times out, the delete does not proceed. New env var
  `ACADEMIC_STRUCTURE_SERVICE_URI` on base-service; `infra/docker-compose.deploy.yml`
  updated. Tests added (dependents found → 409 + no delete; none → 204; check itself
  fails → delete blocked, mocked at the service layer since MockMvc wraps a propagated
  exception before a status assertion can run).

### G-06 — Grading Scale / Course Category have no uniqueness check (MEDIUM)
- Added `existsByNameAndDeletedAtIsNull[AndIdNot]` to both repositories; `create`/`update`
  for both entity types now reject a duplicate name with the existing, already-409-mapped
  `CodeAlreadyInUseException` (reused as-is, no new exception-handler wiring). No new
  `code` field added — deliberately name-uniqueness only, to avoid an API-contract change.
  4 new tests.

### G-07 — Read/write authorization asymmetry (MEDIUM)
- `DegreeProgramService.listCurriculum()` and `CourseOfferingService.listAcademicYearAssignments()`
  now both call `canOnDepartment(..., "View")` before returning data, matching their sibling
  list endpoints. 4 new tests (rejected for a different department, succeeds for the
  caller's own).

### G-08 — Course Catalog has no PUT/DELETE (MEDIUM)
- Added `PUT /course-catalog/{id}` (name/defaultCredits only — code/department not
  editable, same reasoning as Degree Program's immutable identity fields) and
  `DELETE /course-catalog/{id}` (409 if a non-deleted `Course` or `ProgramCourse` still
  references it). Also added the `GET /course-catalog?departmentId=` filter needed by
  G-05's cross-service check. 5 new tests.

### G-09 — Permission overrides parsed but never consulted (MEDIUM)
- `RoleCatalog`/`RoleCatalogClient` now also cache `module|actionKey → permissionId`
  (the sync data already carried this, it just wasn't being retained before). 
  `PermissionResolver.effectiveGrant()` now checks the assignment's `overrides` map for
  that permission id and lets it override the baseline grant in either direction. The
  "Phase 5 — deliberately deferred" comment is removed; overrides are live. 2 new tests
  (an override granting something the baseline denies; an override revoking something
  the baseline grants).

### G-10 — Dead `version` field on role-permission update (LOW)
- `RoleService.updateRolePermission()` now compares the request's `version` (when
  present) to the role's actual `@Version`; mismatch → 409 via a new
  `StaleRoleVersionException`. Omitted `version` (existing callers) is unaffected. 2 new
  tests.

### G-11 — No logout endpoint (LOW, partial fix by design)
- `POST /auth/logout` added to `identity-access-service`, revokes the caller's most
  recent live session (`UserSession.revokedAt`, new column via `V7__user_session_revoked_at.sql`).
  **Explicitly documented limitation, not silently left out**: this does not invalidate
  the JWT itself — every service validates tokens statelessly, so a token issued before
  logout remains valid until its natural expiry. Making logout actually revoke access
  immediately would require every backend service's `JwtAuthFilter` to become a stateful
  check against the session table on every request — a materially larger architectural
  change, explicitly out of scope here and listed in `remaining-issues.md`.

### G-12 — No user deletion endpoint (LOW)
- `DELETE /users/{id}` added (ADMIN-only), using the soft-delete plumbing that already
  existed on `User` but was unreachable. Test confirms a deleted user drops out of
  `GET /users` listings.

### G-13 — Dead frontend code (LOW)
- Removed `AcademicStructureService.activateAcademicYear()` (zero call sites) and the
  orphan `/app/degree-programs` standalone route (the component stays, still used
  embedded inside Program Management).

### G-14 — `/app/reference-degree-details` guard too permissive (LOW)
- Guard changed `authGuard` → `lecturerGuard`, matching where it's actually surfaced in
  the nav (a STUDENT could previously reach it directly by URL).

### G-15 — Notification preferences no-op
- No change made — already correctly disclosed in the UI, per the gap report's own
  conclusion.

### G-16 — Student ID generation race condition (LOW)
- Replaced the 10-attempt random-retry loop with a Postgres sequence
  (`V6__student_id_sequence.sql`, `student_id_seq`), pulled via a native `nextval()`
  query. Public format unchanged: `STU-<year>-<4 digits>`. A `schema.sql` was also added
  for the H2 test profile (Flyway is disabled under `test`), since the real fix needed
  something testable — this only ever runs against the embedded test database.

### G-17 — Login doesn't differentiate INACTIVE vs SUSPENDED (LOW)
- Two distinct 403 messages now, without changing the status code or the (intentionally
  unchanged) non-enumeration behavior for unknown emails/wrong passwords.

### G-18 — Gateway has no auth of its own (MEDIUM, backstop added)
- New `services/gateway/.../security/JwtBackstopFilter.java`: a `GlobalFilter` that
  rejects any request with no/invalid/expired JWT **before** it reaches a backend
  service (except `/api/identity/auth/login` and the gateway's own `/actuator/**`).
  Signature/expiry only — no role or scope logic; every backend service's own
  authorization is unchanged and remains the real decision-maker. This is pure
  defense-in-depth against a future service being added to the routes table without
  its own auth filter; added `jjwt-api/impl/jackson` 0.12.6 to the gateway's `pom.xml`
  (same version every backend service already uses) and a `JWT_SECRET` env var to its
  `docker-compose.deploy.yml` block (previously absent, since the gateway needed no
  secret before this).

### G-19 — SecurityConfig legibility (LOW, documentation)
- A one-line comment in academic-structure-service's `SecurityConfig` now points anyone
  auditing security from that file alone to also check `PermissionResolver` for Degree
  Programs/Courses, since the gate-level rules are intentionally coarser than actual
  enforcement.

---

## Part 2 — The reference-data-service → base-service rename

**Scope, exactly as decided**: infrastructure identity only (directory, Maven artifact,
Java package/class names, DB schema/role, Gateway route, env var names, Jenkinsfile,
`init-schemas.sh`). The business/domain model (Faculty, Department, GradingScale,
CourseCategory — and the `ReferenceStatus` enum) is untouched. The Angular frontend's
"Reference Data" screen, component files, and Angular service/model file names are
**deliberately untouched** — that's the user-facing business feature name, independent
of which microservice happens to serve it (see `reference-data-vs-base-analysis.md` §2
for why conflating the two would have been a mistake).

| What | Before | After |
|---|---|---|
| Directory | `services/reference-data-service` | `services/base-service` |
| Maven artifactId | `reference-data-service` | `base-service` |
| Java package | `com.ucms.referencedata` | `com.ucms.base` |
| Main class | `ReferenceDataServiceApplication` | `BaseServiceApplication` |
| Service bean | `ReferenceDataService` | `BaseService` |
| Test class | `ReferenceDataTest` | `BaseServiceTest` |
| `spring.application.name` | `reference-data-service` | `base-service` |
| DB schema | `reference_data` | `base` |
| DB role | `reference_data_svc` | `base_svc` |
| Gateway route id / path | `reference-data-service` / `/api/reference/**` | `base-service` / `/api/base/**` |
| Gateway → service env var | `REFERENCE_DATA_SERVICE_URI` | `BASE_SERVICE_URI` |
| Service's own DB env vars | `DB_USER=reference_data_svc`, `DB_PASSWORD=${REFERENCE_DATA_SVC_PASSWORD}` | `DB_USER=base_svc`, `DB_PASSWORD=${BASE_SVC_PASSWORD}` |
| Container name | `ucms-reference-data-service` | `ucms-base-service` |
| Frontend API constant | `REFERENCE_API_BASE_URL = '/api/reference'` | `BASE_API_BASE_URL = '/api/base'` |
| Angular feature/screen/files | *(unchanged — not part of this rename)* | `reference-data.component.ts`, `reference-data.service.ts` etc. stay as-is |

**Deliberately NOT touched**: the already-applied Flyway migrations
(`V1__init_schema.sql`, `V2__seed_data.sql`, `V3__add_departments.sql` in the renamed
module) keep their original comment text verbatim (e.g. still say "Reference Data
Service schema" / "schema reference_data") — editing their content, even just a comment,
would change their checksum and break Flyway's validation against the live database's
`flyway_schema_history` table on the next deploy. Same reasoning applied to two existing
comments in academic-structure-service's own already-applied migrations
(`V2__seed_data.sql`, `V3__department_scoping.sql`) that mention "Reference Data" by
name — left untouched for the same checksum-safety reason. Only *new*, not-yet-applied
files and genuinely non-migration source comments were renamed.

Files changed: `pom.xml` (module list), `Jenkinsfile` (build loop), `infra/init-schemas.sh`,
`infra/docker-compose.infra.yml`, `infra/docker-compose.deploy.yml`, `services/gateway/src/main/resources/application.yml`,
every file under the renamed `services/base-service/`, two comments in
`services/academic-structure-service` (non-migration files only), and
`web/src/app/core/config/api.config.ts` + its one consumer
(`web/src/app/core/services/reference-data.service.ts`).

---

## Build/test evidence

```
Tests run: 81, Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.academic.AcademicStructureTest
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.base.BaseServiceTest
Tests run: 8,  Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.identity.AuthFlowTest
Tests run: 7,  Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.identity.ProfileSelfServiceTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.identity.RbacScopeAssignmentTest
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0 -- com.ucms.identity.UserManagementTest
```
160/160 total, from `mvn clean test` across the full 4-module reactor
(identity-access-service, base-service, academic-structure-service, gateway).
`npm run build` under `web/`: zero errors, all chunks (including
`reference-data-component`) still produced.

No step in this log was claimed without the above actually being run and its output
checked — per the governing instruction for this audit not to claim a successful
build/test without verification.

---

## Part 3 — Full build-out of the planned architecture

Implemented from the design artifacts, not invented: the table diagrams
(`architecture/tables-03..06`), the three sequence flows (`flow-01..03`), the Messaging
section of `ARCHITECTURE_ANALYSIS.md`, and every screen in `html-wireframes/index.html`.
Each deliberate departure from a diagram is listed under "Design decisions" below, and the
diagrams were updated to match.

### New services (each: own Maven module, Dockerfile, schema + role, Flyway migrations, JWT validation, tests)

| Service | Port | Highlights |
|---|---|---|
| enrollment-service | 8085 | Program registration (flow-03), course enrollment with capacity check (flow-01), waitlist with automatic FIFO promotion, registration log, rosters, catalog with live seats, student search for Registrations. Seat-taking is serialized per offering with a Postgres advisory lock. |
| assignment-grading-service | 8086 | Assignment lifecycle (Draft → Open → Closed), brief upload, submissions to MinIO (20 MB, deadline + enrollment checks, replace until graded), grading and bulk "Save All Grades" with enrollment re-confirmed (flow-02), grade history with letter grade (from base-service's scale) and GPA, per-course summaries. |
| notification-service | 8087 | Consumes every event; in-app notifications per category preference; email (SMTP, after commit); live push over Server-Sent Events; dedupe by event; preferences owned here (seeded from identity-access's legacy columns). |
| analytics-reporting-service | 8088 | Admin dashboard snapshots (recomputed every 5 min and within ~15s of events); live lecturer and student dashboards; 6-report library generated as CSV/PDF into MinIO; scheduled reports (daily/weekly/monthly, Asia/Colombo time) announced for email delivery. |

### Changes to existing services

- **identity-access**: `GET /users/{id}`, `GET /users/lookup?ids=`; publishes
  `identity.user-created`; **logout now revokes the token** — its hash goes to Redis with
  the token's remaining lifetime as TTL (finishes G-11).
- **gateway**: routes for the four services; the JWT backstop also rejects revoked tokens
  (fails open if Redis is unreachable, logged).
- **academic-structure**: `GET /course-offerings` and `/course-offerings/{id}` (resolved
  capacity, lecturer, credits, Core/Elective, and open-for-enrollment state from status,
  year and semester window); `GET /degree-programs/{id}`.
- **base-service**: V4 migration aligning the seeded department ids with the ids
  academic-structure references (pre-existing fresh-environment bug, found by system
  testing).
- **All service tokens** now carry a GLOBAL Administrator assignment. This fixes the G-05
  dependents check, which silently never saw degree programs (found during this work).

### Infrastructure

- `init-schemas.sh`: roles for the four new schemas.
- `docker-compose.infra.yml`: passwords for the new roles; Mailpit (dev SMTP catcher);
  MinIO image switched to `cgr.dev/chainguard/minio` because `minio/minio` is no longer
  pullable.
- `docker-compose.deploy.yml`: the four services with healthchecks and ordered
  `depends_on`; RabbitMQ/Redis/MinIO/SMTP wiring; Gateway routes and Redis.
- `Jenkinsfile`: deploys all eight backend services.
- `web/nginx.conf`: 25 MB uploads; unbuffered, long-lived proxying for the live stream.

### Frontend (every remaining wireframe screen; "Coming soon" placeholders removed)

- **Admin**: Registrations (program + course, on behalf), Roles & Permissions (permission
  matrix, custom roles, scope assignments — the UI for making a Department Head), Reports
  (generate, download, schedule, pause/resume), analytics dashboard.
- **Lecturer**: Assignments, Grading (marks, feedback, file download, Save All), Students
  and per-course Roster (export CSV), Registrations into own courses, analytics dashboard.
- **Student**: Browse Courses (enroll / waitlist), My Enrollments (drop), My Program
  (curriculum progress), Assignments (instructions, brief, upload), Grades (history, GPA),
  analytics dashboard.
- **Everyone**: notification bell with live push and toasts; Log Out now revokes the token
  server-side; Profile preferences now saved to notification-service.
- **Session expiry**: tokens now last 24 hours (`JWT_EXPIRATION_MINUTES`, default 1440,
  was 60). The browser signs the user out automatically when the token expires: a timer set
  from the token's `exp` (fires even on an idle page), an expired token found at page load,
  or any 401 from the API (e.g. the token was revoked by a logout in another tab). The user
  lands on the login page with "Your session has expired. Please sign in again."

### Design decisions (departures from the diagrams, each deliberate)

1. `course_id` became `course_offering_id` in enrollment and grading: since
   academic-structure split courses into templates and per-year offerings, students enroll
   in an offering.
2. Live push is **Server-Sent Events** from notification-service, not a separate WebSocket
   gateway: it's one-directional and carries the normal Authorization header, so Gateway
   auth and revocation apply unchanged.
3. Added columns needed for correctness, all documented in the migrations and diagrams:
   file keys and sizes for submissions/briefs (MinIO), `source_event_id` (deduping
   redelivered events), notification `category`/`link`, report `object_key`, schedule
   `next_run_at`.
4. New services authorize by flat role + ownership (student-self, lecturer-of-offering,
   admin). The scope-based PermissionResolver stays in academic-structure, where the
   department structure it scopes by actually lives.
5. Events are published after commit with no outbox, so a broker outage can lose an event.
   Accepted for notifications and analytics; analytics also recomputes on a timer.

---

# Part 4 — Security hardening, load balancing, rate limiting

**Date:** 2026-10-29 · **Plan:** [`security-loadbalancing-ratelimit-plan.md`](security-loadbalancing-ratelimit-plan.md)
· **Builds on:** the Keycloak migration (`keycloak-migration-plan.md`), also still uncommitted.

**Verification:** `./mvnw verify` — **239/239** (14 new tests); system test **66/66** (4 new
steps); container/edge test **13/13**; real-browser check under the production CSP **5/5**;
zero-downtime rolling redeploy under load **686/686 requests OK**. Details:
[`test-execution-report.md`](test-execution-report.md) Part 4.

## ⚠️ Production rollout — run in this order

The application stack moves onto new Docker networks that the infra stack creates, so
steps 3 and 4 are one short maintenance window (a few minutes: Postgres, Redis, RabbitMQ,
MinIO and Keycloak restart once).

1. **Fix the host's shared nginx first** (the one terminating HTTPS for
   `esd.azhosting.fun`). After this change it is the *only* way in — the web container
   no longer listens on the server's public address. Its `location` for the site needs:
   ```nginx
   proxy_pass http://127.0.0.1:8091;
   proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;   # rate limits key on this
   proxy_set_header X-Forwarded-Proto https;
   proxy_set_header Host              $host;
   client_max_body_size 25m;                                         # 20 MB submissions
   # and for location /api/notification/notifications/stream:
   proxy_buffering off; proxy_read_timeout 1h; proxy_http_version 1.1; proxy_set_header Connection "";
   ```
   (This also closes remaining-issues §1 item 5.)
2. **Check the Docker Compose version** (`docker compose version`). v2.19.1 was seen to
   attach a container to only some of its networks; `rolling-deploy.sh` detects and repairs
   that, but a current Compose (≥ 2.24) avoids it.
3. **Infra stack:** copy `infra/docker-compose.infra.yml` and `infra/keycloak/` to
   `/opt/ucms/` and run `docker compose --env-file .env up -d --build` there. Then check
   Keycloak got all three networks:
   `docker inspect -f '{{range $n,$_ := .NetworkSettings.Networks}}{{$n}} {{end}}' ucms-keycloak`
   → `ucms_app ucms_data ucms_edge`. If one is missing:
   `docker compose ... up -d --force-recreate --no-deps ucms-keycloak`.
4. **Application stack:** let Jenkins deploy (or run `infra/rolling-deploy.sh` in
   `/opt/ucms-app/infra`). The first run replaces the old single containers with replicas.
   Before it, check memory: each Java replica is capped at 768 MB; defaults are 13 replicas
   (2 × gateway, identity, academic, enrollment, notification; 1 × base, grading, analytics).
   On a small host lower them in `.env` (`GATEWAY_REPLICAS=1`, etc.).
5. **Keycloak realm hardening** (the realm already exists, so the realm file isn't
   re-imported): on the host,
   `KEYCLOAK_ADMIN_PASSWORD=... python3 infra/keycloak/harden_realm.py --url http://127.0.0.1:8180/auth`
   — password policy, event logging, web-client redirect origins. Safe to re-run.
   Optional, after every administrator has an authenticator app: `--admin-mfa on`.
6. **Close the old public port:** `ufw delete allow 8091/tcp` (the container now binds
   127.0.0.1 only, so this is tidying up).
7. **SSH tunnel for the API** changes from `8090` to `8091` (`/api/...` through the web
   container, which load-balances over the gateway replicas) — see `LOGIN_USERS.md`.

## What changed

### Edge — `web/nginx.conf`, `web/nginx/*.conf`, `web/Dockerfile`
- Security headers on every response: HSTS, `X-Content-Type-Options`, `Referrer-Policy`,
  `Permissions-Policy`, `Cross-Origin-Opener-Policy`; a strict CSP for the app
  (`script-src 'self'`, no inline scripts, `frame-ancestors 'self'`); `server_tokens off`.
  Keycloak's pages keep Keycloak's own CSP and headers (only the two it lacks are added).
- Keycloak's admin console, master realm, health and metrics return 404 from the site.
- Real client IP from the host proxy (`set_real_ip_from` private ranges); per-IP limits:
  password form 30/min (burst 30), token endpoint 120/min, API 50/s (burst 200), live
  streams 10 per IP. Over the limit → 429.
- Request bodies 1 MB, except the two upload endpoints (25 MB, streamed).
- Gateway replicas behind an `upstream` with `resolve` (picks up replicas as they change).
- Access log line carries the request id and the gateway replica that served it.
- Frontend: critical-CSS inlining off (it injected an inline `onload` the CSP would
  block); `silent-check-sso.html` uses an external script; 429s show "please wait N
  seconds"; profile password rule 12–128 characters.

### Gateway — `services/gateway`
- `ratelimit/RateLimitFilter` + `scripts/token_bucket.lua`: per-user token buckets in
  Redis, shared by every replica, tiers in `application.yml` (`ucms.rate-limit`): uploads
  10/min, analytics writes 6/min, enrollment writes 2/s (burst 10), user-account writes 2/s
  (burst 10), everything else 20/s (burst 40); the live stream is exempt. Keyed on the
  verified user (`JwtBackstopFilter` now passes it on), else the client IP. 429 +
  `Retry-After` + `{"code":"RATE_LIMITED"}`. Fails open if Redis is down (logged).
- `lb://` routes over `discovery/DnsDiscoveryClient` (Docker DNS answers a service name
  with every replica), health-checked every 5 s; per-route circuit breaker with a 503
  fallback (`web/FallbackController`); GET-only retries on another replica; connect 2 s /
  response 60 s timeouts (the live stream has none).
- `web/RequestIdFilter`: `X-Request-Id` on every request, passed to the service and echoed.
- Graceful shutdown (20 s) for rolling deploys.

### Services
- **All:** graceful shutdown; Hikari pool 5 per replica (`DB_POOL_SIZE`); `RequestIdFilter`
  puts the request id in every log line; validation errors no longer echo Spring's
  internal exception text (identity, base, academic-structure did — it exposed class and
  method signatures).
- **Six callers** (base, academic, enrollment, grading, notification, analytics):
  `config/ServiceClientConfig` — a `@LoadBalanced` `RestClient.Builder` with 2 s connect /
  10 s read timeouts (30 s for analytics) and request-id propagation; one retry of a failed
  read on another replica; `config/DnsDiscoveryClient`. The `*-service-uri` settings are
  now service names; the real locations are under `ucms.discovery.services`, read from the
  same `*_SERVICE_URI` variables as before.
- **identity-access, analytics-reporting:** ShedLock (`config/SchedulerLockConfig`,
  migrations `V9__shedlock.sql` / `V3__shedlock.sql`) so the Keycloak reconciler and the
  report/dashboard jobs run on one replica at a time.
- **notification-service:** `LivePushFanout` — live pushes go through a Redis channel to
  every replica, so a notification reaches the user whichever replica holds their stream.
- **academic-structure-service:** `RoleCatalogClient.refreshWhileEmpty()` retries every
  5 s until the permission catalog first loads (a replica starting before identity no
  longer denies everything for a minute). *This fix had been reported as written in the
  Keycloak round but was not actually in the file.*
- **identity-access-service:** passwords 12–128 characters (create + change); Keycloak
  refusing a change on policy grounds is now a 400 with Keycloak's reason
  (`KeycloakRejectedException`), not a misleading 503.

### Keycloak — `infra/keycloak/`
- Realm file: password policy `length(12) and maxLength(128) and notUsername and notEmail
  and passwordHistory(3)`; login + admin events (30 days); the plain-HTTP server address
  removed from the web client's redirect origins.
- `harden_realm.py`: applies the above to an existing realm; `--admin-mfa on|off` adds or
  removes a required one-time-password step for ADMIN accounts (verified both ways).
- Base images pinned by digest.

### Containers and deployment — `infra/`, `services/*/Dockerfile`, `Jenkinsfile`
- Java images: Temurin 17.0.20.1 JRE, pinned by digest, non-root user, heap sized from the
  container limit, 5 s DNS cache. Every Java container: read-only filesystem (`/tmp`
  tmpfs), all Linux capabilities dropped, `no-new-privileges`, 768 MB / 1 CPU cap, 30 s
  stop grace. nginx 1.30.6 (pinned), read-only, minimal capabilities.
- Three networks: `ucms_edge` (web, gateway, Keycloak), `ucms_app` (gateway, services,
  Keycloak, Redis), `ucms_data` (services, Keycloak, Postgres, RabbitMQ, MinIO, mail). The
  internet-facing web container cannot resolve the database or any service; the gateway
  cannot reach the database.
- Replicas per service (`*_REPLICAS` in `.env`); no `container_name` on scaled services;
  the gateway no longer publishes a port; `ucms-web` binds 127.0.0.1 only; CORS only for
  the HTTPS domain. Postgres `max_connections=200`; Keycloak pool 20. Healthcheck grace
  120 s (cold start of every JVM at once).
- `infra/rolling-deploy.sh`: per service, start new replicas beside the old, wait until
  healthy, let discovery see them, retire the old (graceful). Aborts with the old replicas
  still serving if a new one doesn't come up. Repairs missing network attachments
  (`--check-networks` does only that).
- Jenkins: **Security Scan** stage (Trivy on the built jars, `npm audit` on production
  packages, Trivy on the images before they go live), reports archived;
  `SECURITY_GATE='report'` marks the build UNSTABLE on findings, `'fail'` fails it. Deploy
  now runs `rolling-deploy.sh`.

## Departures from the plan, each deliberate

1. **Refresh-token rotation not enabled.** Every browser tab shares one Keycloak client
   session, so rotation would make the other tabs' refresh tokens stale and sign them out.
   Refresh tokens stay in memory only (keycloak-js), and access tokens last 5 minutes.
2. **Own rate limiter instead of Spring's `RequestRateLimiter`.** Spring's keys its
   buckets on the caller only, so a route-specific limit and the default limit on the same
   route would share (and corrupt) one bucket. Ours keys on tier + caller and supports
   per-minute rates.
3. **Tier numbers loosened** where the plan's would have broken real use: enrollment writes
   burst 10 (plan 5), user-account writes 2/s burst 10 (plan 1/s burst 5) — an admin adding
   several users in a row. nginx per-IP limits are higher than the plan's because a campus
   NAT puts a whole lab behind one address.
4. **No circuit breakers on service-to-service calls.** Timeouts bound a hung dependency,
   a failed read is retried on another replica, and every client already turns a failure
   into a 503; the gateway's breakers protect the edge.
5. **Trivy instead of OWASP dependency-check** — no NVD API key is available on Jenkins, and
   Trivy also scans the images. **The gate is in report mode**: the current dependencies
   have known CVEs (remaining-issues §5), so a failing gate would block every build today.
6. **Admin MFA built but not switched on** — waiting on confirmation that administrators
   have authenticator apps (plan open decision 4).
