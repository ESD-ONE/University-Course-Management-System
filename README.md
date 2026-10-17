# University Course Management System (UCMS)

A microservices-based platform for managing degree programs, enrollment, assignments,
grading, and reporting for admins, lecturers, and students.

- **Architecture & data governance:** [`docs/architecture/README.md`](docs/architecture/README.md)
- **Implementation decisions for this phase:** [`docs/ARCHITECTURE_ANALYSIS.md`](docs/ARCHITECTURE_ANALYSIS.md)

## Status

First vertical slice: **Gateway + Identity & Access service**, implemented end to end
(auth, users, roles, CI, deployment). The remaining 6 services follow the same
template in subsequent iterations.

## Branching

- `main` — deployable
- `release` — stabilization branch cut from `develop`
- `develop` — integration branch, target for all feature PRs
- `feature/*` — one branch per unit of work, cut from `develop`

## Services

One shared PostgreSQL database (`ucms`), one schema per service — not a database per
service. See [`docs/architecture/DATA_GOVERNANCE.md`](docs/architecture/DATA_GOVERNANCE.md)
for why, and [`infra/init-schemas.sh`](infra/init-schemas.sh) for how each service's
isolation is enforced (its own database role, scoped to its own schema only).

| Service | Port (local) | Schema |
|---|---|---|
| `gateway` | 8080 | — |
| `identity-access-service` | 8081 | `ucms.identity_access` |

## Running locally

Infrastructure (Postgres, Redis, RabbitMQ, MinIO) runs on the shared Contabo dev
server, not on your machine. Open an SSH tunnel first:

```bash
ssh -N \
  -L 5434:127.0.0.1:5434 \
  -L 6380:127.0.0.1:6380 \
  -L 5673:127.0.0.1:5673 -L 15673:127.0.0.1:15673 \
  -L 9002:127.0.0.1:9002 -L 9003:127.0.0.1:9003 \
  root@169.58.12.239
```

Then, with the tunnel open, run the Identity & Access service:

```bash
cd services/identity-access-service
DB_HOST=localhost DB_PORT=5434 DB_NAME=ucms \
DB_USER=identity_access_svc DB_PASSWORD=<see infra/.env.ucms-server, not committed> \
JWT_SECRET=<any long random string for local dev> \
../../mvnw spring-boot:run   # or: mvn spring-boot:run
```

And the gateway, pointed at it:

```bash
cd services/gateway
IDENTITY_SERVICE_URI=http://localhost:8081 mvn spring-boot:run
```

Seed admin login (rotate immediately after first use):

```
email: admin@ucms.local
password: ChangeMe123!
```

## Build & test

```bash
./mvnw verify
```

## CI/CD

Jenkins (already running on the shared Contabo host) builds, tests, and deploys via the
root [`Jenkinsfile`](Jenkinsfile) — a multibranch pipeline, same pattern as the existing
`TravelSriLankaNow` job on that instance. `./mvnw verify` runs on every branch; the
`Deploy` stage (local shell, no SSH hop needed since Jenkins and the target host are the
same machine) only runs on `develop`, `release`, and `main`.

## Project layout

```
services/
  gateway/                   Spring Cloud Gateway — single entry point, routes to services
  identity-access-service/   Auth, users, roles — database "ucms", schema "identity_access"
infra/
  docker-compose.infra.yml   Postgres (ucms + per-service schemas/roles) / Redis / RabbitMQ / MinIO
  init-schemas.sh            Creates each service's schema and schema-scoped database role
  docker-compose.deploy.yml  Deploys app services onto the shared host's ucms_default network
docs/
  architecture/               Mermaid diagrams, ER schemas, sequence flows, data governance standard
  ARCHITECTURE_ANALYSIS.md    Why this phase was scoped and built the way it was
```
