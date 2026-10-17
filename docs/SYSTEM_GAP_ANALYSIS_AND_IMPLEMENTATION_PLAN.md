# UCMS — System Gap Analysis & Implementation Plan

Compares the HTML wireframe (`html-wireframes/index.html`, a static client-side
prototype with hardcoded demo data — the `DB` object — and no backend calls at
all) against the real, implemented application (Angular `web/` + Spring Boot
`services/`). Every finding below is grounded in the actual files, not inferred
— file paths and line numbers are given so each claim can be checked directly.

---

## A. System Overview

**Architecture.** Microservices, one shared PostgreSQL database (`ucms`) with
a schema per service (see `docs/architecture/DATA_GOVERNANCE.md`). Of the 7
designed services, only **Identity & Access** is implemented
(`services/identity-access-service/`), fronted by a Spring Cloud Gateway
(`services/gateway/`). Academic Structure, Enrollment, Assignment & Grading,
Notification, Analytics & Reporting, and Reference Data exist only as
architecture diagrams and as hardcoded demo data in the wireframe — there is
no backend, no schema, and no API for any of them.

**User roles.** The application has exactly 3 roles, defined as a fixed Java
enum (`RoleKind.java`): `ADMIN`, `LECTURER`, `STUDENT`. The wireframe's `DB.roles`
array additionally shows 2 "Custom Role" entries (`Program Coordinator`,
`Teaching Assistant`) with a permissions matrix — there is no backend concept
of a custom role or permission matrix anywhere; it's wireframe-only demo data.
Per this task's instruction to use real roles rather than invent new ones,
this plan builds against the 3 real roles and treats the custom-role /
permission-matrix UI as a separate, larger, not-yet-scoped feature (see §C,
Roles & Permissions row).

**Core workflow implemented today:** an admin logs in, sees a flat list of
every user regardless of role, can create a user (name/email/role/status),
change a user's role, and cycle a user's status. That's the entire
functional surface of the application right now.

---

## B. Feature Inventory

### B.1 Wireframe (`html-wireframes/index.html`)

| Area | What exists in the wireframe |
|---|---|
| Login | Full login screen, 3 demo-login chips, validation (`App.login`, line 636) |
| App shell | Topbar, user menu, sidebar nav grouped by role (`ROLE_GROUP`) |
| Dashboard | Role-specific stat tiles (admin/lecturer/student), all fake `DB` data |
| **Admin → Users** | Stat tiles (Total/Active/Students/Staff), filters (role/status/search, lines 1675–1682), flat table, New User modal with conditional Student fields (lines 1012–1021), Change Role modal, status toggle |
| Admin → Roles | Custom role creation, permission matrix, assigned-users list (not in scope — see §A) |
| Admin → Academic Years/Semesters, Programs, Curriculum, Course Catalog | Full CRUD UI, all fake data |
| Admin → Reports | Report library, scheduling |
| Lecturer → Courses, Assignments, Grading, Roster | Full CRUD UI, all fake data |
| Student → Browse Courses, My Enrollments, Assignment submission | Full UI, all fake data |
| Profile | Name/email display, password change form, notification preferences |

### B.2 Implemented application

| Area | Backend | Frontend |
|---|---|---|
| Login | `POST /auth/login` — real, matches `flow-04-login.mmd` | `features/login/` — real, wired |
| Users list | `GET /users` — **no pagination, no filter, no search** (`UserController.java:33-36`) | `features/users/users-list.component.ts` — one flat unsorted table, no tabs, no search, no filter, no pagination |
| Create user | `POST /users` — name, email, role, status, optional password (`CreateUserRequest.java`) | `new-user-dialog.component.ts` — same fields, **no student-specific fields** |
| Change role | `PUT /users/{id}/role` | `change-role-dialog.component.ts` — real, wired |
| Status toggle | `PATCH /users/{id}/status` | Cycles ACTIVE → INACTIVE → SUSPENDED → ACTIVE |
| Profile | `GET /users/me` — read-only | `features/profile/` — read-only, explicit "not implemented" note for password/notifications |
| Dashboard | — | `features/dashboard/` — static "what's live" text, no stat tiles (deliberately, to avoid fake numbers) |
| Roles & permissions | **No backend at all** — `SecurityConfig.java:31` references `/roles/**` but no `RoleController` exists | Not built |
| Everything else in B.1 (Academic Structure, Enrollment, Grading, Reports, etc.) | **No backend** | Not built, sidebar shows "Soon" badges |

---

## C. Gap Analysis

Only gaps that are **in scope** for this pass (Identity & Access / the Users
module) are analyzed to Critical/High/Medium detail. Gaps belonging to
unimplemented services are listed once, at Low priority, with the blocking
dependency named explicitly rather than worked around.

### C.1 No server-side pagination on `GET /users`

