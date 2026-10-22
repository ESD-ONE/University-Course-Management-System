# UCMS — Implementation Architecture Analysis

This document bridges the design artifacts in [`docs/architecture/`](architecture/README.md)
(7-service decomposition, ER diagrams, sequence flows, data governance standard) to the
actual implementation choices made in this repository, and records why each one was made.

## Implementation Status Update (2026-10-17)

Since this document was written, the "one vertical slice" framed below has grown:
2 more services have since been added on top of Gateway + Identity & Access.
Academic Structure (Academic Years/Semesters, Degree Programs, Course Catalog,
Curriculum, Course Offerings) and Reference Data (Faculties, Departments,
Grading Scales, Course Categories) are now both built — full CRUD, migrated,
tested, and deployed — following the same template this document describes for
Identity & Access. That's 3 of 7 services now implemented; Enrollment, Assignment
& Grading, Notification, and Analytics & Reporting remain planned-only, with no
code. A full Angular frontend and a Jenkins multibranch CI/CD pipeline (building
and testing every branch, deploying develop/release/main) also now exist, serving
all 3 services plus the gateway and frontend as Docker containers on shared
infrastructure. The landmine and conventions this document records for Identity &
Access (test naming, data governance standard, deployment verification) carried
over to the two newer services as described. See `docs/architecture/README.md`
for current per-service status and `docs/LOGIN_USERS.md` for live environment
URLs.

## 1. Scope of this phase

Building all 7 services in one pass isn't a sound way to prove an architecture — any
mistake in the shared conventions (auth, auditing, error shapes, CI, deployment) gets
multiplied by seven before anyone notices it. Instead, this phase implements one
**vertical slice** end to end — Gateway + Identity & Access — covering:

- a real deployable service, not a stub
- its own schema (`identity_access`, inside the shared `ucms` database), migrated with
  Flyway, matching [`tables-01-identity-access.mmd`](architecture/tables-01-identity-access.mmd)
  exactly
- the full [data governance standard](architecture/DATA_GOVERNANCE.md) (optimistic
  locking via JPA `@Version`, audit columns, soft delete) — proven here, then repeated
  mechanically for the remaining 6 services
- authentication end to end, matching [`flow-04-login.mmd`](architecture/flow-04-login.mmd)
  branch for branch (valid credentials, wrong credentials, suspended account)
- CI/CD via the existing Jenkins instance, and a real deployment to shared infrastructure,
  verified live (login, authenticated profile lookup, admin listing, unauthenticated
  rejection all exercised against the deployed containers, not just local tests)

### Landmine for the next service: test class naming

Name test classes `*Test.java`, never `*IT.java`. Surefire's default include pattern
(bound to the `test` phase, which every `mvn verify`/`package`/CI run goes through) is
`**/*Test.java` — `*IT` is Failsafe's convention, for a plugin this project never
configured. Both `AuthFlowIT` and `UserManagementIT` were named this way initially and
Surefire silently matched and ran **zero** tests every single time — `mvn test` reported
`BUILD SUCCESS` because there was nothing to fail, not because anything was verified.
This went unnoticed for several commits. If a class is added as `*IT` again, check for
`[INFO] Tests run: 0` — a 0.4s "no-op" surefire phase (no `Tests run:` line, no
`T E S T S` banner at all) is the tell, not just the exit code.

The remaining 6 services (Academic Structure, Enrollment, Assignment & Grading,
Notification, Analytics & Reporting, Reference Data) follow the same template in
subsequent iterations — same module layout, same audit base class, same Flyway +
Spring Security + JWT pattern — once this slice is validated in production.

## 2. Technology choices

