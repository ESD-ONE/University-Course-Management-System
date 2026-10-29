# UCMS — Remaining Issues

**Date:** 2026-10-26 · after the full build-out (`implementation-change-log.md` Part 3)
· **updated 2026-10-29** after security hardening / load balancing / rate limiting (Part 4)

Everything here is still open, each item with the reason it's open. Items closed by this
round are listed at the end so the history stays readable.

## 1. Before this can go live (manual, not automatable from the repo)

1. **Not merged or deployed.** All work is on branch `docs/system-audit-phase1`, in the
   working tree (uncommitted, pending your confirmation per the standing git rule).
2. **Four new database roles on the live database** — `init-schemas.sh` can't add them to
   an existing data directory. SQL: change log, step A.
3. **Base-service schema/role rename** on the live database — change log, step D.
4. **New `.env` variables** on the server — change log, step B.
5. **Shared HTTPS proxy limits** — the other project's nginx in front of
   `esd.azhosting.fun` has nginx's 1 MB default upload limit and buffers responses, so
   20 MB submissions and the live stream fail through the domain until its config is
   changed (change log, step C). **Now a hard prerequisite:** Part 4 closes the direct
   `:8091` address, so the domain becomes the only way in (change log Part 4, step 1).
8. **Part 4 rollout** — infra stack re-created on the new networks, the realm hardened with
   `harden_realm.py`, old `ufw` rule removed: change log Part 4, steps 2–7.
6. **Open PRs from earlier this session overlap with this branch**: #16 (domain/CORS —
   same `CORS_ALLOWED_ORIGIN` line, trivial conflict) and #17 (role lookup fix, already
   contained here as G-03 — close #17 in favour of this branch).
7. **TLS certificates have no auto-renewal** (pre-existing; flagged earlier, not actioned).

## 2. Known limitations (deliberate scope boundaries)

- **No transactional outbox.** Events publish after commit; if RabbitMQ is down at that
  moment the event is lost. The request still succeeds, but the notification is missed;
  analytics self-corrects on its 5-minute recompute.
- **Logout revocation and rate limits fail open if Redis is down** (logged): a Redis
  outage must not take down the whole site. A token is still bounded by its 5-minute
  expiry, and nginx's per-IP limits still apply. Both are enforced at the Gateway only;
  calls from inside the Docker network bypass them.
- **One host.** Replicas survive a crashed service and make deploys zero-downtime, but not
  the server going down. That needs a second host (Swarm or Kubernetes) and replicated
  Postgres. Keycloak and the web container are single instances (a deploy of `web` is a
  ~1 s blip).
- **No circuit breakers on service-to-service calls** — timeouts, a retry on another
  replica, and 503 mapping instead (change log Part 4, departure 4).
- **Refresh-token rotation is off** on purpose: it would sign out a user's other tabs.
- **New services authorize by role + ownership**, not by the scope-based permission
  matrix. Only academic-structure-service consults PermissionResolver (G-02's architectural
  question remains a product decision).
- **One grading scale for every course.** The data model has no course → grading-scale
  link, so letter grades use the institution's "Standard Percentage" scale.
- **"Credits completed"** counts a course only once its academic year is archived with a
  passing grade, because there's no explicit course-completion step. Mid-year it reads 0.
- **User Activity report** lists accounts by role and status. identity-access doesn't
  expose account creation dates, so the "over time" part of the original wireframe text
  isn't possible yet.
- **Scheduled reports** are emailed as a notification with a link to the Reports screen,
  not as an attachment.

## 3. Not covered by testing (see `test-execution-report.md` §5)

Production deployment; concurrent load on the seat lock; real SMTP delivery; a real
wall-clock scheduled-report run; browsers other than Chrome; mobile layouts. Multi-replica
behaviour was tested at reduced scale (2 gateways, 2 base-service replicas, amd64
emulation on an 8 GB laptop) — not the full 13-replica production layout, and not under
a realistic load profile (no k6/Postman performance run yet). RabbitMQ consumers now
compete across replicas; their idempotency on redelivery (each already dedupes by
`source_event_id` where it matters) wasn't re-tested multi-replica.

## 4. Environment notes

- `minio/minio` is no longer published; infra now uses `cgr.dev/chainguard/minio`.
  The production server's existing MinIO container is untouched until that stack is
  re-created.
- The local JDK default is 24, where Mockito can't instrument classes; builds and tests
  need JDK 17 (`JAVA_HOME=$(/usr/libexec/java_home -v 17)`). Jenkins already uses 17.
- `docs/test-evidence/` scripts contain local throwaway credentials (Mailpit/RabbitMQ dev
  passwords, the documented seed admin password) — not production secrets.

- Temurin publishes its Alpine JRE for amd64 only, so on an Apple-silicon Mac the service
  images run under emulation (slow to start — about 3 minutes each). Production is amd64.
- Docker Compose v2.19.1 (local) attached multi-network containers to only some of their
  networks, several times; `rolling-deploy.sh` checks and repairs this.

## 5. Security follow-ups (from Part 4)

1. **Known vulnerabilities in dependencies — highest priority.** Trivy on the built jars:
   **14 critical, 64 high** CVEs with fixes available, almost all through the Spring Boot
   3.3.4 line (Tomcat 10.1.30, Netty 4.1.113, Spring Security 6.3.3, Spring Framework
   6.1.13, Jackson 2.17, PostgreSQL driver 42.7.4, BouncyCastle 1.78). `npm audit`: Angular
   19 — 4 high, fixed only in Angular 21. Spring Boot 3.3.x and Angular 19 are past
   open-source support. Recommended: Spring Boot 3.5.x (latest) + Spring Cloud 2025.0.x,
   which clears most; a few are fixed only in Spring Framework 7 / Boot 4. Then Angular 21.
   Each is a version upgrade across every module, to be done and regression-tested as its
   own piece of work. After that, switch `SECURITY_GATE` to `'fail'` in the Jenkinsfile.
2. **Admin MFA** is built (`harden_realm.py --admin-mfa on`) and verified, but not switched
   on — confirm administrators have an authenticator app first.
3. **No malware scanning of uploads** (ClamAV sidecar was optional in the plan) and no
   file-type allow-list on submissions yet; downloads are always served as attachments with
   `nosniff`, which blocks the browser-side risks.
4. **Secrets rotation** — the seven Keycloak client secrets and the admin password have no
   rotation schedule; `.env` should be `chmod 600`, root-owned, on the server.
5. **No central log/alerting** — the request id is now in every log line, but nothing
   collects logs or alerts on 429/401 spikes or the "Redis unavailable" fail-open warnings.

## 6. Closed in this round

- **G-20** — the four planned services now exist and are tested end to end.
- **G-11** — logout now actually revokes the token (Redis + Gateway), verified by both the
  API and browser tests.
- **G-05** — the cross-service department dependents check works (it silently didn't
  before; root cause was the service token's missing assignments claim).
- **Fresh-environment seed mismatch** between base-service and academic-structure
  department ids — fixed by base-service V4.
- **G-15** — notification preferences now drive real notifications; the in-app "not sent
  yet" disclaimer is gone.
- `end-to-end-test-cases.md` staleness — superseded by the executed suites recorded in
  `test-execution-report.md` and `docs/test-evidence/`.
- **Live push across instances** (Part 4) — Redis fan-out; notification-service can now
  run several replicas.
- **No rate limiting; no load balancing; containers as root; public plain-HTTP port;
  Keycloak admin console on the public site; no security headers** — all Part 4.
- **academic-structure denying everything after a cold start** — `refreshWhileEmpty()`
  (Part 4; the earlier round's fix had never actually landed).
