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
- CI (build + test) and a real deployment to shared infrastructure

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
