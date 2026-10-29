# UCMS — API Inventory

**Date:** 2026-10-26
**Method:** built directly from source (controllers, `SecurityConfig` classes, service-layer
permission checks, DTOs, Flyway migrations) via exhaustive per-service code inventory, not
from documentation. File:line citations exist for every claim in the four raw inventory
passes this document was synthesized from; ask for a specific citation if one is needed —
they are omitted here for readability.

Every endpoint below is reachable only through the Gateway at
`http://<gateway>/api/{identity|reference|academic}/**` — the paths listed under each
service are the path **after** the Gateway's `StripPrefix=2` filter removes
`/api/<segment>`.

---

## Gateway (`services/gateway`, port 8080)

Pure routing + CORS. No auth, no rate limiting — confirmed by an exhaustive file search:
the module contains exactly one Java file (`GatewayApplication.java`, 11 lines, no
filters/controllers/security config), and its `pom.xml` has no security or JWT dependency.

| Path prefix | Target | Target URI env var (default) |
|---|---|---|
| `/api/identity/**` | identity-access-service | `IDENTITY_SERVICE_URI` (`http://localhost:8081`) |
| `/api/reference/**` | reference-data-service | `REFERENCE_DATA_SERVICE_URI` (`http://localhost:8083`) |
| `/api/academic/**` | academic-structure-service | `ACADEMIC_STRUCTURE_SERVICE_URI` (`http://localhost:8084`) |

CORS (`spring.cloud.gateway.globalcors`, applied to `/**`): single origin via
`CORS_ALLOWED_ORIGIN` (default `http://localhost:4200`), methods
GET/POST/PUT/PATCH/DELETE/OPTIONS, all headers, `allowCredentials: true`.

---

## identity-access-service (port 8081)

**Global `SecurityConfig` gate**: `/auth/login` + `/actuator/**` = `permitAll`;
`/users/me`, `/users/me/**` = `authenticated()` (self-service by URL shape only — the
JWT principal is used solely to stamp audit columns, never for an explicit
"does this id match the caller" check); `/users/**`, `/roles/**`, `/permissions/**`,
`/assignments/**` = `hasRole("ADMIN")`; everything else = `authenticated()`.

| Method | Path | Auth | Request | Response | Notes |
|---|---|---|---|---|---|
| POST | `/auth/login` | public | `{email,password}` | `{token,role,name}` (+ persists a `user_sessions` row) | No logout endpoint exists anywhere; sessions are write-only |
| POST | `/users` | ADMIN | `CreateUserRequest{name,email,role,status?,password?}` | `CreatedUserResponse` (201, incl. one-time `temporaryPassword` if none supplied) | `role` lookup via `findByKindAndDeletedAtIsNull` — see Gap G-03 |
| GET | `/users` | ADMIN | query `role?,status?,q?,page,size` (size clamped 1–100) | `PagedResponse<UserResponse>` | |
| GET | `/users/summary` | ADMIN | — | `{activeTotal,activeCount,studentCount,staffCount}` | |
| GET | `/users/me` | self (any role) | — | `ProfileResponse` | |
| PATCH | `/users/me` | self | `{name}` | `ProfileResponse` | |
| PATCH | `/users/me/password` | self | `{currentPassword,newPassword}` | `ProfileResponse` | |
| PATCH | `/users/me/notification-preferences` | self | `{email,assignments,grades,registrations}` | `ProfileResponse` | **No `@Valid`/constraints on the DTO at all** |
| PATCH | `/users/{id}/status` | ADMIN | `{status}` | `UserResponse` | INACTIVE/SUSPENDED is the only "deactivate" path — no delete exists |
| PUT | `/users/{id}/role` | ADMIN | `{role}` | `UserResponse` | Does **not** update `role_scope_assignments` — see Gap G-04 |
| GET | `/roles` | ADMIN | — | `List<RoleResponse>` | Also polled every 60s by academic-structure-service's `RoleCatalogClient` — the one real inter-service call in the system |
| GET | `/permissions` | ADMIN | — | `List<PermissionResponse>` | |
| POST | `/roles` | ADMIN | `{name,kind}` | `RoleResponse` (201) | **No uniqueness check on name or (name,kind)** — see Gap G-03 |
| PATCH | `/roles/{roleId}/permissions` | ADMIN | `UpdateRolePermissionRequest{grants[],version}` | `RoleResponse` | `version` field accepted but **never read/compared** — see Gap G-10 |
| GET | `/users/{userId}/assignments` | ADMIN | — | `List<AssignmentResponse>` | |
| POST | `/users/{userId}/assignments` | ADMIN | `{roleId,scopeType,scopeId?}` | `AssignmentResponse` (201) | |
| PATCH | `/assignments/{assignmentId}/overrides` | ADMIN | `{overrides:{permissionId:bool}}` | `AssignmentResponse` | |
| DELETE | `/assignments/{assignmentId}` | ADMIN | — | 204 | **The only delete endpoint in the entire service** — no delete for users, roles, or permissions exists anywhere |

