# UCMS — End-to-End Test Cases

**Date:** 2026-10-26
**Source**: derived directly from `api-inventory.md` (what exists) and `gap-analysis-report.md`
(what's wrong) — every case below either verifies a documented capability or specifically
tries to reproduce a specific Gap ID. Cases are **not** marked passed in this document.
This file is the test *plan*; `test-execution-report.md` is the companion document
recording what was actually run, against which environment, with real observed results.
Every case's `Actual Result` / `Status` / `Evidence` fields here are placeholders
(`Not yet executed`) until that execution pass happens — that is deliberate, not an
oversight, so there is exactly one place (`test-execution-report.md`) where "did this
actually pass" can ever be answered, instead of two documents that can drift out of sync.

**Legend** — Status: `Not yet executed` (this document's only value today) →
`Pass` / `Fail` / `Blocked` (only ever written in `test-execution-report.md`).

---

## Index

| ID | Scenario | Module | Role | Maps to Gap |
|---|---|---|---|---|
| TC-AUTH-001 | Successful login | identity-access | any | — |
| TC-AUTH-002 | Login rejected, wrong password | identity-access | any | — |
| TC-AUTH-003 | Login rejected, INACTIVE vs SUSPENDED message | identity-access | ADMIN (setup) | G-17 |
| TC-AUTH-004 | Expired/malformed JWT rejected | all 3 services | any | — |
| TC-AUTH-005 | No logout endpoint / session not invalidated | identity-access | any | G-11 |
| TC-USR-001 | Admin creates a Lecturer user | identity-access | ADMIN | — |
| TC-USR-002 | Non-admin blocked from creating a user | identity-access | LECTURER/STUDENT | — |
| TC-USR-003 | Duplicate email rejected | identity-access | ADMIN | — |
| TC-USR-004 | Paginated/filtered user list | identity-access | ADMIN | — |
| TC-USR-005 | Self-service profile name update | identity-access | any | — |
| TC-USR-006 | Role kind-collision regression | identity-access | ADMIN | G-03 / G-03b |
| TC-USR-007 | Role change doesn't sync scope assignment | identity-access | ADMIN | G-04 |
| TC-USR-008 | Permission override has no effect | identity-access + academic | ADMIN | G-09 |
| TC-REF-001 | Department code uniqueness enforced | reference-data | ADMIN | — |
| TC-REF-002 | Delete Faculty with dependent Department blocked | reference-data | ADMIN | — |
| TC-REF-003 | Delete Department with dependent Degree Program NOT blocked | reference-data + academic | ADMIN | G-05 |
| TC-REF-004 | Duplicate Grading Scale name allowed | reference-data | ADMIN | G-06 |
| TC-REF-005 | Any role can read reference data | reference-data | LECTURER/STUDENT | — |
| TC-REF-006 | Non-admin blocked from writing reference data | reference-data | LECTURER/STUDENT | — |
| TC-CAL-001 | Multiple ACTIVE academic years coexist | academic-structure | ADMIN | — |
| TC-CAL-002 | Delete ACTIVE academic year blocked | academic-structure | ADMIN | — |
| TC-CAL-003 | Write to a semester under an ARCHIVED year blocked | academic-structure | ADMIN | — |
| TC-CAL-004 | Non-admin blocked from creating academic year | academic-structure | LECTURER | — |
| TC-DEG-001 | Dept-scoped user creates program for own department | academic-structure | Dept Head | — |
| TC-DEG-002 | Dept-scoped user blocked for a different department | academic-structure | Dept Head | — |
| TC-DEG-003 | Curriculum read has no permission check | academic-structure | LECTURER (other dept) | G-07 |
| TC-DEG-004 | Delete program with existing courses blocked | academic-structure | ADMIN | — |
| TC-DEG-005 | Dept Head has no frontend surface for elevated authority | frontend + academic | Dept Head | G-01 |
| TC-CRS-001 | Lecturer self-service course creation | academic-structure | LECTURER | — |
| TC-CRS-002 | Lecturer blocked creating course for another lecturer | academic-structure | LECTURER | — |
| TC-CRS-003 | Admin creates course on behalf of another lecturer | academic-structure | ADMIN | — |
| TC-CRS-004 | No update/delete endpoint for Course Catalog | academic-structure | ADMIN | G-08 |
| TC-CRS-005 | Assign course to an ARCHIVED year blocked | academic-structure | LECTURER | — |
| TC-SEC-001 | Unauthenticated request rejected per-service | all 3 services | none | — |
| TC-SEC-002 | CORS preflight from unapproved origin rejected | gateway | none | — |
| TC-SEC-003 | Gateway forwards unauthenticated requests (no gate of its own) | gateway | none | G-18 |
| TC-FE-001 | STUDENT can navigate directly to lecturer-only route | frontend | STUDENT | G-14 |
| TC-FE-002 | Dead frontend method/route — static check | frontend | — | G-13 |
| TC-FE-003 | Users list pagination/search regression baseline | frontend | ADMIN | — |
| TC-FE-004 | Notification preferences round-trip, documented no-op | frontend + identity-access | any | G-15 |

---

## Authentication & Session

### TC-AUTH-001 — Successful login
- **Preconditions**: Seed admin account exists (`admin@ucms.local`)
- **User Role**: any
- **Frontend Steps**: Open `/login`, enter valid email/password, submit
- **API Request**: `POST /api/identity/auth/login {email,password}`
- **Expected Backend Behaviour**: BCrypt match succeeds; JWT issued with `sub`,`email`,`role`,`assignments`; a `user_sessions` row is written
- **Expected DB Result**: new row in `user_sessions` (hashed token)
- **Expected API Response**: 200, `{token,role,name}`
- **Expected Frontend Result**: redirect to `/app/dashboard`; `AuthService` calls `GET /users/me` to populate full profile
- **Negative/Edge Cases**: see TC-AUTH-002/003
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-AUTH-002 — Login rejected, wrong password
- **Preconditions**: valid account exists
- **User Role**: any
- **Frontend Steps**: Submit login form with correct email, wrong password
- **API Request**: `POST /api/identity/auth/login`
- **Expected Backend Behaviour**: BCrypt match fails
- **Expected DB Result**: no `user_sessions` row written
- **Expected API Response**: 401/403 with a generic message (not "wrong password" specifically, to avoid user enumeration — verify which)
- **Expected Frontend Result**: inline error banner on the login form, no redirect
- **Negative/Edge Cases**: unknown email entirely — confirm same response shape as wrong-password (no enumeration)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-AUTH-003 — Login rejected, INACTIVE vs SUSPENDED (Gap G-17)
- **Preconditions**: one INACTIVE user and one SUSPENDED user exist (create via admin status PATCH)
- **User Role**: ADMIN for setup, then the affected account for the login attempt
- **Frontend Steps**: attempt login as each
- **API Request**: `POST /api/identity/auth/login` for each account
- **Expected Backend Behaviour**: both rejected before password/JWT issuance
- **Expected DB Result**: no session row for either
- **Expected API Response**: 403 for both — **this test's purpose is to confirm whether the two response bodies are byte-identical** (per G-17) or have been differentiated
- **Expected Frontend Result**: generic "account not active" banner
- **Negative/Edge Cases**: n/a
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-AUTH-004 — Expired/malformed JWT rejected
- **Preconditions**: a valid JWT, manually expired (or truncated/corrupted)
- **User Role**: any
- **Frontend Steps**: n/a (API-level test); simulate by editing localStorage token then reloading
- **API Request**: any protected GET with the bad token, against all 3 backend services independently (e.g. `GET /api/identity/users/me`, `GET /api/reference/faculties`, `GET /api/academic/academic-years`)
- **Expected Backend Behaviour**: each service's own `JwtAuthFilter` independently rejects it — no shared/gateway-level check
- **Expected DB Result**: none
- **Expected API Response**: 401 from each of the 3 services
- **Expected Frontend Result**: `provideAppInitializer`'s `restoreSession()` catches this on app load and logs out silently; a token that expires **mid-session** is not specially handled (confirmed by frontend inventory — no global 401 interceptor) — this test should also confirm that mid-session behavior: a component's own generic error banner, no auto-redirect to `/login`
- **Negative/Edge Cases**: token with valid signature but wrong `JWT_SECRET`-signed structure (simulated cross-environment token)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-AUTH-005 — No logout endpoint / session not invalidated (Gap G-11)
- **Preconditions**: logged-in user with a valid token
- **User Role**: any
- **Frontend Steps**: click "Log Out" in the topbar
- **API Request**: none — `AuthService.logout()` makes no HTTP call, only clears localStorage
- **Expected Backend Behaviour**: the `user_sessions` row from login remains untouched; the JWT itself remains valid until natural expiry
- **Expected DB Result**: no change to `user_sessions`
- **Expected API Response**: n/a
- **Expected Frontend Result**: redirected to `/login`, localStorage cleared
- **Negative/Edge Cases**: **capture the pre-logout JWT separately, then after clicking "Log Out" replay a request with that same JWT directly against a backend service — confirm it still succeeds**, proving logout is client-side-only
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## User Management & RBAC

### TC-USR-001 — Admin creates a Lecturer user
- **Preconditions**: logged in as ADMIN
- **User Role**: ADMIN
- **Frontend Steps**: Users → Lecturers tab → "+ New User" → fill name/email, submit
- **API Request**: `POST /api/identity/users {name,email,role:"LECTURER",status:"ACTIVE"}`
- **Expected Backend Behaviour**: role resolved via `RoleRepository`; password auto-generated; a GLOBAL `role_scope_assignments` row created matching the role
- **Expected DB Result**: new `users` row, new `role_scope_assignments` row (GLOBAL scope)
- **Expected API Response**: 201, `CreatedUserResponse` incl. one-time `temporaryPassword`
- **Expected Frontend Result**: dialog shows the temp password once; new row appears in the Lecturers table
- **Negative/Edge Cases**: TC-USR-003 (duplicate email)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-002 — Non-admin blocked from creating a user
- **Preconditions**: logged in as LECTURER or STUDENT
- **User Role**: LECTURER / STUDENT
- **Frontend Steps**: n/a — no UI surface exists for non-admins; API-level test only
- **API Request**: `POST /api/identity/users` with a non-admin token
- **Expected Backend Behaviour**: `SecurityConfig`'s `hasRole("ADMIN")` on `/users/**` rejects before reaching the service
- **Expected DB Result**: none
- **Expected API Response**: 403
- **Expected Frontend Result**: n/a
- **Negative/Edge Cases**: n/a
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-003 — Duplicate email rejected
- **Preconditions**: a user with `taken@ucms.local` already exists
- **User Role**: ADMIN
- **Frontend Steps**: attempt to create another user with the same email
- **API Request**: `POST /api/identity/users {email:"taken@ucms.local",...}`
- **Expected Backend Behaviour**: `findByEmailAndDeletedAtIsNull` finds the existing row, throws `EmailAlreadyInUseException`
- **Expected DB Result**: no new row
- **Expected API Response**: 409
- **Expected Frontend Result**: dialog shows inline error, does not close
- **Negative/Edge Cases**: create a user, soft-delete it (if a path existed — it doesn't, see G-12), then try to reuse the email — **currently untestable since no delete endpoint exists**; flagged as a blocked case in the execution report
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-004 — Paginated/filtered user list
- **Preconditions**: more than one page of users exists (create ≥21 users, or use a small `size` to force pagination with fewer)
- **User Role**: ADMIN
- **Frontend Steps**: Users screen, filter by role tab, type in search box (debounced 300ms), use Previous/Next
- **API Request**: `GET /api/identity/users?role=LECTURER&status=ACTIVE&q=smith&page=0&size=20`
- **Expected Backend Behaviour**: `UserSpecifications.filter` builds the combined query; `size` clamped to [1,100]
- **Expected DB Result**: read-only
- **Expected API Response**: 200, `PagedResponse<UserResponse>` with correct `totalElements`
- **Expected Frontend Result**: "Showing X–Y of Z" updates correctly; Previous disabled on page 0
- **Negative/Edge Cases**: `size=0`, `size=500` (both should clamp, not error); `q` with no matches → empty-state row
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-005 — Self-service profile name update
- **Preconditions**: logged in as any role
- **User Role**: any
- **Frontend Steps**: Profile → Name form → change name → Save
- **API Request**: `PATCH /api/identity/users/me {name}`
- **Expected Backend Behaviour**: `findActiveOrThrow(callerId)`; note the caller's id is taken from the JWT, not a path param — there is no separate "is this really you" check beyond the URL pattern itself (per the identity-access inventory)
- **Expected DB Result**: `users.name` updated, `updatedBy = own id`
- **Expected API Response**: 200, `ProfileResponse`
- **Expected Frontend Result**: inline "Saved" text
- **Negative/Edge Cases**: attempt the same call with another user's id path-style via raw `curl` against a hypothetical `/users/{otherId}` — **does not apply, since the endpoint is fixed at `/users/me` with no id param**, confirming the self-service boundary is structurally enforced here (unlike a naive "check `id == me`" pattern that could be bypassed by a bad comparison)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-006 — Role kind-collision regression (Gap G-03 / G-03b)
- **Preconditions**: seeded "Lecturer" role exists with `kind=LECTURER`
- **User Role**: ADMIN
- **Frontend Steps**: n/a — API-level reproduction
- **API Request**: `POST /api/identity/roles {name:"Department Head",kind:"LECTURER"}`, then `POST /api/identity/users {role:"LECTURER",...}`
- **Expected Backend Behaviour (on the unfixed `develop` branch)**: role creation succeeds with no uniqueness check (confirms G-03's root cause is still open); the subsequent user-creation role lookup via `findByKindAndDeletedAtIsNull` throws `NonUniqueResultException` → unhandled 500/403, reproducing the original production incident
- **Expected Backend Behaviour (once PR #17 is merged)**: role creation still succeeds (uniqueness still unaddressed at creation time — see G-03's recommended follow-up), but user creation now resolves deterministically via `findFirstByKindAndDeletedAtIsNullOrderByCreatedAtAsc` and succeeds
- **Expected DB Result**: two `roles` rows sharing `kind=LECTURER`; one new `users` row on success
- **Expected API Response**: 500/403 (unfixed) vs 201 (fixed)
- **Expected Frontend Result**: "+ New User" dialog shows a generic error (unfixed) vs succeeds (fixed)
- **Negative/Edge Cases**: this is itself the edge case for G-03 — no further negative variant needed
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-007 — Role change doesn't sync scope assignment (Gap G-04)
- **Preconditions**: a user exists with role LECTURER and a GLOBAL scope assignment pointing at the Lecturer role
- **User Role**: ADMIN
- **Frontend Steps**: Users → change the user's role via the role-change dialog to STUDENT
- **API Request**: `PUT /api/identity/users/{id}/role {role:"STUDENT"}`, then `GET /api/identity/users/{id}/assignments`
- **Expected Backend Behaviour**: `users.role_id` updates; `role_scope_assignments` rows for this user are left untouched
- **Expected DB Result**: `users.role_id` = STUDENT role id; the existing GLOBAL assignment's `role_id` **still points at the old LECTURER role** — this test's purpose is to confirm that drift
- **Expected API Response**: 200 on the role change; the follow-up `GET .../assignments` returns the stale role id
- **Expected Frontend Result**: role-change dialog shows success; no UI anywhere surfaces the now-inconsistent assignment (Roles & Permissions screen is a "Coming soon" placeholder)
- **Negative/Edge Cases**: n/a — this test exists specifically to demonstrate the drift, not to find a boundary
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-USR-008 — Permission override has no effect (Gap G-09)
- **Preconditions**: a user with a DEPARTMENT-scoped assignment to some department D; a known permission in that assignment's role baseline currently grants, e.g., `Courses:Create`
- **User Role**: ADMIN (to set the override), then the affected user (to test its effect)
- **Frontend Steps**: n/a — no UI exists for assignment overrides (Roles & Permissions is a placeholder); API-level only
- **API Request**: `PATCH /api/identity/assignments/{assignmentId}/overrides {overrides:{"<Courses:Create permissionId>":false}}`, then as that user, `POST /api/academic/courses` for department D
- **Expected Backend Behaviour**: the override PATCH succeeds and persists; the subsequent course-creation call still succeeds despite the override claiming to revoke it, because `PermissionResolver.effectiveGrant()` never consults `AssignmentClaim.overrides`
- **Expected DB Result**: `assignment_permission_overrides` row written with `granted=false`; course still created
- **Expected API Response**: 200 on the override PATCH; 201 on the course creation (should have been 403 if overrides were honored)
- **Expected Frontend Result**: n/a
- **Negative/Edge Cases**: the inverse — using an override to **grant** something the baseline role would otherwise deny — should also silently fail to take effect
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Reference Data

### TC-REF-001 — Department code uniqueness enforced
- **Preconditions**: a Department with code `DOCS` exists
- **User Role**: ADMIN
- **Frontend Steps**: Reference Data → Departments tab → create new with the same code
- **API Request**: `POST /api/reference/departments {name,code:"DOCS",facultyId,status}`
- **Expected Backend Behaviour**: `existsByCodeAndDeletedAtIsNull` finds the match, throws `CodeAlreadyInUseException`
- **Expected DB Result**: no new row
- **Expected API Response**: 409
- **Expected Frontend Result**: inline dialog error
- **Negative/Edge Cases**: same code under a **different** faculty — should still conflict, since Department code uniqueness is global, not per-faculty (confirms that specific design fact, not a bug)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-REF-002 — Delete Faculty with dependent Department blocked
- **Preconditions**: a Faculty with at least one active Department
- **User Role**: ADMIN
- **Frontend Steps**: Reference Data → Faculties tab → delete the faculty
- **API Request**: `DELETE /api/reference/faculties/{id}`
- **Expected Backend Behaviour**: `existsByFacultyIdAndDeletedAtIsNull` is true → `HasDependentsException`
- **Expected DB Result**: no change
- **Expected API Response**: 409
- **Expected Frontend Result**: toast/error, row remains
- **Negative/Edge Cases**: delete the dependent Department first, then retry — should now succeed (204)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-REF-003 — Delete Department with dependent Degree Program NOT blocked (Gap G-05)
- **Preconditions**: a Department `D` with a Degree Program in academic-structure-service referencing `D`'s id as `departmentId`
- **User Role**: ADMIN
- **Frontend Steps**: Reference Data → Departments tab → delete `D`
- **API Request**: `DELETE /api/reference/departments/{D.id}`, then `GET /api/academic/degree-programs?departmentId={D.id}`
- **Expected Backend Behaviour**: delete succeeds unconditionally (reference-data-service has no awareness of academic-structure-service's reference); the degree program's `departmentId` now points at a soft-deleted department
- **Expected DB Result**: `departments.deleted_at` set; the degree program row is unchanged and still resolvable by id
- **Expected API Response**: 204 on the delete (should arguably have been 409, per G-05); the follow-up GET still returns the orphaned program
- **Expected Frontend Result**: Reference Data screen shows the department gone; Program Management screen still shows the program, now pointing at a department that no longer appears anywhere in the Reference Data UI — a dangling reference visible to an admin who looks in both places
- **Negative/Edge Cases**: attempt `canOnDepartment` checks against the now-deleted department id — confirm whether `PermissionResolver` still resolves them (it does, since it has no live FK check either)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-REF-004 — Duplicate Grading Scale name allowed (Gap G-06)
- **Preconditions**: none
- **User Role**: ADMIN
- **Frontend Steps**: Reference Data → Grading Scales tab → create two with identical name/bands
- **API Request**: `POST /api/reference/grading-scales {name:"Standard",bands:[...]}`, twice, identical body
- **Expected Backend Behaviour**: no `existsBy*` check exists on `GradingScaleRepository`; both succeed
- **Expected DB Result**: two indistinguishable (by content) rows with different ids
- **Expected API Response**: 201 both times
- **Expected Frontend Result**: both appear in the list, visually identical except by inspecting network responses for `id`
- **Negative/Edge Cases**: repeat for Course Category — same result expected
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-REF-005 — Any role can read reference data
- **Preconditions**: logged in as LECTURER or STUDENT
- **User Role**: LECTURER / STUDENT
- **Frontend Steps**: n/a (Reference Data nav item is `adminGuard`-only — not reachable via UI for these roles); API-level test
- **API Request**: `GET /api/reference/faculties` with a non-admin token
- **Expected Backend Behaviour**: `SecurityConfig`'s `authenticated()` on GET allows any authenticated role
- **Expected API Response**: 200
- **Expected Frontend Result**: n/a — confirms the API is more permissive than the frontend's nav/guard exposes, which is expected and not a gap (write operations are the ones that matter for this screen being admin-only)
- **Negative/Edge Cases**: n/a
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-REF-006 — Non-admin blocked from writing reference data
- **Preconditions**: logged in as LECTURER
- **User Role**: LECTURER
- **Frontend Steps**: n/a — API-level test
- **API Request**: `POST /api/reference/faculties` with a LECTURER token
- **Expected Backend Behaviour**: `hasRole("ADMIN")` rejects before the service layer
- **Expected API Response**: 403
- **Negative/Edge Cases**: repeat for PUT and DELETE on all 4 entity types (8 additional sub-cases) — grouped here rather than as separate top-level IDs since they share one expected outcome
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Academic Calendar

### TC-CAL-001 — Multiple ACTIVE academic years coexist
- **Preconditions**: one Academic Year already ACTIVE
- **User Role**: ADMIN
- **Frontend Steps**: Academic Calendar → create a new year with status ACTIVE
- **API Request**: `POST /api/academic/academic-years {name,status:"ACTIVE",...}`
- **Expected Backend Behaviour**: `createYear` does not touch the existing ACTIVE year
- **Expected DB Result**: two rows in `academic_years` both with `status='ACTIVE'`
- **Expected API Response**: 201
- **Expected Frontend Result**: both years show an "Active" badge simultaneously in the list
- **Negative/Edge Cases**: same test via `PATCH .../activate` on an existing UPCOMING year instead of create
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CAL-002 — Delete ACTIVE academic year blocked
- **Preconditions**: an ACTIVE academic year
- **User Role**: ADMIN
- **Frontend Steps**: attempt to delete it from the Academic Calendar list
- **API Request**: `DELETE /api/academic/academic-years/{id}`
- **Expected Backend Behaviour**: `deleteYear` checks `status==ACTIVE` → `HasDependentsException`
- **Expected API Response**: 409
- **Negative/Edge Cases**: archive it first (or confirm it has no dependents and isn't ACTIVE), then delete — should succeed
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CAL-003 — Write to a semester under an ARCHIVED year blocked
- **Preconditions**: an ARCHIVED academic year with at least one semester
- **User Role**: ADMIN
- **Frontend Steps**: attempt to edit that semester
- **API Request**: `PUT /api/academic/academic-years/{yearId}/semesters/{semesterId}`
- **Expected Backend Behaviour**: `ArchivedYearException` thrown before the update applies
- **Expected API Response**: 409
- **Negative/Edge Cases**: the archived year's **delete**-semester path has no archived-check at all per the inventory — confirm whether deleting a semester under an archived year succeeds (expected: yes, unlike update, since only the dependents check applies to delete)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CAL-004 — Non-admin blocked from creating academic year
- **Preconditions**: logged in as LECTURER
- **User Role**: LECTURER
- **Frontend Steps**: n/a — Academic Calendar nav is `adminGuard`-only
- **API Request**: `POST /api/academic/academic-years` with a LECTURER token
- **Expected Backend Behaviour**: falls to `SecurityConfig`'s `anyRequest().hasRole("ADMIN")` catch-all
- **Expected API Response**: 403
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Degree Programs & Curriculum

### TC-DEG-001 — Dept-scoped user creates program for own department
- **Preconditions**: a user with a DEPARTMENT-scoped assignment to department `D`
- **User Role**: "Department Head"-shaped custom role
- **Frontend Steps**: n/a — no screen exposes this distinction on the frontend today (this is exactly Gap G-01); API-level test for the backend half of the gap
- **API Request**: `POST /api/academic/degree-programs {departmentId:"D",...}`
- **Expected Backend Behaviour**: `canOnDepartment(D,"Degree Programs","Create")` resolves true
- **Expected API Response**: 201
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-DEG-002 — Dept-scoped user blocked for a different department
- **Preconditions**: same user as above, department `E` they have no assignment for
- **User Role**: "Department Head"-shaped custom role
- **Frontend Steps**: n/a
- **API Request**: `POST /api/academic/degree-programs {departmentId:"E",...}`
- **Expected Backend Behaviour**: `canOnDepartment(E,...)` resolves false → `AccessDeniedException`
- **Expected API Response**: 403
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-DEG-003 — Curriculum read has no permission check (Gap G-07)
- **Preconditions**: a Degree Program under department `E`; caller has a DEPARTMENT-scoped assignment only to department `D` (no grant on `E`)
- **User Role**: LECTURER (department-scoped to D)
- **Frontend Steps**: n/a — no screen currently lets a Lecturer browse another department's curriculum directly, but the API has no gate here regardless of what the UI exposes
- **API Request**: `GET /api/academic/degree-programs/{E-program-id}/curriculum`
- **Expected Backend Behaviour**: `listCurriculum` performs no `PermissionResolver` check
- **Expected API Response**: 200 with full curriculum data for a department the caller has no grant on — this is the gap being confirmed, not a hoped-for failure
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-DEG-004 — Delete program with existing courses blocked
- **Preconditions**: a Degree Program with at least one Course
- **User Role**: ADMIN
- **Frontend Steps**: Program Management → attempt delete
- **API Request**: `DELETE /api/academic/degree-programs/{id}`
- **Expected Backend Behaviour**: `existsByDegreeProgramIdAndDeletedAtIsNull` on `CourseRepository` is true → `HasDependentsException`
- **Expected API Response**: 409
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-DEG-005 — Dept Head has no frontend surface for elevated authority (Gap G-01)
- **Preconditions**: a user account with a DEPARTMENT-scoped "Department Head" role assignment (flat `role` field = LECTURER, per the kind-collision precedent)
- **User Role**: "Department Head"-shaped custom role
- **Frontend Steps**: log in; observe the sidebar nav and dashboard
- **API Request**: none — pure UI inspection
- **Expected Backend Behaviour**: n/a
- **Expected Frontend Result**: `adminGuard`/`lecturerGuard` both evaluate against `ProfileResponse.role === 'LECTURER'`; the user sees exactly the same nav as a plain Lecturer — "My Courses", "Reference Degree Details" — with **no** Program Management or admin-side Degree Programs screen, despite the backend (per TC-DEG-001) granting them department-wide create/edit/delete authority on Degree Programs and the ability to act on behalf of any lecturer in their department
- **Negative/Edge Cases**: confirm the same account, hitting the API directly (bypassing the UI) with the exact calls from TC-DEG-001, still succeeds — proving the authority exists and only the UI path to it is missing
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Courses & Course Catalog

### TC-CRS-001 — Lecturer self-service course creation
- **Preconditions**: logged in as LECTURER, a Degree Program in their own department exists
- **User Role**: LECTURER
- **Frontend Steps**: My Courses → "+ Create Course" → fill form → Save as Draft
- **API Request**: `POST /api/academic/courses {degreeProgramId,...}` (no `lecturerId` in the request — defaults to caller)
- **Expected Backend Behaviour**: `actingForSelf=true` → `canOnDepartment(...,"Create")` checked against the caller's own department scope
- **Expected API Response**: 201, `lecturerId` = caller's id
- **Expected Frontend Result**: new course appears in "My Courses"
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CRS-002 — Lecturer blocked creating course for another lecturer
- **Preconditions**: two LECTURER accounts, L1 and L2, L1 has no DEPARTMENT-scoped grant beyond their own baseline
- **User Role**: LECTURER (L1)
- **Frontend Steps**: n/a — no UI exposes an "on behalf of" lecturer field for plain Lecturers; API-level test
- **API Request**: `POST /api/academic/courses {lecturerId:"L2",...}` as L1
- **Expected Backend Behaviour**: `actingForSelf=false`; `CurrentRole.is("ADMIN")` is false and L1 has no DEPARTMENT-scoped grant → `AccessDeniedException`
- **Expected API Response**: 403
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CRS-003 — Admin creates course on behalf of another lecturer
- **Preconditions**: logged in as ADMIN, a LECTURER account L2 exists
- **User Role**: ADMIN
- **Frontend Steps**: n/a — Admin's Create Course screen is the same component; confirm whether the admin flow actually exposes a lecturer picker (verify during execution — not confirmed in the static inventory)
- **API Request**: `POST /api/academic/courses {lecturerId:"L2",...}` as ADMIN
- **Expected Backend Behaviour**: `CurrentRole.is("ADMIN")` is true → bypasses the department-scope check entirely
- **Expected API Response**: 201, `lecturerId="L2"`
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CRS-004 — No update/delete endpoint for Course Catalog (Gap G-08)
- **Preconditions**: a Course Catalog entry exists
- **User Role**: ADMIN
- **Frontend Steps**: n/a — no edit/delete UI exists for Course Catalog entries, consistent with the backend gap
- **API Request**: `PUT /api/academic/course-catalog/{id}` and `DELETE /api/academic/course-catalog/{id}`
- **Expected Backend Behaviour**: no controller mapping exists for either
- **Expected API Response**: 404 (no matching route) for both
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-CRS-005 — Assign course to an ARCHIVED year blocked
- **Preconditions**: an ARCHIVED academic year, a Course not yet assigned to it
- **User Role**: LECTURER (own course) or ADMIN
- **Frontend Steps**: My Courses → Manage Years → attempt to assign to the archived year (if it's even selectable in the dropdown — verify during execution)
- **API Request**: `POST /api/academic/courses/{id}/academic-years {academicYearId:"<archived>",...}`
- **Expected Backend Behaviour**: `ArchivedYearException` thrown
- **Expected API Response**: 409
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Gateway / Cross-cutting Security

### TC-SEC-001 — Unauthenticated request rejected per-service
- **Preconditions**: none
- **User Role**: none (no `Authorization` header)
- **Frontend Steps**: n/a
- **API Request**: `GET /api/identity/users`, `GET /api/reference/faculties`, `GET /api/academic/academic-years` — no token on any
- **Expected Backend Behaviour**: each service's own `JwtAuthFilter`/`SecurityConfig` rejects independently
- **Expected API Response**: 401/403 from all three (confirm exact code per service — may differ since each has its own `SecurityConfig`)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-SEC-002 — CORS preflight from an unapproved origin rejected
- **Preconditions**: none
- **User Role**: none
- **Frontend Steps**: n/a — simulate via `curl -H "Origin: https://evil.example"` with an `OPTIONS` preflight against the gateway
- **API Request**: `OPTIONS /api/identity/users` with `Origin: https://evil.example`
- **Expected Backend Behaviour**: gateway's `globalcors` config only allows the configured `CORS_ALLOWED_ORIGIN` value(s)
- **Expected API Response**: no `Access-Control-Allow-Origin` header matching the bad origin in the response
- **Negative/Edge Cases**: repeat with the real approved origin(s) (`https://esd.azhosting.fun`, `http://169.58.12.239:8091`) — should succeed
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-SEC-003 — Gateway forwards unauthenticated requests with no gate of its own (Gap G-18)
- **Preconditions**: none
- **User Role**: none
- **Frontend Steps**: n/a
- **API Request**: send a request directly to the gateway's port with no `Authorization` header, and inspect — via service logs if accessible — whether the request actually reaches the backend service's filter (vs. being rejected at the gateway itself)
- **Expected Backend Behaviour**: the gateway performs no auth check of its own (confirmed statically — only one Java file exists in the module); the 401/403 seen in TC-SEC-001 is produced by the **backend service**, not the gateway
- **Expected API Response**: same 401/403 as TC-SEC-001, but this test's purpose is confirming *where* that rejection happens (this matters for G-18: a future service that forgets to add its own filter would not be caught here)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## Frontend-specific

### TC-FE-001 — STUDENT can navigate directly to lecturer-only route (Gap G-14)
- **Preconditions**: logged in as STUDENT
- **User Role**: STUDENT
- **Frontend Steps**: manually type `/app/reference-degree-details` into the browser address bar
- **API Request**: whatever that component's `ngOnInit` triggers (`GET /api/reference/departments`, `GET /api/academic/course-catalog`, etc.)
- **Expected Backend Behaviour**: all underlying endpoints are `authenticated()`-gated, so a STUDENT's calls succeed at the API level too
- **Expected Frontend Result**: the route's guard is `authGuard` only (not role-specific) — the page renders fully for a STUDENT despite no nav link ever pointing there for that role
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-FE-002 — Dead frontend method/route — static check (Gap G-13)
- **Preconditions**: none
- **User Role**: n/a
- **Frontend Steps**: n/a — static code check, not a runtime UI test
- **API Request**: n/a
- **Expected Backend Behaviour**: n/a
- **Expected Frontend Result**: `grep -rn "activateAcademicYear(" web/src/app` finds only the method's own definition in `academic-structure.service.ts`, no call sites; `grep -rn 'routerLink="/app/degree-programs"' web/src/app` finds zero matches
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-FE-003 — Users list pagination/search regression baseline
- **Preconditions**: ≥21 users across mixed roles/statuses
- **User Role**: ADMIN
- **Frontend Steps**: Users screen → type a search term, switch status filter, page forward/back across role tabs
- **API Request**: successive `GET /api/identity/users?...` calls as filters change
- **Expected Backend Behaviour**: server-side filter/pagination via `UserSpecifications`
- **Expected Frontend Result**: debounce (300ms) prevents a request per keystroke; switching tabs resets to page 0 independently per role; "Showing X–Y of Z" always matches the response's `totalElements`
- **Negative/Edge Cases**: rapid tab-switching mid-debounce — confirm no stale response overwrites a newer one (the inventory notes a request-id race guard exists on several *other* screens; confirm whether Users list has the same protection or could show stale results)
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

### TC-FE-004 — Notification preferences round-trip, documented no-op (Gap G-15)
- **Preconditions**: logged in as any role
- **User Role**: any
- **Frontend Steps**: Profile → Notifications section → toggle all 4 preferences → Save
- **API Request**: `PATCH /api/identity/users/me/notification-preferences {email,assignments,grades,registrations}`
- **Expected Backend Behaviour**: persists to the 4 `notify_*` columns; no `@Valid` constraints on the DTO (confirm whether sending malformed/missing fields still succeeds, e.g. `{}`)
- **Expected DB Result**: `users` row's 4 boolean columns updated
- **Expected API Response**: 200
- **Expected Frontend Result**: "Saved" text appears; the in-app disclaimer text ("Not sent yet...") remains visible, confirming the UI is honest about this being a no-op today
- **Actual Result**: Not yet executed · **Status**: Not yet executed · **Evidence**: —

---

## What this document does not cover

Actual execution — which of the above were run, against which environment (local dev
stack vs. the live server), what was actually observed, and which cases were blocked
and why — is tracked exclusively in `test-execution-report.md`. Do not treat any case
above as "passing" based on this document alone.
