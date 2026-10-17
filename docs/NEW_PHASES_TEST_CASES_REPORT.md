# UCMS — Test Cases Report for New Architecture Phases

**Status:** Test plan only. None of these phases are implemented yet — this document is the Phase 5/6 ("Validate" / "Regression Testing") companion to `docs/SYSTEM_ARCHITECTURE_REPORT.md`'s Phase 4 ("Implement"). Write/run these as each phase lands, in the order given in that report's §16 roadmap.

**Conventions**
- ID format: `TC-P<phase>-<seq>`. Phase numbers match `SYSTEM_ARCHITECTURE_REPORT.md` §16 exactly (P1 = Departments, P2 = RBAC tables, P3 = Authorization resolution, P4 = Courses UI, P5 = Course Modules, P6 = Grading Scale move, P7 = Academic Year, P8 = Roles & Permissions UI, P9 = Event-driven propagation).
- **Type**: Unit / Integration / Security / Regression / E2E / Live.
- **Priority**: P0 (blocks the phase, must pass before merge) / P1 (should pass before merge) / P2 (nice-to-have, can follow up).
- "Live" tests must be run against the real Postgres instance (local SSH-tunnel dev *and* the Contabo server, both share one DB per this project's established practice) — not just the H2 test profile, per the project's own prior incident where an H2-only bug (the `timestamptz`/`LocalDateTime` JPA auditing mismatch) stayed hidden until live-tested.
- Every **Security** test case models a direct API call with a valid, correctly-signed JWT that bypasses the UI entirely (e.g. raw `curl`/Postman) — per `SYSTEM_ARCHITECTURE_REPORT.md` §12.4, hiding a button is not sufched authorization.

---

## Phase 1 — Departments

### P1.A — Schema & migration

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P1-001 | Integration | P0 | Apply `departments` migration to a fresh schema | Table exists with `id, faculty_id FK, name, code UK, status, ` + full audit block |
| TC-P1-002 | Integration | P0 | Insert a department with a `faculty_id` that doesn't exist in `faculties` | Rejected — FK violation (same-schema FK, unlike cross-schema refs) |
| TC-P1-003 | Integration | P0 | Insert two departments with the same `code` | Rejected — unique constraint violation |
| TC-P1-004 | Live | P0 | Run the one-time data migration: move `('Department of Mathematics','DOM',...)` out of `faculties` into `departments` under the chosen parent faculty | `faculties` no longer contains a `DOM` row; `departments` contains exactly one `DOM` row with the correct `faculty_id` |
| TC-P1-005 | Regression | P0 | Re-run `reference-data-service`'s existing 9 tests after the migration | All 9 still pass (none of them assert `DOM` is a faculty row by exact count — verify this assumption first, fix the test fixture if it does) |
| TC-P1-006 | Integration | P1 | `GET /faculties` after migration | Response no longer includes the `DOM` row; faculty count drops by 1 from the pre-migration seed |
| TC-P1-007 | Integration | P0 | `GET /departments?facultyId=<FOC>` | Returns only departments under Faculty of Computing |
| TC-P1-008 | Integration | P1 | `GET /departments` with no filter, as any authenticated role | 200, full list — reads stay open to any authenticated user, per the existing Reference Data pattern |
| TC-P1-009 | Security | P0 | `POST /departments` as STUDENT role | 403 |
| TC-P1-010 | Security | P0 | `POST /departments` as LECTURER role | 403 (write stays ADMIN-only until Phase 2/3 introduces Dept-Head-scoped writes) |
| TC-P1-011 | Integration | P0 | `POST /departments` as ADMIN with duplicate `code` | 409 Conflict, matching the existing `DuplicateEntityException` pattern used elsewhere in this codebase |

### P1.B — `degree_programs.faculty_id` → `department_id` rename

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P1-012 | Integration | P0 | Backfill: map each of the 4 seeded programs (BSc-CS/SE/DS, Dip-IT) to a real `department_id` before the `NOT NULL`/rename migration runs | All 4 programs have a non-null `department_id` after backfill, before the column is dropped |
| TC-P1-013 | Integration | P0 | Apply the rename migration (`faculty_id` → `department_id`) | Column no longer named `faculty_id`; existing FK/index references updated accordingly (no dangling index named `idx_..._faculty_id`) |
| TC-P1-014 | Regression | P0 | Re-run `academic-structure-service`'s existing 21 tests | All 21 still pass — note: several directly construct `DegreeProgram` and call `setFacultyId(...)`; these **must** be updated to `setDepartmentId(...)` as part of this phase, not left broken |
| TC-P1-015 | Integration | P0 | `POST /degree-programs` with the old field name `facultyId` in the request body (simulating a stale client) | Rejected with 400 (unknown field) — confirms the DTO was actually renamed, not just aliased |
| TC-P1-016 | Integration | P0 | `POST /degree-programs` with `departmentId` pointing at a real department | 201, response echoes `departmentId` |
| TC-P1-017 | E2E | P1 | Angular Degree Programs page: create-program dialog's "Faculty" dropdown becomes a "Department" dropdown, sourced from `GET /departments` | Dropdown lists departments, not faculties; selecting one and submitting succeeds |
| TC-P1-018 | Live | P0 | Run TC-P1-012 through TC-P1-013 against the real Contabo-hosted Postgres (not just local H2), on a copy/staging pass before touching the live `academic` schema | Migration completes without error; `SELECT department_id FROM academic.degree_programs` returns 4 non-null rows matching the backfill mapping |

### P1.C — `course_catalog.owning_department_id`

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P1-019 | Integration | P0 | Backfill all 5 seeded catalog subjects with an `owning_department_id` before the `NOT NULL` constraint is added | All 5 rows populated |
| TC-P1-020 | Integration | P0 | Attempt `POST /course-catalog` (or whatever create path exists) without `owningDepartmentId` after the constraint lands | 400 — missing required field |
| TC-P1-021 | Integration | P1 | Create a catalog subject owned by Department of Mathematics, then add it to BSc-CS's curriculum (a Computing-department program) via `POST /degree-programs/{id}/curriculum` | Succeeds — confirms a program can legitimately curriculum-include a course owned by a different department (§7.3 of the architecture report), i.e. this is *not* rejected |

---

## Phase 2 — RBAC scope-assignment tables

### P2.A — Schema

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P2-001 | Integration | P0 | Insert a `role_scope_assignments` row with `scope_type='GLOBAL'` and a non-null `scope_id` | Rejected — violates `CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL))` |
| TC-P2-002 | Integration | P0 | Insert a row with `scope_type='DEPARTMENT'` and a null `scope_id` | Rejected — same CHECK, opposite direction |
| TC-P2-003 | Integration | P0 | Insert a row with `scope_type='GLOBAL'` and null `scope_id` | Accepted |
| TC-P2-004 | Integration | P0 | Insert a row with `scope_type='COURSE_MODULE'` and a valid `scope_id` | Accepted |
| TC-P2-005 | Integration | P0 | Insert an `assignment_permission_overrides` row referencing a non-existent `assignment_id` | Rejected — FK violation |
| TC-P2-006 | Integration | P0 | Insert two override rows for the same `(assignment_id, permission_id)` | Second insert rejected — composite PK violation (must be an `UPDATE`, not a second `INSERT`) |
| TC-P2-007 | Integration | P1 | Soft-delete (`deleted_at`) a `role_scope_assignments` row, then re-query via the repository's standard `...AndDeletedAtIsNull` finder | Revoked assignment no longer returned |

### P2.B — Backfill of existing users

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P2-008 | Live | P0 | Before this migration, record the exact set of users and their `RoleKind` (ADMIN/LECTURER/STUDENT) in the live DB | Baseline snapshot captured (e.g. the 3 known seeded users: Seed Administrator, Dr. Silva, Nimal Perera) |
| TC-P2-009 | Integration | P0 | Run the backfill migration that creates one `GLOBAL`-scope `role_scope_assignments` row per existing user, bound to their current role | Every pre-existing user now has exactly one `role_scope_assignments` row, `scope_type='GLOBAL'` |
| TC-P2-010 | Regression | P0 | After backfill, every endpoint that worked before this phase (login, profile, users list, reference data reads/writes, academic structure reads/writes) still behaves identically | No behavior change is observable yet — Phase 2 only adds data, Phase 3 is what changes enforcement |
| TC-P2-011 | Live | P0 | Confirm backfill count matches TC-P2-008's baseline exactly (no extra, no missing) | Row count in `role_scope_assignments` == user count at migration time |

### P2.C — New endpoints

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P2-012 | Integration | P0 | `GET /roles` as ADMIN | 200, includes baseline `role_permissions` grants per role |
| TC-P2-013 | Security | P0 | `GET /roles` as STUDENT | 403 (or 200-read-only if the design intends open reads — **resolve this open question before writing the real test**; default to 403 until specified, since role definitions are sensitive) |
| TC-P2-014 | Integration | P0 | `POST /roles` with `{name:"Department Head", kind:"LECTURER"}` as ADMIN | 201, new `roles` row created, `kind` persisted as given |
| TC-P2-015 | Integration | P0 | `PATCH /roles/{id}/permissions` toggling one `(module, actionKey)` grant | `role_permissions` row updated; `version` incremented (optimistic lock) |
| TC-P2-016 | Integration | P0 | `POST /users/{id}/assignments` with `{roleId, scopeType:"DEPARTMENT", scopeId:<dept>}` | 201, new `role_scope_assignments` row; `GET /users/{id}/assignments` immediately reflects it |
| TC-P2-017 | Integration | P0 | `PATCH /assignments/{id}/overrides` setting `{permissionId, granted:false}` | New/updated `assignment_permission_overrides` row |
| TC-P2-018 | Integration | P0 | `DELETE /assignments/{id}` | 204; assignment's `deleted_at` set, not hard-deleted |
| TC-P2-019 | Security | P0 | All of TC-P2-014 through TC-P2-018 attempted as LECTURER (non-admin) | 403 on every one |
| TC-P2-020 | Security | P0 | **Privilege-escalation guard**: a Department Head (not global ADMIN) attempts `POST /users/{id}/assignments` to grant themselves a `GLOBAL` scope assignment | Rejected — per §15 of the architecture report, a user cannot grant a scope broader than their own highest-held scope |
| TC-P2-021 | Security | P0 | Same as TC-P2-020 but the Dept Head grants a `DEPARTMENT` assignment for a *different* department than their own | Rejected |
| TC-P2-022 | Integration | P1 | Optimistic-lock race: two concurrent `PATCH /roles/{id}/permissions` calls with the same stale `version` | Second one fails with 409 (`ObjectOptimisticLockingFailureException`), matching the existing pattern used across all 3 built services |

---

## Phase 3 — Authorization resolution (the critical phase)

### P3.A — Unit tests on the resolution algorithm itself (§5.3), isolated from HTTP

These must exist as pure unit tests against the resolver class/function directly — not only reachable through an HTTP round-trip — per the architecture report's explicit call-out that this is the highest-risk piece of logic in the whole proposal.

| ID | Scenario | Expected |
|---|---|---|
| TC-P3-001 | User has one `GLOBAL` assignment, role's baseline grants the permission | Allowed |
| TC-P3-002 | User has one `GLOBAL` assignment, role's baseline does **not** grant the permission | Denied |
| TC-P3-003 | User has a `DEPARTMENT`-scoped assignment matching the resource's resolved department, baseline grants it | Allowed |
| TC-P3-004 | User has a `DEPARTMENT`-scoped assignment for a **different** department than the resource's | Denied |
| TC-P3-005 | User has a `COURSE`-scoped assignment, resource is a `COURSE_MODULE` *under* that course | Allowed (ancestor resolution: course scope covers its modules) |
| TC-P3-006 | User has a `COURSE_MODULE`-scoped assignment, resource is the *parent* `COURSE` (not the module itself) | Denied — scope does not flow upward |
| TC-P3-007 | User has a `COURSE_MODULE` assignment with baseline role granting the permission, **and** an `assignment_permission_overrides` row denying it | Denied — override wins for that assignment |
| TC-P3-008 | User has a `COURSE_MODULE` assignment with baseline role **not** granting the permission, and an override row granting it | Allowed — override widens |
| TC-P3-009 | User has **two** assignments on the same resource: one denies via override, the other (different assignment, e.g. also a `DEPARTMENT`-level one) grants via baseline | Allowed — "OR across matching assignments," per §5.3's explicit design choice that one assignment's narrowing override must not silently block a different, broader grant |
| TC-P3-010 | User has zero matching assignments of any kind for the resource | Denied |
| TC-P3-011 | User has a `DEGREE_PROGRAM`-scoped assignment, resource is a `COURSE` whose catalog subject is curriculum-linked into that program **but owned by a different department** | Allowed for program-curriculum actions, but must be re-verified against TC-P3-012 for catalog-subject-editing actions specifically |
| TC-P3-012 | Same setup as TC-P3-011, but the action is "edit the catalog subject itself" (not the program's curriculum membership) | Denied — per §7.3, catalog-subject edit rights resolve against the subject's `owning_department_id`, not the querying program's department |
| TC-P3-013 | Soft-deleted (`deleted_at` not null) assignment that would otherwise match | Denied — revoked assignments are excluded |
| TC-P3-014 | Role's `role_permissions` grant exists but `granted=false` | Denied (an explicit `false` row is not "no grant," it's a revoke — resolver must not treat these identically if the schema allows both "no row" and "row with granted=false") |

### P3.B — Integration: replacing hardcoded `hasRole` in each built service

| ID | Service | Steps | Expected Result |
|---|---|---|---|
| TC-P3-015 | identity-access-service | `/users/**`, `/roles/**` endpoints, previously `hasRole("ADMIN")` | Now driven by the `Users`/`Roles` permission modules; an ADMIN-kind user whose role's baseline has been (hypothetically) stripped of the `Users` permission is now correctly denied — this is the key behavioral difference from today, where `kind=ADMIN` alone was sufficient |
| TC-P3-016 | reference-data-service | `POST /faculties`, `/departments`, `/course-categories`, previously `hasRole("ADMIN")` | A Department Head's `DEPARTMENT`-scoped assignment, if its baseline role grants `Departments:Edit`, can now `PATCH` their own department's metadata without being global ADMIN |
| TC-P3-017 | academic-structure-service | `POST /courses`, previously `hasRole("LECTURER")` | A plain LECTURER-kind user with **no** matching `COURSE`/`COURSE_MODULE`/`DEPARTMENT` assignment for the target academic year/semester is now denied, where previously any LECTURER-kind JWT could always create a course |
| TC-P3-018 | academic-structure-service | `POST /academic-years`, `/degree-programs`, previously `hasRole("ADMIN")` | Still ADMIN-gated by default (no role other than the seeded Administrator role's baseline grants `DegreeProgram:Create` in the illustrative matrix, §5.8) — confirms the migration doesn't accidentally *loosen* anything for non-admins |

### P3.C — Security: direct-API-bypass matrix (one row per changed endpoint, mandatory per §12.4)

| ID | Endpoint | Caller | Expected |
|---|---|---|---|
| TC-P3-019 | `POST /courses` | Valid LECTURER JWT, zero scope assignments | 403 |
| TC-P3-020 | `POST /courses` | Valid LECTURER JWT, `COURSE_MODULE` assignment on an *unrelated* module | 403 |
| TC-P3-021 | `PATCH /degree-programs/{id}/curriculum` | Valid LECTURER JWT (not Dept Head) | 403 |
| TC-P3-022 | `PATCH /degree-programs/{id}/curriculum` | Valid Dept-Head-role JWT, `DEPARTMENT` assignment for a **different** department than the program's | 403 |
| TC-P3-023 | `PATCH /degree-programs/{id}/curriculum` | Valid Dept-Head-role JWT, correct department assignment | 200 — confirms the new path also *positively* works, not just denies |
| TC-P3-024 | `GET /departments/{id}` (hypothetical restricted read, if P2-013 resolves to "reads also gated") | STUDENT JWT | Matches whatever TC-P2-013 decided |
| TC-P3-025 | Any changed endpoint | Expired JWT | 401, unchanged from current behavior — confirms Phase 3 didn't regress basic token-expiry handling |
| TC-P3-026 | Any changed endpoint | JWT with `role` claim manually edited to `ADMIN` but signed with the wrong key (forged) | 401 — confirms signature validation still runs *before* the new permission resolution, not instead of it |

### P3.D — Regression

| ID | Steps | Expected |
|---|---|---|
| TC-P3-027 | Re-run all 54 pre-existing backend tests (24 identity-access + 9 reference-data + 21 academic-structure) after Phase 3's `SecurityConfig` rewrites | All still pass — if any fail, it's because a test's fixture JWT doesn't carry the scope assignments Phase 3 now requires, and the fixture (not the test's intent) needs updating |
| TC-P3-028 | Live, both local-tunnel and Contabo server | Log in as each of the 3 real seeded accounts (Seed Administrator, Dr. Silva, Nimal Perera) post-Phase-3-deploy and confirm each can still do exactly what they could before (per Phase 2's backfilled `GLOBAL` assignments) | No regression for existing real accounts |

---

## Phase 4 — Courses UI redesign (pagination & faceting)

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P4-001 | Integration | P0 | `GET /courses?page=0&size=2` with 3+ seeded courses | Returns exactly 2 results, `totalElements`/`totalPages` correct, matching the `Page<...>` shape already used by `identity-access-service`'s `GET /users` |
| TC-P4-002 | Integration | P0 | `GET /courses?facultyId=<FOC>` | Only courses whose resolved department rolls up to Faculty of Computing |
| TC-P4-003 | Integration | P0 | `GET /courses?departmentId=<dept>` | Only courses under that exact department |
| TC-P4-004 | Integration | P0 | `GET /courses?programId=<prog>` | Only courses whose catalog subject is curriculum-linked to that program |
| TC-P4-005 | Integration | P0 | `GET /courses?academicYearId=<year>` | Only courses in that year |
| TC-P4-006 | Integration | P0 | `GET /courses?status=PUBLISHED` | Only `PUBLISHED` courses |
| TC-P4-007 | Integration | P0 | `GET /courses?q=programming` | Case-insensitive match against catalog course name/code |
| TC-P4-008 | Integration | P1 | Combine all 6 filters simultaneously, including one combination that yields zero results | Empty `content` array, `totalElements=0`, not an error |
| TC-P4-009 | Integration | P0 | `GET /courses?mine=true` as a plain Lecturer, with exactly one `COURSE`-scoped assignment | Returns only that one course |
| TC-P4-010 | Integration | P0 | `GET /courses?mine=true` as a Department Head with a `DEPARTMENT` assignment and **no** direct `lecturer_id` match on any course | Returns every course under that department — confirms `mine=true`'s resolution genuinely changed from "lecturer_id = me" to full scope resolution, per §7.2 |
| TC-P4-011 | Integration | P0 | `GET /courses?mine=true` as a Teaching Assistant with only a `COURSE_MODULE` assignment | Returns the parent course of that module (or the module-level data, depending on final endpoint shape — pin down exact contract before writing this test for real) |
| TC-P4-012 | Security | P0 | `GET /courses` (no `mine` param, i.e. the admin oversight listing) as LECTURER | Either 403, or scoped identically to `mine=true` — must not silently return university-wide data to a non-admin just because the `mine` param was omitted |
| TC-P4-013 | E2E | P1 | Angular admin Courses page: Faculty → Department → Program drill-down UI | Selecting each level narrows the table; clearing a selection widens it back out; pagination controls work with the new server-side paging |
| TC-P4-014 | Regression | P0 | Existing `mine=true`/`mine=false` tests in `AcademicStructureTest.java` (built this session) | Still pass under the new pagination/faceting response shape — the response envelope changing from a bare array to a `Page` object is a **breaking change** to the existing Angular `AcademicStructureService.listCourses()` contract; both the test and the frontend service need updating together, not independently |

---

## Phase 5 — Course Modules

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P5-001 | Integration | P0 | `POST /courses/{id}/modules` with `{title, sequence}` | 201, new `course_modules` row |
| TC-P5-002 | Integration | P0 | Create two modules on the same course with the same `sequence` | Second rejected — `UNIQUE (course_id, sequence)` |
| TC-P5-003 | Integration | P0 | `GET /courses/{id}/modules` | Ordered by `sequence` ascending |
| TC-P5-004 | Integration | P1 | Soft-delete a course, then query its modules | Modules themselves aren't auto-soft-deleted by a DB cascade (no FK `ON DELETE CASCADE` per the architecture's "no destructive cascades, soft-delete only" convention) — service-layer logic must explicitly decide and the test must assert whichever behavior is actually chosen |
| TC-P5-005 | Security | P0 | `POST /courses/{id}/modules` as a Lecturer with a `COURSE`-scoped assignment on a *different* course | 403 |
| TC-P5-006 | Security | P0 | `POST /courses/{id}/modules` as a Lecturer with a `COURSE`-scoped assignment on *this* course, baseline role grants `Course Modules:Create` | 201 |
| TC-P5-007 | Integration | P0 | `PATCH /modules/{id}` to reorder `sequence` to a value already used by a sibling module | Rejected, same unique constraint as TC-P5-002 |
| TC-P5-008 | E2E | P1 | Angular: drill into a Course from the Courses list → see its modules → create/edit one | Full round trip renders correctly |
| TC-P5-009 | Integration | P2 | `course_modules.grading_scale_id` left null at creation | Allowed — nullable per §7.4/§9.2, resolution falls back to the course's own scale per Phase 6 |

---

## Phase 6 — Grading Scale move to Assignment & Grading

*(Depends on the Assignment & Grading service existing at all — these test cases assume its minimal `grading_scales`/`GRADES` tables exist per §9.1 of the architecture report.)*

### P6.A — Move / drop

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P6-001 | Integration | P0 | Confirm, before dropping, that `reference_data.grading_scales` truly has zero references anywhere (re-run the grep-based check from the architecture report §2.6) | Still zero — if anything now references it, **stop**, this migration order assumption is broken |
| TC-P6-002 | Integration | P0 | Drop `reference_data.grading_scales`; `GET /grading-scales` on reference-data-service | 404 route-not-found (endpoint removed, not just empty) |
| TC-P6-003 | Regression | P0 | `ReferenceDataTest.java`'s grading-scale tests (3 of its 9 current tests, per this session's own build log) | Must be **deleted**, not left failing — removing dead tests for a removed feature is the correct action here, not a bug to fix |
| TC-P6-004 | E2E | P0 | Angular Reference Data page's "Grading" tab | Removed entirely from the 3-tab UI, per the user's explicit instruction in the architecture request |
| TC-P6-005 | Integration | P0 | `POST /grading-scales` on the new assignment-grading-service | 201, creates in `assignment_grading.grading_scales` |

### P6.B — Context-specific assignment & resolution hierarchy

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P6-006 | Integration | P0 | `PATCH /degree-programs/{id}` with `defaultGradingScaleId` set | Persisted |
| TC-P6-007 | Integration | P0 | `PATCH /courses/{id}` with `gradingScaleId` set, program's default differs | Resolving "effective scale for this course" returns the course's own, not the program's |
| TC-P6-008 | Integration | P0 | `PATCH /modules/{id}` with `gradingScaleId` set, course's own differs | Module's own wins |
| TC-P6-009 | Integration | P0 | A module with no scale, whose course also has none, whose program also has none | Falls back to the single system-wide default scale |
| TC-P6-010 | Unit | P0 | Resolution function tested in isolation for all 4 levels of the fallback chain independently | Each level correctly short-circuits before falling further |

### P6.C — Historical consistency (the highest-value test group in this phase)

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P6-011 | Integration | P0 | Create a `GRADES` row while a module's resolved scale is "Standard Percentage" (A:70-100...) | `GRADES.grading_scale_snapshot` contains a verbatim copy of that scale's `bands` at insert time |
| TC-P6-012 | Integration | P0 | After TC-P6-011, edit/retire the scale (mark `INACTIVE`, or — per the immutable-bands policy — create a new "v2" row and leave the original untouched) | Re-fetching the *same* grade still displays using its stored snapshot, unaffected by the scale change |
| TC-P6-013 | Integration | P0 | Attempt to directly `UPDATE` the `bands` column of a `grading_scales` row that has at least one `GRADES.grading_scale_id` pointing at it (simulating someone bypassing the service layer) | Should be caught at the service layer (§9.3's documented policy) — if only enforced in application code, this test should call the real service method, not raw SQL, and assert it refuses the edit / instead creates a new versioned row |
| TC-P6-014 | Integration | P1 | Create a new scale version ("v2") after TC-P6-012; assign the new version as a module's active scale | Old grades under the same module (if any existed before the version switch) keep their original snapshot; new grades entered after the switch snapshot the new version's bands |
| TC-P6-015 | Security | P0 | `POST /grading-scales` as a Department Head, scoped to their own department | Allowed if their role's baseline grants `Grading Scales:Create` within `DEPARTMENT` scope (per §9.3/§5.8's illustrative matrix) |
| TC-P6-016 | Security | P0 | Same, but a Department Head of a **different** department attempting to set the scale on a course outside their department | 403 |

---

## Phase 7 — Academic Year context & filtering

### P7.A — Database constraint

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P7-001 | Integration | P0 | With one existing `ACTIVE` year, attempt to `UPDATE` a second year's `status` to `ACTIVE` directly via SQL | Rejected by `idx_academic_years_one_active` (unique partial index violation) |
| TC-P7-002 | Integration | P0 | `AcademicCalendarService.setActive(newYearId)` when a different year is currently `ACTIVE` | Succeeds; old year flips to `ARCHIVED` and new year flips to `ACTIVE` **atomically** (both changes visible together, never a window with zero or two active years) |
| TC-P7-003 | Integration | P1 | Concurrency: two simultaneous `setActive()` calls for two different target years | Exactly one wins; the other either fails cleanly (409) or is serialized by the transaction — must not leave the DB in a 0-active or 2-active state (covered by TC-P7-001's index either way, but assert the *service's* error handling too, not just the DB's) |
| TC-P7-004 | Regression | P0 | Existing seeded data (3 years: 2024/2025 ARCHIVED, 2025/2026 ACTIVE, 2026/2027 UPCOMING) | Still satisfies the new constraint without any migration-time fixup needed — confirm before assuming this is a no-op migration |

### P7.B — No-active-year / fallback handling

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P7-005 | Integration | P0 | Flip the only `ACTIVE` year to `ARCHIVED` without activating another (simulating the gap between terms) | `GET /academic-years` still returns all years correctly; no server error |
| TC-P7-006 | E2E | P0 | Frontend `AcademicYearContextService` initializes when **no** year is `ACTIVE` | Falls back to the most recent `UPCOMING` year with a visible "No active academic year — showing <year>" banner, per §8.2 — must **not** silently guess without telling the user |
| TC-P7-007 | E2E | P1 | Same scenario, but zero years exist at all (fresh install edge case) | Graceful empty state, not a crash |

### P7.C — Shared frontend context

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P7-008 | E2E | P0 | Change the shared year selector on the Courses page | Enrollment/Assignments/Grades pages (once they exist) and the Courses page itself all re-fetch using the new year — not just the page the selector visually lives on |
| TC-P7-009 | E2E | P0 | Rapidly switch years twice before the first fetch resolves | Final displayed data matches the *second* selection only — no race where a slow first response overwrites the second selection's correct data |
| TC-P7-010 | E2E | P1 | Degree Programs' curriculum view | Does **not** show or react to the year selector at all, per §8.3's explicit exclusion (curriculum is year-independent) |
| TC-P7-011 | Integration | P0 | Every year-aware endpoint call verified to include an explicit `academicYearId` query param in its request — audit the actual HTTP calls, not just the UI behavior | No endpoint silently defaults to "today" server-side when the frontend's shared context should have supplied an explicit id |

### P7.D — Write restrictions on archived years

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P7-012 | Security | P0 | `POST /courses` targeting an `ARCHIVED` academic year, as ADMIN | 409 — archived-year write rejection applies **regardless of role**, per §8.4 |
| TC-P7-013 | Security | P0 | `POST /degree-programs/{id}/curriculum` with a `recommendedYear`/semester resolving into an `ARCHIVED` academic year (if curriculum entries become year-bound in a later phase) | 409, same rule |
| TC-P7-014 | Integration | P1 | The same write attempted against an `UPCOMING` year | Succeeds — only `ARCHIVED` is blocked, not `UPCOMING` |
| TC-P7-015 | Integration | P0 | Reads (`GET /courses?academicYearId=<archived>`) against an `ARCHIVED` year | Still succeed — read-only, not inaccessible, per §8.4 |

---

## Phase 8 — Roles & Permissions real UI

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P8-001 | E2E | P0 | Load the Roles & Permissions page | Renders the real `GET /roles` data in the same `Module × View/Create/Edit/Delete` grid shape as the wireframe — visual parity confirms no unnecessary redesign was done |
| TC-P8-002 | E2E | P0 | Toggle one checkbox in the matrix | Fires `PATCH /roles/{id}/permissions`; a page reload shows the toggle persisted (unlike the wireframe's client-only `App.togglePerm`) |
| TC-P8-003 | E2E | P0 | "New Role" flow | Creates a real row via `POST /roles`, immediately selectable in the left-hand role list |
| TC-P8-004 | E2E | P0 | "Add Assignment" flow (replacing the wireframe's destructive "Assign User") | Creates an additive `role_scope_assignments` row; the target user's **existing** `RoleKind`/account role is confirmed unchanged afterward — this is the key behavioral difference from the wireframe's `user.role = role.name` overwrite, and must be explicitly asserted, not assumed |
| TC-P8-005 | E2E | P1 | Per-assignment override UI (new, no wireframe precedent) | Setting an override on one assignment doesn't affect any other assignment of the same role |
| TC-P8-006 | Security | P0 | Load the page as a non-admin (direct URL navigation, bypassing the nav guard) | Route guard redirects away; underlying API calls independently 403 if attempted directly (cross-reference TC-P2-013/019) |
| TC-P8-007 | E2E | P1 | Revoke (`DELETE /assignments/{id}`) from the UI | Assignment disappears from that role's assignment list; a re-fetch of the affected user's own `GET /users/{id}/assignments` confirms it's gone too |

---

## Phase 9 — Event-driven propagation (RabbitMQ)

| ID | Type | Priority | Steps | Expected Result |
|---|---|---|---|---|
| TC-P9-001 | Integration | P0 | Identity & Access publishes a `role.permission.changed` event on `PATCH /roles/{id}/permissions` | Message appears on the expected RabbitMQ exchange/queue |
| TC-P9-002 | Integration | P0 | A consuming service (e.g. academic-structure-service) receives the event | Its local permission-catalog cache updates to reflect the change |
| TC-P9-003 | Integration | P0 | Publish the same event twice (duplicate delivery, simulating at-least-once delivery semantics) | Consumer applies it idempotently — second application is a no-op, not a double-increment or error |
| TC-P9-004 | Integration | P0 | Stop a consuming service, publish 3 events, restart the service | On restart, the service catches up on all 3 (via durable queue / requeue, not silently dropped) |
| TC-P9-005 | E2E | P0 | Revoke a user's `role_scope_assignments` row while they hold a still-valid, unexpired JWT | Their very next request to a service that has received the propagated event is denied **without** needing to log out/in — this is the entire point of Phase 9 over Phase 3's login-time-only embedding, and must be the headline test for this phase |
| TC-P9-006 | Integration | P1 | RabbitMQ connection drops mid-operation (simulate broker restart) | Publishing service retries/buffers rather than silently losing the event; consuming service reconnects and resumes |
| TC-P9-007 | Live | P0 | Run TC-P9-005 against the real infra (RabbitMQ container already provisioned per `infra/docker-compose.infra.yml`, currently unused — confirm it is actually reachable and credentialed before relying on it) | End-to-end propagation confirmed on the real deployed stack, not just local H2/in-memory test doubles |

---

## Cross-cutting test suites (run after *every* phase, not just once)

### C1 — Full regression sweep

| ID | Steps | Expected |
|---|---|---|
| TC-C1-001 | Run the complete existing suite (54 tests as of the Academic Structure build: 24 identity-access + 9 reference-data + 21 academic-structure) after each phase lands | 100% pass, or every failure is explicitly triaged as "expected fixture update" vs. "real regression" before merging |
| TC-C1-002 | `./mvnw -o test` for the whole multi-module reactor after each phase | Build green |
| TC-C1-003 | `npx ng build` for the Angular app after each phase | No new compile errors; pre-existing harmless `?.` warnings (already present, see this session's Academic Structure build log) don't need to be zero, but no *new* warnings/errors should appear |

### C2 — Live deployment parity (per the project's established two-environment practice)

| ID | Steps | Expected |
|---|---|---|
| TC-C2-001 | After each phase's migrations are written, apply them against the local dev tunnel's Postgres **first** | Flyway reports successful migration, service boots, Hibernate validates schema (`ddl-auto: validate`) without error |
| TC-C2-002 | Then apply the same jar/migration to the Contabo server via the established `docker compose build` + `up` flow | Identical success — this project has a standing instruction not to deploy without explicit confirmation first, so this step is "ready to run," not "run automatically" |
| TC-C2-003 | After any destructive-looking migration (renames, drops), verify via direct `psql` that the *other*, unrelated services' schemas were untouched (schema-per-service isolation didn't leak) | e.g. after Phase 1's `reference_data` changes, `identity_access` and `academic` schemas are byte-for-byte unaffected except where Phase 1 explicitly also touches `academic.degree_programs` |

### C3 — Security sweep (consolidated bypass matrix across all phases)

| ID | Steps | Expected |
|---|---|---|
| TC-C3-001 | For every endpoint touched by Phases 1–8, send a request with no `Authorization` header | 401/403 per the existing unauthenticated-request convention (already verified consistent across all 3 built services this session) |
| TC-C3-002 | For every endpoint touched by Phases 1–8, send a request with a validly-signed JWT for a role/scope combination that should be denied | 403, never a 500 (a 500 here would indicate the resolver crashes on an unexpected-but-reachable input, not that it correctly denies) |
| TC-C3-003 | Attempt SQL-injection-shaped input in any new free-text field (`departments.name`, `roles.name`, scope-assignment creation with a malformed `scopeId`) | Parameterized queries (already the pattern via JPA throughout this codebase) reject/escape cleanly; no raw string concatenation introduced by any new phase |