Entities of note: `User` (email UNIQUE, flat `role` FK, nullable `studentId` UNIQUE, 4
`notify_*` booleans), `UserSession` (no soft-delete, deliberately ephemeral, never
queried back), `Role`/`Permission`/`RolePermission`, `RoleScopeAssignment` +
`AssignmentPermissionOverride` (the newer scope-based RBAC layer — see Gap G-02).

---

## reference-data-service (port 8083)

4 structurally identical controllers (Faculty/Department/GradingScale/CourseCategory),
delegating to one shared `ReferenceDataService`. `SecurityConfig`: GET = `authenticated()`
(any role); POST/PUT/DELETE = `hasRole("ADMIN")` on all four.

| Resource | GET (list) | GET filter | POST | PUT | DELETE |
|---|---|---|---|---|---|
| `/faculties` | any role | — | ADMIN, `{name,code,status?}`, 409 on dup `code` | ADMIN, `status` `@NotNull` | ADMIN, **409 if any department still references it** |
| `/departments` | any role | `?facultyId=` | ADMIN, `{name,code,facultyId(NotNull),status?}`, 409 on dup `code` (global, not per-faculty), 404 if parent faculty missing/deleted | ADMIN, allows silent re-parenting to a different `facultyId`, `status` `@NotNull` | ADMIN, **unconditional — no dependents guard at all**, despite being referenced cross-service by `degree_programs.department_id`/`course_catalog.owning_department_id` |
| `/grading-scales` | any role | — | ADMIN, `{name,bands,status?}`, **no uniqueness check of any kind** | ADMIN, `status` `@NotNull` | ADMIN, unconditional |
| `/course-categories` | any role | — | ADMIN, `{name,description,status?}`, **no uniqueness check** | ADMIN, `status` `@NotNull` | ADMIN, unconditional |

Consistent pattern across all four: Create DTOs have optional `status` (server defaults to
ACTIVE); Update DTOs require it. `ReferenceStatus` = `ACTIVE`/`INACTIVE` only. Only
Faculty→Department has a dependents guard (`HasDependentsException`, 409); Department,
GradingScale, and CourseCategory deletes are all unconditional soft-deletes.

---

## academic-structure-service (port 8084)

Coarse `SecurityConfig` gate (GET `/**` = `authenticated()`; a short explicit list of
POST/PUT/DELETE path patterns = `authenticated()`; everything else, including
`/academic-years/**` writes and `POST /course-catalog`, falls to `anyRequest().hasRole("ADMIN")`)
is only the first gate. For Degree Programs and Courses, the **real** access decision is
pushed into the service layer via `PermissionResolver`, which reads `DEPARTMENT`/`GLOBAL`
scope assignments out of the caller's JWT (`assignments` claim) and checks a cached
role→module→action grant matrix synced from identity-access-service every 60s.

### Academic Calendar (`AcademicCalendarController`) — gate-level ADMIN-only, no service-layer scoping