| Decision | Choice | Why |
|---|---|---|
| Backend framework | Spring Boot 3 / Java 17 | Matches this team's existing Java/Maven tooling; enterprise-standard DI, JPA, Flyway, Actuator out of the box. |
| Gateway | Spring Cloud Gateway | Same JVM ecosystem as every service behind it; declarative route config, no separate language/runtime to operate. |
| Persistence | One shared PostgreSQL instance and database (`ucms`), one schema per service, each with its own schema-scoped database role | Matches the architecture's core non-negotiable — no service ever has a real FK into another service's data — without the operational overhead of running a separate Postgres instance per service. See §4 and `DATA_GOVERNANCE.md`. |
| Optimistic locking | JPA `@Version` on a shared `Auditable` base class | `@Version` is JPA's native mechanism for exactly the `version` column semantics `DATA_GOVERNANCE.md` specifies — no custom SQL needed. |
| Auth | Self-issued JWT (HS256), `Authorization: Bearer` | Matches `flow-04-login.mmd`; stateless, so the Gateway doesn't need session affinity. |
| User provisioning | Admin-created only, admin-assigned role | Matches the earlier, deliberate UX decision to reject a self-service "sign in as" role picker. |

### Known scope gap, called out rather than hidden

`flow-04-login.mmd` shows the Gateway caching validated tokens in Redis so later
requests don't re-hit Identity & Access per call. This slice does **not** implement
that yet — `JwtAuthFilter` validates the JWT signature locally in each service instead,
which is correct but skips the caching optimization. That's deliberate: proving the
auth *contract* (issuance, validation, the three login branches) first, and adding the
Gateway-side cache once a second service exists to make the latency trade-off visible.

## 3. Branching strategy

Git-flow, adapted to a brand-new repository:

- **`main`** — always deployable; what's actually running wherever "production" ends up.
- **`release`** — release-stabilization branch, cut from `develop` when a set of
  services is ready to harden; merges forward into `main`.
- **`develop`** — integration branch; every finished slice merges here first.
- **`feature/*`** — one branch per unit of work, cut from `develop` (this slice lived on
  `feature/identity-access-vertical-slice`).

## 3a. CI/CD: Jenkins, not GitHub Actions

The shared Contabo host already runs Jenkins (snap install, `JENKINS_HOME=/var/snap/jenkins/5108`)
with an existing multibranch pipeline job (`TravelSriLankaNow`) reading a `Jenkinsfile`
from its repo root. This repo follows the same pattern instead of introducing a second
CI system: the root [`Jenkinsfile`](../Jenkinsfile) runs `./mvnw verify` on every branch
and, only on `develop`/`release`/`main`, deploys — copying the built jars and
`infra/docker-compose.deploy.yml` into `/opt/ucms-app` and running `docker compose up
--build`. No SSH hop is needed for deploy: Jenkins and the deploy target are the same
host.

The Maven Wrapper (`./mvnw`) is committed so the pipeline doesn't depend on whatever
Maven version happens to be on the Jenkins host — at the time of writing, the host has
JDK 17 system-wide but no system Maven at all.

