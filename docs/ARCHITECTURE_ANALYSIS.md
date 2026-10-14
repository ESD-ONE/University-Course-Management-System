# UCMS — Implementation Architecture Analysis

This document bridges the design artifacts in [`docs/architecture/`](architecture/README.md)
(7-service decomposition, ER diagrams, sequence flows, data governance standard) to the
actual implementation choices made in this repository, and records why each one was made.

## 1. Scope of this phase

Building all 7 services in one pass isn't a sound way to prove an architecture — any
mistake in the shared conventions (auth, auditing, error shapes, CI, deployment) gets
multiplied by seven before anyone notices it. Instead, this phase implements one
**vertical slice** end to end — Gateway + Identity & Access — covering:

- a real deployable service, not a stub
- its own database (`identity_db`), migrated with Flyway, matching
  [`tables-01-identity-access.mmd`](architecture/tables-01-identity-access.mmd) exactly
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
| Persistence | PostgreSQL per service (database-per-service) | Matches the architecture's core non-negotiable — no service ever has a real FK into another service's data. |
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
exact deployment definition.