- **Module/page:** Identity & Access → Users list
- **Wireframe intent:** Flat list, client-side filtered — acceptable at wireframe demo scale (12 fake users)
- **Current implementation:** `UserRepository.findAll()` loads every row, every time, for every request (`UserService.java:84`)
- **Why this matters:** The task's own framing is "thousands of student records." Loading the entire table into the browser to page through it client-side does not scale and was never asked for by the wireframe's own data volume
- **Required change:** Spring Data `Pageable` on the repository/service/controller; query params `page`, `size`, `sort`
- **Dependencies:** None
- **Priority:** **Critical**
- **Acceptance criteria:** `GET /users?page=0&size=20` returns at most 20 rows and total-element/total-page metadata; response time doesn't grow linearly with total user count for a fixed page size

### C.2 No role filter, no role-based views ("thousands of students in one undifferentiated list")

- **Module/page:** Identity & Access → Users list
- **Wireframe intent:** A role *filter* on an otherwise flat list (line 1675) — not role-based tabs. Tabs are this task's own explicit new requirement (§6 of the brief), not a wireframe gap
- **Current implementation:** No role parameter on `GET /users` at all; frontend has one table for all roles mixed together (matches the screenshot provided: a single "ADMIN" row with no way to see lecturers/students separately)
- **Required change:** `GET /users?role=STUDENT` server-side filter; frontend role-based tabs (Administrators / Lecturers / Students) backed by it, each tab's table independently paginated/searched/filtered
- **Dependencies:** C.1 (pagination)
- **Priority:** **Critical**
- **Acceptance criteria:** Each tab shows only users of that role; switching tabs re-queries rather than re-filtering an already-fetched full list; record count per tab is accurate (from the server, not `array.length` on a partial page)

### C.3 No search

- **Module/page:** Identity & Access → Users list
- **Wireframe intent:** `ufSearch` input, client-side substring match on name/email (line 1681)
- **Current implementation:** No search capability anywhere in `UserController`/`UserService`
- **Required change:** `GET /users?q=...` server-side, case-insensitive match on name and email; debounced on the frontend (~300ms) so it doesn't fire a request per keystroke
- **Dependencies:** C.1
- **Priority:** **High**
- **Acceptance criteria:** Searching "herath" returns matching users by name or email, server-side, combinable with the role tab and status filter

### C.4 No status filter

- **Module/page:** Identity & Access → Users list
- **Wireframe intent:** `ufStatus` dropdown (line 1678)
- **Current implementation:** Status only shown per-row; no filter
- **Required change:** `GET /users?status=ACTIVE` server-side filter, combinable with role + search
- **Dependencies:** C.1
- **Priority:** **Medium**
- **Acceptance criteria:** Filtering by status returns only matching rows, combined correctly with role/search when both are set

### C.5 No student-specific registration fields

- **Module/page:** Identity & Access → New User (Student)
- **Wireframe intent:** When role = Student, show a Student ID (auto-generated, `STU-YYYY-NNNN`) and a Degree Program dropdown (lines 1016–1018, 1037–1040)
- **Current implementation:** `CreateUserRequest` has no student-specific fields at all; `User` entity has no `studentId` column
- **Required change:**
  - **Student ID:** add a nullable, unique `student_id` column to `users` (Identity & Access owns this — it's an identifier on the account, not academic data), auto-generated server-side on create when role = STUDENT, format `STU-YYYY-NNNN`, shown read-only to the admin after creation (matches the wireframe's auto-generate-not-type pattern)
  - **Degree Program:** **cannot be implemented truthfully right now.** A real program selector needs a real `DegreeProgram` table, which belongs to the Academic Structure service — a service that does not exist yet (no schema, no API, no data). Faking it with a hardcoded dropdown or a table living inside Identity & Access's own schema would violate the schema-per-service boundary this project has otherwise been careful about (see `DATA_GOVERNANCE.md`), and would produce a value with nothing to validate against. **This is explicitly deferred, not silently dropped** — see §F, Phase 5
- **Dependencies:** Degree Program sub-item blocked on Academic Structure service (does not exist)
- **Priority:** **High** for Student ID; **Blocked** (not Low — Low implies "nice to have later", this is "cannot be done correctly yet") for Degree Program
- **Acceptance criteria:** Creating a Student without specifying a Student ID gets one generated in the `STU-YYYY-NNNN` format; it's unique; it's returned in the creation response and on every later read of that user

### C.6 No summary/count metrics

- **Module/page:** Identity & Access → Users list
- **Wireframe intent:** 4 stat tiles — Total, Active, Students, Staff & Admin (lines 1667–1672)
- **Current implementation:** None
- **Required change:** A lightweight `GET /users/summary` (or equivalent) returning counts by role and by status, computed server-side (`COUNT(*) ... GROUP BY`), not derived from a paginated page's row count
- **Dependencies:** None
- **Priority:** **Medium**
- **Acceptance criteria:** Tile counts match the true totals regardless of which page or filter is currently applied in the table below them