**Known gap:** the Jenkins multibranch job itself (pointing at this repo, mirroring the
existing `TravelSriLankaNow` job's `GitSCMSource` + `BranchDiscoveryTrait` config) could
not be created automatically — the provided Jenkins admin credentials returned `401
Unauthorized` against both the UI login and the REST API. Creating the job manually
(Jenkins → New Item → Multibranch Pipeline → Git source →
`https://github.com/ESD-ONE/University-Course-Management-System.git` → script path
`Jenkinsfile`) is the one manual step left once Jenkins access is sorted out.

## 4. Infrastructure topology

Dev/shared infrastructure (PostgreSQL, Redis, RabbitMQ, MinIO) runs on the existing
Contabo host, in its own isolated stack (`/opt/ucms`, containers prefixed `ucms-*`),
alongside the unrelated `proj001` stack already running there — different container
names, different Docker volumes, different ports, nothing shared. Every UCMS
infrastructure port is bound to `127.0.0.1` on the server only; there is no public
internet exposure. Local development reaches it over an SSH local-port-forward:

```
ssh -N \
  -L 5434:127.0.0.1:5434 \
  -L 6380:127.0.0.1:6380 \
  -L 5673:127.0.0.1:5673 -L 15673:127.0.0.1:15673 \
  -L 9002:127.0.0.1:9002 -L 9003:127.0.0.1:9003 \
  root@169.58.12.239
```

Application services (this slice: `identity-access-service`, `gateway`), once deployed,
run as separate containers on the same host, joined to the infra stack's Docker network
(`ucms_default`) and addressed by container name — not through the published
localhost-only ports, which exist only for the SSH-tunnelled local dev workflow.

See [`infra/docker-compose.deploy.yml`](../infra/docker-compose.deploy.yml) for the
exact deployment definition and
[`infra/docker-compose.infra.yml`](../infra/docker-compose.infra.yml) /
[`infra/init-schemas.sh`](../infra/init-schemas.sh) for the Postgres/Redis/RabbitMQ/MinIO
stack itself.

### One database, one schema per service — not one database per service

The architecture diagrams originally showed a separate PostgreSQL *database* per
service. This repository uses a single shared instance and database (`ucms`) with a
dedicated **schema** per service instead — `identity_access` for this slice, with
`academic`, `enrollment`, `assignment_grading`, `notification`, `analytics`, and
`reference_data` pre-created and waiting for their services.

The isolation property that actually matters — no service can ever read or write
another service's tables — is preserved by giving each service its own PostgreSQL
login role, granted privileges on its own schema only (`identity_access_svc` can use
`identity_access`; it has no grant at all on `academic`, verified with
`has_schema_privilege('identity_access_svc', 'academic', 'USAGE')` returning `false`).
A schema boundary plus a schema-scoped role enforces the same thing a separate database
would, at the connection/credential level, not just by convention — without the
operational cost of running, backing up, and monitoring seven Postgres instances for a
system at this scale. Revisit if a service's data genuinely needs to scale or fail
independently of the others.

One practical consequence worth flagging: extensions (e.g. `pgcrypto`, used for
`gen_random_uuid()`) are database-scoped in Postgres, not schema-scoped, and a
schema-scoped role deliberately doesn't have `CREATE EXTENSION` privilege. Extensions
are created once, up front, by the admin role in `init-schemas.sh` — a service's own
Flyway migrations never attempt it.

### Messaging (RabbitMQ) — provisioned, not wired in

`ucms-rabbitmq` runs as part of the infra stack (`infra/docker-compose.infra.yml`) and
is reachable from local dev over the SSH tunnel above (ports 5673 / 15673, the latter
being the management UI). That is the extent of what exists today: **no service has
`spring-boot-starter-amqp` on its classpath, no `RabbitTemplate` publishes anything, and
no `@RabbitListener` consumes anything** — verified by grepping every `pom.xml` and
every `.java` file under `services/` for `amqp`/`rabbit`, case-insensitive, with zero
hits. The container is running and empty; it was provisioned ahead of the services that
will actually use it, not left over from something that was removed.

**What it's for, once Enrollment, Assignment & Grading, Notification, and Analytics &
Reporting exist:** the design's asynchronous backbone, for exactly the cases where a
request shouldn't block on a side effect. The pattern (drawn as dotted edges through a
"Message Broker" node in `docs/architecture/high-level-architecture.mmd` and
`microservices-architecture.mmd`, and spelled out step-by-step in
`docs/architecture/flow-01-student-enrollment.mmd` / `flow-02-grading.mmd`, both
explicitly marked PLANNED):

1. A service finishes a significant write — e.g. Enrollment registers a student in a
   course.
2. It **publishes** a domain event (e.g. `StudentEnrolled`) to a RabbitMQ exchange via
   `RabbitTemplate.convertAndSend(...)`, carrying just the IDs involved, and returns its
   HTTP response immediately — publishing doesn't wait for anyone to receive it.
3. RabbitMQ routes that message to every queue bound to the exchange. Notification has
   a queue bound for `StudentEnrolled`; so does Analytics, for a different reason (both
   consume the same event independently, per `flow-02-grading.mmd`'s note on this).
4. Each consumer handles it on its own schedule via a `@RabbitListener`-annotated
   method — a slow or briefly-down Notification service never delays Enrollment's
   response to the student, and never blocks Analytics either.

This is why `docs/architecture/README.md` and the two system-level diagrams draw
Message-Broker edges as dashed/planned rather than solid: the broker and the pattern are
real and designed, but until a producer and a consumer both exist on opposite ends of at
least one queue, nothing actually flows through it.
