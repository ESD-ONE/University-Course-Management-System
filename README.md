# University Course Management System (UCMS)

A microservices-based platform for managing degree programs, enrollment, assignments,
grading, and reporting for admins, lecturers, and students.

- **Architecture & data governance:** [`docs/architecture/README.md`](docs/architecture/README.md)
- **Implementation decisions for this phase:** [`docs/ARCHITECTURE_ANALYSIS.md`](docs/ARCHITECTURE_ANALYSIS.md)

## Status

**3 of 7 services built and deployed**: Gateway + Identity & Access (auth, users, roles,
RBAC scope assignments), Academic Structure (calendar, degree programs, curriculum,
courses), and Reference Data (faculties, departments, grading scales, course
categories) — all with full CRUD, tests, and CI/CD. A full Angular frontend is built
and deployed alongside them. The remaining 4 services (Enrollment, Assignment &
Grading, Notification, Analytics & Reporting) are still planned only — see
[`docs/architecture/README.md`](docs/architecture/README.md) for the current
per-service status and [`docs/LOGIN_USERS.md`](docs/LOGIN_USERS.md) for live
environment URLs.

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

| Service | Port (local) | Port (gateway dev / server) | Schema |
|---|---|---|---|
| `gateway` | 8080 | 8082 (dev) / 8090 (server) | — |
| `identity-access-service` | 8081 | — | `ucms.identity_access` |
| `reference-data-service` | 8083 | — | `ucms.reference_data` |
| `academic-structure-service` | 8084 | — | `ucms.academic` |
| `web` (Angular frontend) | 4200 (`ng serve`) | 8091 (server) | — |

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

Then, with the tunnel open, run each service — the pattern is the same for all three,
just a different port/schema/role per `infra/init-schemas.sh`:

```bash
cd services/identity-access-service
DB_HOST=localhost DB_PORT=5434 DB_NAME=ucms \
DB_USER=identity_access_svc DB_PASSWORD=<see infra/.env.ucms-server, not committed> \
JWT_SECRET=<any long random string for local dev> \
../../mvnw spring-boot:run   # or: mvn spring-boot:run

cd services/reference-data-service
DB_HOST=localhost DB_PORT=5434 DB_NAME=ucms \
DB_USER=reference_data_svc DB_PASSWORD=<...> JWT_SECRET=<same value as above> \
../../mvnw spring-boot:run

cd services/academic-structure-service
DB_HOST=localhost DB_PORT=5434 DB_NAME=ucms \
DB_USER=academic_structure_svc DB_PASSWORD=<...> JWT_SECRET=<same value as above> \
IDENTITY_SERVICE_URI=http://localhost:8081 \
../../mvnw spring-boot:run
```

And the gateway, pointed at all three:

```bash
cd services/gateway
SERVER_PORT=8082 \
IDENTITY_SERVICE_URI=http://localhost:8081 \
REFERENCE_DATA_SERVICE_URI=http://localhost:8083 \
ACADEMIC_STRUCTURE_SERVICE_URI=http://localhost:8084 \
mvn spring-boot:run
```

And the frontend, proxying `/api` to the gateway above via `web/proxy.conf.json`:

```bash
cd web
npm install
ng serve   # http://localhost:4200
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

Jenkins (already running on the shared Contabo host, same host the app deploys to) builds,
tests, and deploys via the root [`Jenkinsfile`](Jenkinsfile) — a multibranch pipeline (job
`UCMS`), same pattern as the existing `TravelSriLankaNow` job on that instance, polling
every 5 minutes since the host has no inbound webhook access. `./mvnw verify` runs on every
branch that has a `Jenkinsfile`, followed by a frontend build in a throwaway `node:20-alpine`
container (the Jenkins host itself has no Node toolchain); the `Deploy` stage (local shell,
no SSH hop needed since Jenkins and the target host are the same machine) only runs on
`develop`, `release`, and `main`, and covers all 4 backend services plus the frontend.

## Project layout

```
services/
  gateway/                      Spring Cloud Gateway — single entry point, routes to services
  identity-access-service/      Auth, users, roles, RBAC scope assignments — schema "identity_access"
  reference-data-service/       Faculties, departments, grading scales, course categories — schema "reference_data"
  academic-structure-service/   Calendar, degree programs, curriculum, courses — schema "academic"
web/
  Angular SPA — served by its own nginx container in production, reverse-proxying /api/
  to the gateway; ng serve + proxy.conf.json does the same for local dev
infra/
  docker-compose.infra.yml   Postgres (ucms + per-service schemas/roles) / Redis / RabbitMQ / MinIO
  init-schemas.sh            Creates each service's schema and schema-scoped database role
  docker-compose.deploy.yml  Deploys app services + frontend onto the shared host's ucms_default network
docs/
  architecture/               Mermaid diagrams, ER schemas, sequence flows, data governance standard
  ARCHITECTURE_ANALYSIS.md    Why this phase was scoped and built the way it was
  LOGIN_USERS.md               Live environment URLs and seeded accounts
```
