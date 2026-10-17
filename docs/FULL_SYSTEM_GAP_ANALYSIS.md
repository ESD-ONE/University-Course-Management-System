# UCMS — Full System Gap Analysis (Wireframe vs. Implementation)

Supersedes the Users-module-only scope of `SYSTEM_GAP_ANALYSIS_AND_IMPLEMENTATION_PLAN.md`
(still accurate for Users — not repeated here) with a complete inventory of
**all 25 views** defined in `html-wireframes/index.html`'s `NAV` object and
view-render functions, read in full, line by line, not sampled. Every row
below reflects what the wireframe's actual JavaScript does, with file/line
references, checked against the real implemented application.

## A. Method

The wireframe (`App` object, `html-wireframes/index.html`) defines exactly
25 views across 3 role-based navigation sets (`NAV.Admin`, `NAV.Lecturer`,
`NAV.Student`, lines 552–596) plus one shared view (`profile`). Each view's
render function (lines 1389–2234) was read in full. The real application
(`web/src/app/`) currently routes exactly 3 authenticated pages:
`dashboard`, `users`, `profile` (`app.routes.ts`) — everything else in the
sidebar renders a "Soon" badge and is not a route at all.

## B. The hard constraint this analysis keeps running into

19 of the 25 wireframe views depend on data and business logic that belongs
to one of 5 backend services that **do not exist**: Academic Structure
(years/semesters/programs/curriculum/catalog), Enrollment
(registrations/browse/roster/drop), Assignment & Grading
(assignments/submissions/grades), Reference Data (faculties/grading
scales/categories), Analytics & Reporting (reports/dashboards). Only
Identity & Access is implemented. This isn't a prioritization choice on
this pass's part — there is no schema, no entity, no API endpoint for any
of that data anywhere in the codebase. A `Degree Program` dropdown with
nothing behind it, or a `+ Create Assignment` button that writes to no
table, is exactly the "placeholder UI... superficial implementation" this
task explicitly rules out. Building each of those services to the same
standard as Identity & Access (schema, entities, Flyway migrations, Spring
Security wiring, real Angular pages, tests, live deployment) is the
remaining work, and it is large — Identity & Access alone took this
project's entire history so far across 3 iterative passes. That is recorded
honestly below, per-view, rather than glossed over.

## C. View-by-view inventory

Legend: ✅ implemented and matches · 🟡 partially implemented · ⛔ not
implemented, blocked on a missing service · 🔲 not implemented, achievable
now (no missing service — just not built yet)