### C.7 Roles & Permissions screen

- **Module/page:** Admin → Roles (wireframe only)
- **Wireframe intent:** Create custom roles, assign a permission matrix per module/action, see assigned users per role
- **Current implementation:** No backend (`/roles/**` is referenced in `SecurityConfig` but unimplemented), no frontend
- **Why not in this pass:** This is a materially different, larger feature — dynamic roles and a permission matrix — than "manage the 3 fixed roles that exist." It changes the authorization model itself (today, `hasRole("ADMIN")` is a compile-time Spring Security rule, not data-driven). Scoping it in without a deliberate design pass risks exactly the kind of rushed, bolted-on change this task is trying to avoid elsewhere
- **Priority:** **Low / Out of scope for this pass** — flagged for a dedicated design discussion, not implemented here
- **Dependencies:** A real permission model decision (out of scope to decide unilaterally here)

### C.8 Bulk operations (CSV/Excel import, export, batch status)

- **Wireframe intent:** Not present in the wireframe at all — this is a requirement from the task brief itself ("assess whether bulk registration... is valuable"), not a wireframe gap
- **Assessment:** Valuable at real scale (registering a cohort of students one-by-one does not scale), but it has real dependencies this project doesn't have yet: a file-upload mechanism (MinIO is provisioned in infra but nothing in Identity & Access uses it), duplicate-detection and partial-failure reporting for a multi-row import, and a decision on who can bulk-import and what gets audited. Building this now, on top of a Users module whose single-record path (role-based tabs, search, pagination) doesn't exist yet, would be building a bigger feature on a foundation that isn't there
- **Priority:** **Low / deferred** — recommended as the next phase after the single-record workflow in this plan ships and is used
- **Dependencies:** C.1–C.3 (the single-record list/search/pagination this import would need to report against), a file-upload path via MinIO

### C.9 Everything outside Identity & Access

Academic Structure, Enrollment, Assignment & Grading, Notification, Analytics
& Reporting, Reference Data — all wireframe-only, zero backend, as documented
already in `docs/ARCHITECTURE_ANALYSIS.md`. Not re-litigated here; out of
scope for this pass, which is explicitly about the Users module per §6 of the
task brief.

---

## D. UI/UX Improvement Plan

Grounded in a short research pass (not exhaustive — enough to make specific,
justified decisions rather than generic ones):