| Method | Path | Notes |
|---|---|---|
| GET | `/academic-years` | any role |
| POST | `/academic-years` | ADMIN; 409 on dup name; optional auto-create default semesters |
| POST | `/academic-years/{yearId}/semesters` | ADMIN |
| PATCH | `/academic-years/{yearId}/activate` | ADMIN; **does not deactivate any other year** — multiple years can be ACTIVE simultaneously, by design (see system-architecture-analysis.md §3) |
| PUT | `/academic-years/{yearId}` | ADMIN; same non-exclusive-activation behavior |
| DELETE | `/academic-years/{yearId}` | ADMIN; 409 if status==ACTIVE or has dependent program/course-year assignments; cascades soft-delete to its semesters |
| PUT | `/academic-years/{yearId}/semesters/{semesterId}` | ADMIN; 409 if parent year ARCHIVED |
| DELETE | `.../semesters/{semesterId}` | ADMIN; 409 if dependent `CourseAcademicYear` rows reference it |

### Degree Programs & Curriculum (`DegreeProgramController`) — gate-level `authenticated()`, real check is `PermissionResolver.canOnDepartment(departmentId, "Degree Programs", action)`

| Method | Path | Service-layer authz |
|---|---|---|
| GET | `/degree-programs?departmentId=` | global `View` grant → sees all; else filtered to departments the caller can view |
| POST | `/degree-programs` | requires `canOnDepartment(..., "Create")`; 409 dup code |
| PUT | `/degree-programs/{id}` | requires `Edit`; `code`/`departmentId` not editable (deliberate — see service Javadoc) |
| DELETE | `/degree-programs/{id}` | requires `Delete`; 409 if it has courses, academic-year assignments, or curriculum |
| GET | `/degree-programs/{id}/curriculum` | **no permission check at all** — any authenticated user can read any program's curriculum regardless of department scope (Gap G-07) |
| POST | `/degree-programs/{id}/curriculum` | requires `Edit` |
| GET | `/degree-program-academic-years?academicYearId=` | filtered same as the list endpoint above |
| POST | `/degree-programs/{id}/academic-years` | requires `Edit`; `courseIds` null ⇒ cascades to every course in the program (best-effort, no re-check); explicit list ⇒ exactly those |
| PUT | `.../academic-years/{assignmentId}` | requires `Edit` |
| DELETE | `.../academic-years/{assignmentId}` | requires `Delete` |
| GET | `.../academic-years/{assignmentId}/courses` | requires `View` |

### Courses (`CourseController`, `@RequestMapping("/courses")`) — gate-level `authenticated()`; service-layer self-vs-on-behalf-of-another-lecturer pattern

| Method | Path | Service-layer authz |
|---|---|---|
| GET | `/courses?mine=&degreeProgramId=` | `mine=true` → caller's own courses (literal `lecturerId` match) **or** any course in a department the caller is DEPARTMENT-scoped `Courses:View` for |
| POST | `/courses` | acting for self → `canOnDepartment(dept,"Courses","Create")`; acting on another lecturer's behalf → `ADMIN` or DEPARTMENT-scoped `Create` |
| PUT | `/courses/{id}` | same self/on-behalf pattern, action `Edit` |
| DELETE | `/courses/{id}` | same pattern, `Delete`; cascades soft-delete to its `CourseAcademicYear` rows |
| GET | `/courses/{id}/academic-years` | **no permission check at all** — same read/write asymmetry as curriculum (Gap G-07) |
| POST | `/courses/{id}/academic-years` | same self/on-behalf pattern, `Edit`; 409 if target year ARCHIVED or already assigned |
| PUT | `.../academic-years/{assignmentId}` | same pattern, `Edit`; 409 if year is now ARCHIVED |
| DELETE | `.../academic-years/{assignmentId}` | same pattern, `Delete` |

### Course Catalog (`CourseCatalogController`, `@RequestMapping("/course-catalog")`)

| Method | Path | Notes |
|---|---|---|
| GET | `/course-catalog` | any role |
| POST | `/course-catalog` | gate-level ADMIN-only (not one of the explicit `authenticated()` patterns); **no `PermissionResolver` check at all**; 409 dup code |
| — | — | **No PUT or DELETE exists anywhere for Course Catalog entries** — Create+Read only (Gap G-08) |

