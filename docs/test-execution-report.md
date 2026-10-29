# UCMS — Test Execution Report

**Date:** 2026-10-26 · **Branch:** `docs/system-audit-phase1` (uncommitted working tree at time of testing)
**Scope:** the complete system — all 7 microservices + Gateway + Angular frontend — after the
full build-out recorded in `implementation-change-log.md`.

Every result below was **actually executed and observed**. Nothing is marked passed on the
basis of reasoning alone. Raw evidence is committed alongside this report in
[`docs/test-evidence/`](test-evidence/): the scripts, their JSON results, and 16 browser
screenshots.

## Summary

| Layer | What ran | Result |
|---|---|---|
| Automated unit / integration (per service, H2 + MockMvc) | `./mvnw clean verify` across all 9 Maven modules | **216 / 216 pass**, build exit 0 |
| System / end-to-end (API) | `test-evidence/system_test.py` — 55 steps through the Gateway against the live local stack | **55 / 55 pass** |
| UI (real browser) | `test-evidence/ui_test.js` — headless Chrome via Playwright driving the Angular app as admin, lecturer, student | **27 / 27 pass**, 0 browser console errors |
| Frontend build | `ng build` | succeeds, no errors |

**Defects found by testing and fixed before these final runs: 5** (details in §4). Each was
re-verified by the run that produced the numbers above.

## 1. Environment

All local, on the developer machine (macOS, JDK 17, Node 20):

- **Infrastructure** — `infra/docker-compose.infra.yml`, freshly provisioned:
  PostgreSQL 15 (one `ucms` database, 7 schemas, 7 schema-scoped roles created by
  `init-schemas.sh`), Redis 7, RabbitMQ 3.13, MinIO (Chainguard build — see §4 D-5),
  Mailpit (SMTP catcher).
- **Services** — the eight packaged jars run with `java -jar` (as in production), each on
  its own port, wired to the infrastructure above. Script: `scratchpad start-stack.sh`
  (environment variables equivalent to `infra/docker-compose.deploy.yml`).
- **Frontend** — `ng serve` with the dev proxy to the Gateway on :8082.
- Flyway applied every migration on the empty database: identity_access v7, base v4,
  academic v7, enrollment v1, assignment_grading v1, notification v1, analytics v2.
- RabbitMQ topology verified live: `notification.events` and `analytics.events` each with 1
  consumer, bound to `ucms.events` (`enrollment.#`, `grading.#`, `identity.#`, plus
  `analytics.#` for notification), each with a dead-letter queue.

**Not tested here:** the production server. Nothing was deployed (per the standing
branch + PR rule). The production deploy also needs the steps in `remaining-issues.md` §1.

## 2. Automated test suites (216 tests)

| Module | Tests | Covers |
|---|---|---|
| identity-access-service | 47 | auth, users, roles (incl. G-03 collision), RBAC assignments, profile, logout |
| base-service | 32 | reference data CRUD, uniqueness (G-06), cross-service delete guard (G-05) |
| academic-structure-service | 81 | calendar, programs, curriculum, catalog CRUD, offerings, permission overrides (G-09) |
| enrollment-service | 18 | flow-01 (enroll, full → waitlist, drop → promote), flow-03 (program registration), ownership rules, roster, catalog, student search |
| assignment-grading-service | 13 | assignment lifecycle, submissions (deadline, enrollment, resubmit), flow-02 grading, GPA, class average |
| notification-service | 9 | event → notification mapping, dedupe, preferences, fan-out, email after commit, scheduled-report email |
| analytics-reporting-service | 16 | role dashboards, CSV/PDF reports, CSV formula neutralisation, schedules (incl. time-zone maths) |

## 3. System test — 55 steps (end to end, through the Gateway)

Full table with actual values: `test-evidence/system-test-results.json`. Grouped:

