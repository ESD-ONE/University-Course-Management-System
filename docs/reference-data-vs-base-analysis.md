# UCMS — Reference-Data-vs-Base Architectural Evaluation

**Date:** 2026-10-26
**Scope**: evaluate whether replacing `reference-data-service` with a broader "Base"
microservice (shared reference data + common business logic) improves the architecture.
Per the governing instruction for this audit, this is an **evaluation only** — nothing
in this document is a plan to execute. No code, schema, or service-boundary change has
been made as a result of it, and none will be without an explicit follow-up decision.
**No implementation work has started.**

---

## 1. What reference-data-service actually is today (verified facts)

From the full source-code inventory (`api-inventory.md` §reference-data-service,
`gap-analysis-report.md` G-05/G-06):

- **4 entity types**: Faculty, Department (→ Faculty, real FK), GradingScale,
  CourseCategory. All CRUD, delegated to a single shared `ReferenceDataService` class.
- **Zero business logic beyond CRUD** except one dependents guard
  (`deleteFaculty` blocks if it has active Departments). Department/GradingScale/
  CourseCategory deletes are unconditional. Nothing resembling a workflow, calculation,
  or cross-entity rule exists in this service.
- **Own schema, own Flyway migrations, own schema-scoped DB role, own SecurityConfig** —
  textbook single-responsibility microservice boundary.
- **Consumed by exactly one other service**: academic-structure-service, via two plain
  UUID fields (`degree_programs.department_id`, `course_catalog.owning_department_id`)
  with **no FK, no synchronous existence check, no API call** — academic-structure-service
  never calls reference-data-service at all; it just trusts the UUID it was given at
  write time. This is the opposite pattern from how academic-structure-service treats
  identity-access-service's Role Catalog (a real, polled, cached API dependency via
  `RoleCatalogClient`). That asymmetry is itself a finding, not a reason to merge services —
  see §5.
- **Internal coupling**: effectively none. Faculty↔Department is the only relationship
  inside the service; GradingScale and CourseCategory are both fully standalone.

## 2. What "common business logic" would actually go into a Base service — checked, not assumed

The proposal names "common business logic" as a second payload for a Base service,
alongside reference data. I looked for a concrete, present-day candidate for that payload
across all three built services, rather than assuming one exists:

| Candidate | Where it lives today | Is it actually duplicated/shared logic, or just data? |
|---|---|---|
| Audit columns (`version`/`createdAt`/`createdBy`/`updatedAt`/`updatedBy`/`deletedAt`/`deletedBy`) | Each service has its own `Auditable`/`AuditableNoSoftDelete` `@MappedSuperclass` | **Duplicated code**, not shared data. Each service compiles its own copy; there is no runtime sharing happening, just consistent convention. |
| `GlobalExceptionHandler` mapping (`DuplicateEntityException`→409, etc.) | Each service has its own class | Same — **duplicated code**, not shared data or shared runtime logic. |
| JWT validation (`JwtAuthFilter`/`JwtValidator`) | Each service independently validates the same `JWT_SECRET`-signed token | Same — **duplicated code**. Functionally correct and consistent, but copy-pasted, not called from a shared place. |
| Role/Permission catalog | Lives in identity-access-service; academic-structure-service polls it via a real HTTP client (`RoleCatalogClient`) | This **is** a genuine shared-data dependency across services — but it already has a clean, deliberate home (identity-access-service, since Role/Permission is tightly bound to User/Auth), and is the one piece of evidence that cross-service shared data integration *can* be done cleanly here when it's a real need. |
| Department-existence validation | Owned by reference-data-service; **not actually consulted** by academic-structure-service (see §1) | This is the one real gap (G-05) — but it's a missing *integration*, not a missing *service*. |

**Finding**: there is no verified, present-day common business logic that spans the 3
built services and lacks a home. The three duplicated patterns (audit columns, exception
mapping, JWT validation) are **copy-pasted code**, which is a shared-*library* problem
(solved by a Maven module, not a microservice), not a shared-*service* problem. Treating
them as justification for merging a runtime service would be a category error — a Base
*microservice* adds a network dependency and a deployment unit; it does nothing to reduce
code duplication that a shared JAR wouldn't do better and far more cheaply.

## 3. The three options, compared