| # | View (`NAV` label) | Wireframe source | Status | Blocked on | Priority |
|---|---|---|---|---|---|
| 1 | Login | lines 278–314, `App.login` 639–652 | ✅ | — | — |
| 2 | Admin Dashboard | `adminDashboard()` 1389–1442 | ⛔ (shell only: static "what's live" text, no tiles/charts) | Academic Structure, Enrollment (student/course/program/registration counts), Analytics (enrollment trend, top courses) | High for the parts backed by real Identity & Access data (see §D) |
| 3 | Users | `adminUsers()` 1663–1721 | ✅ | — | — |
| 4 | Academic Calendar | `adminCalendar()` 1444–1470 | ⛔ | Academic Structure (academic years, semesters) | Medium |
| 5 | Degree Programs (+Curriculum) | `adminPrograms()` 1472–1511 | ⛔ | Academic Structure (programs, curriculum, course catalog) | Medium |
| 6 | Courses (university-wide) | `adminCourses()` 1730–1785 | ⛔ | Academic Structure (course catalog, offerings) | Medium |
| 7 | Registrations (admin proxy-register) | `adminRegistrations()` 1513–1588 | ⛔ | Enrollment (registration records), Academic Structure (programs/curriculum) | Medium |
| 8 | Roles & Permissions | `adminRoles()` 1590–1626 | ⛔ | A real, data-driven permission model — today `hasRole("ADMIN")` is a compile-time Spring Security rule, not a database-backed matrix. This changes the authorization architecture itself, not just adds a screen | Low — needs its own design pass, not a bolt-on (see prior report §C.7) |
| 9 | Reference Data | `adminReference()` 1628–1662 | ⛔ | Reference Data service (faculties, grading scales, categories) | Medium — smallest of the missing services, unblocks Academic Structure next |
| 10 | Reports | `adminReports()` 1787–1811 | ⛔ | Analytics & Reporting service | Low |
| 11 | Lecturer Dashboard | `lecturerDashboard()` 1814–1846 | ⛔ | Academic Structure, Assignment & Grading | Medium |
| 12 | My Courses (lecturer) | `lecturerCourses()` 1848–1874 | ⛔ | Academic Structure (course offerings) | Medium |
| 13 | Course Roster | `lecturerRoster()` 1876–1913 | ⛔ | Enrollment (who's registered) | Medium |
| 14 | Create Course | `lecturerCreateCourse()` 1915–1946 | ⛔ | Academic Structure (catalog, offerings) | Medium |
| 15 | Grade Submissions | `lecturerGrading()` 1948–1973 | ⛔ | Assignment & Grading | Medium |
| 16 | Assignments (lecturer) | `lecturerAssignments()` 1975–2004 | ⛔ | Assignment & Grading | Medium |
| 17 | Students (lecturer, cross-course) | `lecturerStudents()` 2006–2038 | ⛔ | Enrollment | Low |
| 18 | Student Dashboard | `studentDashboard()` 2041–2071 | ⛔ | Enrollment, Assignment & Grading | Medium |
| 19 | Browse Courses | `studentBrowse()` 2073–2096 | ⛔ | Academic Structure, Enrollment (seats/enroll) | High — this is the enrollment self-service path flow-01-student-enrollment.mmd was written around |
| 20 | Assignment (student submission) | `studentAssignment()` 2098–2127 | ⛔ | Assignment & Grading, plus a file-upload path (MinIO is provisioned, nothing uses it yet) | Medium |
| 21 | My Enrollments | `studentEnrollments()` 2130–2144 | ⛔ | Enrollment | High — pairs with #19 |
| 22 | My Program | `studentProgram()` 2146–2174 | ⛔ | Academic Structure, Enrollment | Medium |
| 23 | Grades (student) | `studentGrades()` 2176–2196 | ⛔ | Assignment & Grading | Medium |
| 24 | Profile | `profile()` 2199–2234 | 🔲 **Read-only today; every field below is achievable with no new service** | — | **Critical — see §D, this is this pass's focus** |
| 25 | App shell / nav / role groups | lines 316–339, 542–596 | 🟡 partial — shell and role-based nav exist; "Soon" badges stand in for views 4–23 honestly (not disguised as working) | — | — |

## D. What this pass actually implements: Profile

The one view where the wireframe's intent is **fully achievable inside
Identity & Access's existing boundary** — no new service, no fake data.

| Component | Wireframe (`profile()`, lines 2199–2234) | Current (`web/src/app/features/profile/`) | This pass |
|---|---|---|---|
| Name edit | `#pfName` input, `App.saveProfile()` | Read-only display, no edit, no backend endpoint | **Implement**: `PATCH /users/me` (name only — email is intentionally `disabled` in the wireframe too, matching that email isn't meant to be self-service-editable) |
| Change Password | `#pfCurPwd`/`#pfNewPwd`/`#pfConfPwd`, `App.updatePassword()`, inline error (`#pfPwdError`) | Not implemented — explicit "not implemented yet" note in the current Profile page | **Implement**: `PATCH /users/me/password`, verifies current password server-side, rejects with a clear error if wrong (matches the wireframe's inline `Modal.err`-style pattern) |
| Notification Preferences | 4 checkboxes (email / assignment reminders / grade alerts / registration confirmations), `App.savePrefs()` | Not implemented | **Implement, honestly scoped**: persisted as real boolean columns on the user's own account (Identity & Access legitimately owns "this account's settings"), with an explicit note in the UI and in code comments that no Notification service exists yet to *act* on them — this is real, working settings storage, not a fake toggle, but it has no consumer yet |
| Status badge, avatar, role pill | `UI.avatar`, `UI.badge` | Already implemented | — |

Dashboard is **not** rebuilt with fake charts in this pass — the current
honest "what's live" placeholder stays, except its text is updated to also
mention Profile is now fully live (see the component once it's live).

## E. Recommended sequence for the remaining 19 views

In dependency order — building Reference Data first unblocks Academic
Structure, which unblocks Enrollment and Assignment & Grading, which
unblocks the dashboards and Analytics:

1. **Reference Data service** (view #9) — smallest, no dependencies, unblocks #4–6
2. **Academic Structure service** (views #4, #5, #6) — depends on #1
3. **Enrollment service** (views #7, #13, #17, #19, #21) — depends on #2
4. **Assignment & Grading service** (views #14, #15, #16, #20, #23) — depends on #3
5. **Dashboards, real** (views #2, #11, #18) — depend on #2–4 existing
6. **Analytics & Reporting service** (view #10) — depends on #2–4
7. **Roles & Permissions** (view #8) — independent of the above, but needs its own authorization-model design pass first, not bundled in

Each phase is comparable in size to the Identity & Access vertical slice
already built (schema + entities + migrations + Security + Gateway route +
real Angular pages + tests + live deployment + wireframe sync). Attempting
several in one pass risks exactly the superficial, under-tested result this
task explicitly rules out — the Identity & Access slice itself needed 3
rounds of real bugs found and fixed after "it compiles" (see
`docs/ARCHITECTURE_ANALYSIS.md`'s landmine notes and this file's own commit
history) before it was actually correct. This plan recommends the same
one-service-at-a-time rigor going forward rather than a faster, shallower
pass across all of them at once.