- **Tabs are the right pattern here.** Material Design's tab guidance is
  that tabs suit "related content at the same level of hierarchy" where only
  one group is relevant at a time and groups aren't compared side-by-side —
  exactly the Administrators/Lecturers/Students split, where an admin is
  never comparing a lecturer row against a student row
  ([Material Design — Tabs](https://m2.material.io/go/design-tabs)). The CMS
  Design System's guidance to prefer another pattern past ~4 tabs doesn't
  apply — this is 3, matching the system's 3 real roles.
- **Server-side pagination over infinite scroll**, for the reason this task
  itself gives (thousands of records): enterprise data-table guidance
  converges on explicit pagination with a visible "showing X of Y" count
  rather than infinite scroll, which loses position/landmarks at scale
  ([UX Planet — data table best practices](https://uxplanet.org/best-practices-for-usable-and-efficient-data-table-in-applications-4a1d1fb29550)).
  Implemented as §C.1/C.2.
- **Search and filters reduce how much paging is needed at all** — the same
  source argues a table pattern succeeds when strong search/filtering means
  users rarely need to page deep into a list. Implemented as §C.3/C.4,
  combinable with the role tab rather than siloed.
- **Accessibility baseline for the tabs:** `role="tablist"`/`role="tab"`/
  `role="tabpanel"`, `aria-selected` on exactly one tab at a time, and
  `aria-controls`/`aria-labelledby` linking each tab to its panel, per the
  W3C ARIA Authoring Practices tab pattern. Full roving-tabindex arrow-key
  navigation between tabs is **not** implemented in this pass (tracked in
  §F as a fast follow) — the tabs are reachable and operable by keyboard via
  standard Tab/Enter, just not with the APG's arrow-key enhancement yet.
- **Role-locked "New User" action per tab.** Rather than the wireframe's
  single modal with a role dropdown that conditionally reveals fields, each
  tab's "+ New [Role]" button pre-selects and locks that role — fewer
  fields, fewer states to validate, and it matches the mental model "I'm on
  the Students tab, so I'm adding a student," which is a smaller change in
  intent than the wireframe's more general-purpose modal.
- **Loading/empty/error states.** The current implementation already has a
  reasonable base pattern (`loading`/`loadError` signals in
  `users-list.component.ts`) — extended per tab rather than redesigned.

---

## E. Technical Implementation Plan

### Backend (`services/identity-access-service`)

1. **Migration `V3__add_student_id.sql`:** `ALTER TABLE users ADD COLUMN student_id varchar(20) UNIQUE;` (nullable — only STUDENT rows get one)
2. **`User.java`:** add `studentId` field + accessors
3. **`UserRepository.java`:** replace `findAll()` usage with a `Specification`-or-derived-query combination of role/status/search, all `Pageable`-aware; keep `findByEmailAndDeletedAtIsNull` as-is
4. **`UserService.java`:**
   - `search(RoleKind role, UserStatus status, String q, Pageable pageable): Page<UserResponse>`
   - `summary(): UserSummaryResponse` (counts by role/status via a `COUNT... GROUP BY` query, not in-memory)
   - `createUser(...)`: generate `student_id` server-side when `role == STUDENT`, format `STU-<year>-<4 digits>`, retry on the (rare) collision from `UNIQUE` constraint violation
5. **`UserController.java`:**
   - `GET /users` gains `role`, `status`, `q`, `page`, `size`, `sort` query params, returns `Page<UserResponse>`
   - `GET /users/summary` — new
6. **DTOs:** `UserResponse`/`CreatedUserResponse` gain nullable `studentId`; new `UserSummaryResponse(long total, long active, long students, long staff)`
7. **Tests:** extend `UserManagementTest` with pagination/filter/search/summary cases and the student-ID auto-generation + uniqueness case

### Frontend (`web/src/app/features/users/`)

1. **`user.model.ts`:** `studentId?: string` on `UserResponse`/`CreatedUserResponse`; new `PagedResponse<T>` and `UserSummary` models matching the new backend shapes
2. **`user.service.ts`:** `list()` takes `{ role, status, q, page, size }` and returns `Observable<PagedResponse<UserResponse>>`; new `summary(): Observable<UserSummary>`
3. **`users-list.component.ts`/`.html`:** rebuilt around 3 role tabs (Administrators/Lecturers/Students), each owning its own search/status-filter/page state so switching tabs doesn't lose the other tabs' filters; summary tiles wired to `GET /users/summary`; pagination controls (prev/next + page indicator, "showing X–Y of Z")
4. **`new-user-dialog.component.ts`/`.html`:** role is passed in (locked, from the active tab) instead of chosen; shows the generated Student ID in the post-create confirmation step when present

### Wireframe (`html-wireframes/index.html`)

Add the same role-tab structure to `App.adminUsers()` (actual function name
to be confirmed against the live file at implementation time), each tab
filtering the existing `DB.users` array client-side by role (the wireframe
has no backend to paginate against — its "pagination" stays representative/
demo-only, clearly distinguishable from the real app's server-side paging),
plus a Student ID column on the Students tab and a locked role on each tab's
"+ New" button.

---

## F. Implementation Sequence

1. **Backend: pagination + filtering + search + summary** (§C.1–C.4, C.6) — blocks everything else, since the frontend tabs have nothing real to call otherwise
2. **Backend: student ID generation** (§C.5, Student ID half only)
3. **Frontend: role-based tabs, wired to the new paginated/filtered endpoint**
4. **Frontend: search, status filter, pagination controls, summary tiles**
5. **Frontend: role-locked New User flow + Student ID display**
6. **Wireframe: mirror the tab structure and Student ID column**
7. **Tests + verification** (§G)
8. **Deferred, explicitly out of scope for this pass:** Roles & Permissions screen (§C.7), bulk import/export (§C.8), Degree Program field (§C.5 second half) — each blocked or deliberately descoped for the reasons stated inline, not forgotten

---

## G. Testing and Verification Plan

- **Backend:** `UserManagementTest` extended with: paginated list returns correct page metadata; role filter returns only matching rows; search matches name and email, case-insensitively; status filter combines correctly with role filter; summary counts are accurate against a known seeded set; student ID is generated, unique, and `STU-YYYY-NNNN`-shaped; creating a non-student never gets a student ID
- **Frontend:** manual verification against the running local stack (no FE test framework is wired up yet — noted as a pre-existing gap, not newly introduced by this work) — each tab shows only its role, search/filter/pagination combine correctly, switching tabs preserves each tab's own filter state, New User is role-locked per tab, Student ID appears in the creation confirmation and in the list
- **Regression:** existing login, change-role, and status-cycle flows re-verified unchanged after the list/query rework
- **Comparison:** updated wireframe and updated app opened side-by-side for the Users module specifically — tab labels, counts, and Student ID placement checked for consistency

---

## Status

This report was written before implementation began. Progress against §F is
tracked here as each phase completes — see the end of this document for the
current state once implementation starts.