### Cross-cutting facts

- **Multiple academic years can be simultaneously ACTIVE** — a deliberate V7 migration
  reversal of an earlier single-active-year constraint (degree programs run 3–4 years,
  so concurrent cohorts have concurrently live coursework). No schema or service
  constraint prevents it; only ARCHIVED years reject writes.
- **`AssignmentClaim.overrides` (per-assignment permission overrides) are parsed out of
  the JWT but never consulted** — `PermissionResolver.effectiveGrant()` only calls the
  cached role-baseline grant, explicitly deferred to "Phase 5" per its own comment
  (Gap G-09).
- `RoleCatalogClient` fails open: if its 60-second sync to identity-access-service's
  `/roles` fails, it keeps serving the last-known-good cache rather than clearing it.
- `GlobalExceptionHandler` mapping (shared pattern across all 3 services):
  `DuplicateEntityException`/`ArchivedYearException`/`HasDependentsException`/
  `ObjectOptimisticLockingFailureException` → 409; `EntityNotFoundException` → 404;
  `MethodArgumentNotValidException`/`IllegalArgumentException` → 400;
  `AccessDeniedException` → 403 (generic message).

---

## Frontend → backend call coverage

Every backend endpoint above has at least one Angular call site, confirmed by the frontend
inventory, with two exceptions:

- `AcademicStructureService.activateAcademicYear()` (→ `PATCH /academic-years/{id}/activate`)
  is defined on the Angular service but has **zero call sites** anywhere in the app —
  the Academic Calendar screen only ever sends full-record updates with `status: 'ACTIVE'`
  through the regular `PUT`. Dead frontend method (Gap G-13).
- The `/app/degree-programs` **route** exists in `app.routes.ts` but has no inbound link
  anywhere in the UI — `DegreeProgramsComponent` is only ever reached embedded as a tab
  inside Program Management. Dead route (Gap G-13).

No backend endpoint was found with zero frontend caller otherwise — frontend/backend
endpoint coverage is otherwise 1:1 for all three built services.

See `gap-analysis-report.md` for the full severity-ranked gap table, including the
frontend-authorization-model mismatch this inventory surfaced: the Angular app's only
authorization primitives are `adminGuard`/`lecturerGuard`, a flat string compare against
`ProfileResponse.role` — there is no frontend concept of the DEPARTMENT-scoped grants that
`PermissionResolver` actually enforces server-side in academic-structure-service (Gap G-01).

---

# Addendum (after the full build-out) — new and changed endpoints

Gateway prefixes: `/api/identity`, `/api/base` (renamed from `/api/reference`),
`/api/academic`, `/api/enrollment`, `/api/grading`, `/api/notification`, `/api/analytics`.
Every route requires a valid, non-revoked JWT at the Gateway except `POST /api/identity/auth/login`.

## identity-access-service (additions)
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/auth/logout` | any | revokes this token (Redis) — rejected by the Gateway from then on |
| GET | `/users/{id}` | ADMIN | used by other services with a service token |
| GET | `/users/lookup?ids=` | ADMIN | batch id → user |

## academic-structure-service (additions)
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/course-offerings?academicYearId=&status=&lecturerId=&degreeProgramId=&ids=&mine=` | any | resolved offering: capacity, lecturer, credits, Core/Elective, `openForEnrollment` |
| GET | `/course-offerings/{id}` | any | |
| GET | `/degree-programs/{id}` | View on its department | |