| Area | Steps | What was proven |
|---|---|---|
| Identity | ST-01–04 | Login via Gateway; Gateway rejects no-token requests (401); admin creates 5 accounts, each signs in with its one-time password; `identity.user-created` produces a welcome notification |
| Academic | ST-05–07b | New `/course-offerings` lookup resolves capacity, lecturer, open state; an offering in a semester whose enrollment window closed (2025-09-15) is reported closed and blocks students (409 `NOT_OPEN`) |
| Enrollment | ST-08–17 | flow-03 program registration (and duplicate → 409, lecturer → 403); flow-01: two students enroll, third gets 409 `COURSE_FULL`, joins waitlist; duplicate enroll 409; roster visible only to the teaching lecturer; a drop auto-promotes the waitlisted student, who is notified; registration log records every action and actor |
| Assignment & Grading | ST-18–29 | Lecturer creates/publishes (other lecturer 403; drafts invisible to students); publish fans out notifications; submission stored in MinIO and downloaded byte-identical; dropped student can't submit; non-submitters listed; marks > max → 400; grade saved → student notified; grade history 88.0% → A, GPA 4.0 (letter from base-service's grading scale); resubmit after grading → 409 |
| Analytics | ST-30–37 | Admin dashboard recomputed from all four sources; lecturer class average 88.0; student GPA/program; 6 seeded reports; CSV generated to MinIO and downloaded; PDF is a real PDF (`%PDF-`); weekly schedule with correct next run in Asia/Colombo; pause; reports admin-only |
| Notifications | ST-38–41 | **Live push**: a regrade arrives on the open SSE stream through the Gateway; unread count and mark-all-read; turning grade alerts off suppresses grade notifications; emails delivered to SMTP (Mailpit) |
| Security | ST-42–44 | **Logout revokes the token** (200 before, 401 "Token has been revoked" after — G-11); malformed token 401; renamed `/api/base` works and `/api/reference` is gone |
| Gap fixes | ST-45–48 | G-05: deleting a department still referenced by degree programs → 409; G-01: admin creates a Department Head role and department-scopes a lecturer → `/users/me` exposes the scope → that lecturer creates a program in their department (201) but not another (403), after the 60s role-catalog sync |
| Broker resilience | ST-49 | A malformed event is retried, then dead-lettered to `notification.events.dlq` rather than redelivered forever |

## 4. Defects found during testing — all fixed and re-verified

| ID | Found by | Defect | Fix |
|---|---|---|---|
| D-1 | Code review during build-out (confirmed by ST-45) | **G-05's fix didn't work in practice.** base-service's dependents check sent a service token without an `assignments` claim; academic-structure-service's `PermissionResolver` therefore filtered `GET /degree-programs?departmentId=` to nothing, and the check always reported "no degree programs". Unit tests mocked the client, so they couldn't catch it. | Service tokens (in every service) now carry a GLOBAL Administrator assignment. |
| D-2 | System test ST-09b | **403 became 401** in the three new services. In a real servlet container Spring forwards an error to `/error` as an ERROR dispatch the JWT filter doesn't run on, so it was treated as anonymous. MockMvc skips that forward, so unit tests passed. | `dispatcherTypeMatchers(ERROR, ASYNC).permitAll()` in each service's security config. |
| D-3 | System test (ST-45 setup) | **Pre-existing seed inconsistency.** academic-structure-service's V3 migration hardcodes production department UUIDs; base-service's V3 generates random ones. Every fresh environment had degree programs pointing at departments that don't exist. | New `base-service` migration V4 aligns the two seeded departments to the well-known ids (no-op on production, where they already match). |
| D-4 | System test ST-40 (race) | Notification pushed the live event and sent the email **inside** the DB transaction, so a browser could see a notification before it committed (or one that rolled back), and SMTP held the transaction open. | Push and email now run after commit; unit test updated to assert nothing is sent before commit. |
| D-5 | Environment setup | **`minio/minio` can no longer be pulled** from Docker Hub or quay.io, so `docker-compose.infra.yml` couldn't provision MinIO on any fresh host. | Image switched to `cgr.dev/chainguard/minio` (same server, same configuration). Existing running containers are unaffected. |

Test-side mistakes I made and corrected (not product defects, listed for transparency):
the first offering was placed in a semester whose enrollment window had already closed;
`networkidle` waits never complete because the live notification stream keeps a request
open (this confirmed the stream connects); a case-sensitive text match failed on
CSS-uppercased labels; the DLQ check ran before RabbitMQ's retry back-off finished; one
report-download check didn't account for a report generated by an earlier run.

## 5. Not covered / limits of this testing

- **Production deployment** was not performed or tested (branch + PR only).
- **Load and concurrency**: the seat lock (Postgres advisory lock) was exercised only
  sequentially. No parallel-enrollment stress test was run.
- **Multiple instances**: every service ran as one instance; live push is per-instance
  (see `remaining-issues.md`).
- **Real SMTP**: email went to Mailpit, not a real mail relay.
- **Scheduled report firing**: the schedule's next-run calculation and the generate +
  announce path (`runOne`) are tested, but a real wall-clock weekly trigger wasn't awaited.
- Browsers other than Chrome; mobile layouts.

---

# Part 4 — Security hardening, load balancing, rate limiting (2026-10-29)

Changes: `implementation-change-log.md` Part 4. Every result below was executed and
observed; raw results are in `docs/test-evidence/` (`system_test_results.json`,
`container_test_results.json`, `csp_browser_test_results.json`).