| Dimension | Option A — Keep reference-data-service as-is | Option B — Evolve it into a broader "Base" service (rename + absorb common business logic) | Option C — Introduce a new, separate Base service with *selective* migration |
|---|---|---|---|
| **Separation of concerns** | Clean — one service, one bounded domain (institutional reference data) | Degraded — "Base" by definition has no bounded domain; its charter grows to mean "wherever we didn't have a better place," the classic microservice anti-pattern (a service that accumulates unrelated concerns over time) | Depends entirely on what's selectively migrated — but §2 found no concrete candidate today, so in practice this collapses to "nothing moves," same as Option A |
| **Maintainability** | High — small, homogeneous, easy to reason about (confirmed by its own test suite being a single, well-organized file) | Lower over time — a service with a vague charter is harder to keep small; every future team member asks "does X belong in Base?" with no principled answer | Same as A in practice, since nothing concrete migrates; the service wouldn't exist yet |
| **Data ownership** | Explicit and correct — Reference Data owns Faculty/Department/GradingScale/CourseCategory exclusively | Unclear — once "Base" absorbs other services' shared concerns, who owns what inside it becomes ambiguous over time | N/A — no new ownership created without a concrete migration target |
| **Coupling** | Low — only one consumer (academic-structure-service), and even that consumer doesn't actually call it synchronously today (G-05) | **Higher** — a Base service that accumulates more responsibilities becomes a dependency of more and more of the other 6 services as they're built, concentrating risk | Low, same as A, until something real is identified to migrate |
| **Performance** | No concern — current CRUD load is trivial, no N+1 or cross-service join patterns found | No material difference for the reference-data payload itself; a bigger service means a bigger blast radius for unrelated load spikes to affect reference-data reads | No material difference |
| **Security** | Clean, auditable `SecurityConfig` scoped to exactly 4 entity types | Harder to audit — a Base service's permission surface grows opaque as its charter grows | Same as A |
| **Deployment complexity** | Status quo — already deployed, already in the Jenkins pipeline, already healthchecked | Requires a rename (package, routes, container name, Jenkins job references, Gateway route prefix, frontend `REFERENCE_API_BASE_URL`), a DB schema rename/migration, and re-verification of every consumer — real, non-trivial, hard-to-reverse work for a payload (§2) that doesn't exist yet | Requires creating and deploying an entirely new service for no identified present-day workload |
| **Migration risk** | None — no migration happens | **High** — renaming a live, deployed, schema-owning production service (container name, DB role, Gateway route, frontend base URL, Jenkins job) is exactly the class of hard-to-reverse change this audit's own working rules call out for extra caution | Low *only if* nothing is actually migrated; otherwise same risk profile as B for whatever does move |

## 4. Recommendation

**Keep reference-data-service as-is (Option A).** Neither Option B nor Option C is
justified by anything found in this audit — this is a "no" that follows directly from
the evidence in §1–§3, not a default-to-caution answer. Specifically:

- There is no present-day common business logic that spans the built services and
  lacks a home (§2) — the stated rationale for a Base service doesn't have a concrete
  payload yet.
- The one real cross-service problem this evaluation surfaced — **Gap G-05**:
  academic-structure-service never verifies that a `departmentId` it's writing still
  exists in reference-data-service, so a deleted department silently orphans degree
  programs and catalog entries — is a missing **integration** (a synchronous existence
  check or an async reconciliation job), not a missing **service merge**. Merging the
  two services would not fix this; it would just make the same unenforced reference
  an unenforced reference *within* a bigger service instead of across two smaller ones.
- The duplicated-code patterns (audit columns, exception mapping, JWT validation) are
  real but belong to a different architectural conversation: a shared Maven library
  (e.g. a `ucms-common` module published to each service's build), not a runtime
  service boundary change. A library costs nothing at runtime and doesn't create a new
  network dependency every other service would need to be available; a Base
  *microservice* would.

## 5. What to actually do, if anything

Two follow-ups came out of this evaluation, **neither of which is the Base-service
migration the proposal asked me to evaluate**, and neither of which I'm implementing
without your separate go-ahead:

1. **Fix Gap G-05 directly**: give academic-structure-service a real existence check
   against reference-data-service's Department (mirroring the `RoleCatalogClient`
   pattern it already uses for identity-access-service's Role Catalog — a precedent
   that this kind of cross-service integration can be done cleanly here), or add
   dependents-awareness to reference-data-service's `deleteDepartment`. This is a
   same-service-boundary fix, far lower risk than any service rename.
2. **Consider a shared code library later, as a separate initiative**, only if/when the
   4 planned services are actually built and the audit-column/exception-handling/JWT
   duplication becomes a demonstrated maintenance cost across 5+ services instead of 3 —
   not now, and not as a reason to touch reference-data-service's boundary.

## 6. Explicit non-action

Per this audit's working rules, I have not touched reference-data-service's name,
package, schema, routes, or the frontend's integration with it as a result of this
document. If you want either follow-up in §5 actioned, say so explicitly and I'll scope
it as its own change — the existence-check fix (G-05) is a small, low-risk, same-boundary
change I could do next; the shared-library extraction is a larger, separate initiative
I'd want to plan out before touching any service's build.