## enrollment-service
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/program-enrollments` | ADMIN | 409 `ALREADY_REGISTERED` if an active one exists |
| GET | `/program-enrollments?studentId=&degreeProgramId=` | ADMIN, LECTURER | |
| GET | `/program-enrollments/me` | any | |
| PATCH | `/program-enrollments/{id}` | ADMIN | year of study / status |
| POST | `/course-enrollments` | student (self), lecturer (own offering), admin | 409 `COURSE_FULL` / `NOT_OPEN` / `ALREADY_ENROLLED`; `joinWaitlistIfFull` |
| DELETE | `/course-enrollments/{id}` | owner student, offering lecturer, admin | drop; promotes the first waitlisted student |
| GET | `/course-enrollments/me` | any | |
| GET | `/course-enrollments?offeringId=` | offering lecturer, admin | roster |
| GET | `/course-enrollments/my-students` | LECTURER, ADMIN | |
| GET | `/course-enrollments/check?studentId=&offeringId=` | ADMIN (service) | flow-02 enrollment check |
| GET | `/offerings/seats?ids=` | any | enrolled / waitlisted counts |
| GET | `/catalog?academicYearId=` | any | Browse Courses data incl. caller's status |
| GET | `/registrations?limit=&studentId=` | ADMIN (all), LECTURER (own actions) | |
| GET | `/students?q=` | ADMIN, LECTURER | student picker |
| GET | `/stats` | ADMIN (service) | analytics aggregates |

## assignment-grading-service
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/assignments?offeringId=&status=` | role-aware | admin all, lecturer own offerings, student enrolled + published |
| GET | `/assignments/{id}` | teaching staff / enrolled student | |
| POST | `/assignments` | offering lecturer, admin | created as DRAFT |
| PUT | `/assignments/{id}` | offering lecturer, admin | max marks locked once grading starts |
| PATCH | `/assignments/{id}/status` | offering lecturer, admin | DRAFT→OPEN publishes |
| DELETE | `/assignments/{id}` | offering lecturer, admin | 409 if it has submissions |
| POST / GET | `/assignments/{id}/brief` | staff / viewers | multipart upload / download |
| GET | `/assignments/{id}/submissions` | offering lecturer, admin | every enrolled student |
| POST | `/assignments/{id}/submissions` | enrolled STUDENT | multipart; 409 `NOT_OPEN` / `PAST_DUE` / `ALREADY_GRADED` |
| GET | `/assignments/{id}/submissions/me` | STUDENT | |
| GET | `/submissions/{id}/file` | owner, offering lecturer, admin | download |
| PUT | `/submissions/{id}/grade` | offering lecturer, admin | 400 marks > max; 409 `NOT_ENROLLED` |
| PUT | `/assignments/{id}/grades` | offering lecturer, admin | bulk "Save All Grades" |
| GET | `/grades/me` | STUDENT | per-course %, letter, points; GPA |
| GET | `/grades/offerings/{id}/summary` | offering lecturer, admin | class average |
| GET | `/stats/offering-summaries?ids=`, `/stats/course-results` | ADMIN (service) | analytics |

## notification-service
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/notifications/me?unreadOnly=&page=&size=` | any | |
| GET | `/notifications/me/unread-count` | any | |
| PATCH | `/notifications/{id}/read` | owner | |
| POST | `/notifications/me/read-all` | any | |
| GET | `/notifications/stream` | any | Server-Sent Events live push |
| GET / PUT | `/preferences/me` | any | email + per-category toggles |

## analytics-reporting-service
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/dashboards/admin` · POST `/dashboards/admin/refresh` | ADMIN | snapshot |
| GET | `/dashboards/lecturer` | LECTURER | live |
| GET | `/dashboards/student` | STUDENT | live |
| GET | `/reports` | ADMIN | |
| POST | `/reports/{id}/generate` | ADMIN | file stored in MinIO |
| GET | `/reports/{id}/download` | ADMIN | 404 until generated |
| GET / POST | `/scheduled-reports` | ADMIN | |
| PATCH | `/scheduled-reports/{id}/status` | ADMIN | ACTIVE / PAUSED |
| DELETE | `/scheduled-reports/{id}` | ADMIN | |

## Events (RabbitMQ `ucms.events`)
`identity.user-created`; `enrollment.student-enrolled`, `.student-waitlisted`,
`.waitlist-promoted`, `.course-dropped`, `.program-registered`;
`grading.assignment-published`, `.submission-received`, `.grade-posted`;
`analytics.report-generated`. Envelope: `{eventId, type, occurredAt, data}`.