## Summary

| Layer | What ran | Result |
|---|---|---|
| Unit / integration | `./mvnw verify`, all modules, JDK 17 (Testcontainers Redis for the gateway and live-push tests) | **239 / 239** |
| System (API, end to end) | `system_test.py` against the local stack — Keycloak sign-in, all 7 services, gateway | **66 / 66** |
| Edge / containers | `container_test.py` against the real deploy compose: web nginx + 2 gateway + 2 base-service replicas | **13 / 13** |
| Real browser under the production CSP | `csp_browser_test.js` (headless Chromium via the web container) | **5 / 5**, 0 CSP violations |
| Zero-downtime deploy | `rolling-deploy.sh base-service gateway` while a client called the API every 250 ms | **686 / 686 requests 200** |
| Keycloak admin MFA | `harden_realm.py --admin-mfa on/off` + sign-ins | on: admin sent to authenticator setup, student unaffected; off: both sign in |
| Dependency scan | Trivy on the built jars; `npm audit --omit=dev` | **14 critical / 64 high** (backend), **4 high** (Angular) — open, remaining-issues §5 |

New tests: gateway `GatewayEdgeTest` (5) and `RateLimitFilterTest` (2); notification
`LivePushFanoutTest` (3); identity `KeycloakErrorMappingTest` (3) and a short-password test
(1). System steps ST-56 (per-user rate limit), ST-57 (request id), ST-58 (12-character
minimum), ST-59 (Keycloak policy reasons as 400). ST-30 now accepts this run's course being
tied out of the top-5 by courses from earlier runs on the same database.

## What the container test proved (C-01 … C-13)

Security headers and strict CSP on app pages, nginx version hidden; silent-SSO page and
production build free of inline code; Keycloak admin console / master realm / health /
metrics 404 from the site; Keycloak's own CSP and `no-referrer` intact, nothing duplicated;
reads spread over both base-service replicas (12 / 12) through both gateway replicas;
**killing a replica mid-traffic: 40 / 40 requests still 200**; a service with no replica →
503 fallback (0.05 s once the breaker opened); per-user limit shared by both gateways via
Redis (10 through, 20 × 429, every allowed response checked against Redis); nginx 429s on a
login-form flood; 2 MB body refused on normal routes, accepted on the upload route; web
container can resolve neither Postgres nor any service, gateway cannot resolve Postgres;
services run as uid 100 on a read-only filesystem with every capability dropped.

## Defects found and fixed during this round

1. **Rate limiter would have run the request twice** — `switchIfEmpty` after the chain
   (which completes empty) re-ran it. Caught in review before the first test run.
2. **ShedLock timestamps 5½ hours off** — `timestamptz` columns reinterpreted ShedLock's
   UTC database time in the session zone. Locking was still consistent on one machine but
   would break between replicas with different time zones. Columns now plain `timestamp`
   (ShedLock's documented schema); unreleased migrations corrected in place.
3. **Validation errors leaked internals** in three services (full exception text with class
   and method signatures). Now just the field's message.
4. **Admin-MFA script edited the wrong subflow** — Keycloak 26's browser flow has two
   "Conditional" subflows; the script picked the Organization one, so MFA silently didn't
   apply. Now located by the OTP form inside it, and rebuilt from scratch on every run.
5. **Keycloak pages got a second, weaker `Referrer-Policy`** (and duplicated HSTS/nosniff).
6. **Containers attached to only some of their networks** (Docker Compose v2.19.1, seen
   four times: Keycloak, a gateway, both base-service replicas). A gateway without
   `ucms_app` stayed "healthy" but returned 503 for everything and skipped Redis (fail
   open). `rolling-deploy.sh` now checks and repairs attachments.
7. **Healthcheck grace too short for a cold start** — new replicas were declared unhealthy
   while still starting; the rollout correctly stopped with the old replicas serving (zero
   failed requests). Grace raised to 120 s.
8. **academic-structure cold-start race** — the `refreshWhileEmpty()` fix reported in the
   Keycloak round had never been saved to the file; added and verified.
9. **Keycloak policy rejections returned 503** ("sign-in service unavailable") instead of
   a 400 with the reason.

## Limits of this round's testing

Reduced scale (2 + 2 replicas, amd64 emulation, 3.8 GB for Docker), not the 13-replica
production layout or a realistic load profile; the edge was tested on `localhost:8091`
without the host's HTTPS proxy in front (so HSTS and the real-IP chain were checked by
configuration, not end to end); Jenkins pipeline changes were syntax-reviewed, not run.
