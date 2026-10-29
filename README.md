# University Course Management System (UCMS)

A microservices-based platform for managing degree programs, enrollment, assignments,
grading, and reporting for admins, lecturers, and students.

- **Architecture & data governance:** [`docs/architecture/README.md`](docs/architecture/README.md)
- **Implementation decisions for this phase:** [`docs/ARCHITECTURE_ANALYSIS.md`](docs/ARCHITECTURE_ANALYSIS.md)

## Status

All seven microservices from the architecture are built, plus the Gateway and the Angular
frontend — see [`docs/system-architecture-analysis.md`](docs/system-architecture-analysis.md)
and the test evidence in [`docs/test-execution-report.md`](docs/test-execution-report.md).

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
| `gateway` | 8080 (8082 in local dev) | — |
| `identity-access-service` | 8081 | `ucms.identity_access` |
| `base-service` | 8083 | `ucms.base` |
| `academic-structure-service` | 8084 | `ucms.academic` |
| `enrollment-service` | 8085 | `ucms.enrollment` |
| `assignment-grading-service` | 8086 | `ucms.assignment_grading` |
| `notification-service` | 8087 | `ucms.notification` |
| `analytics-reporting-service` | 8088 | `ucms.analytics` |

Asynchronous events go through RabbitMQ (`ucms.events`); Redis holds revoked tokens;
MinIO stores submissions and generated reports.

## Running locally

Everything can run on your machine:

```bash
# 1. Infrastructure (Postgres, Redis, RabbitMQ, MinIO, Mailpit) — needs infra/.env with
#    POSTGRES_PASSWORD and one *_SVC_PASSWORD per service (see docker-compose.infra.yml)
cd infra && docker compose -f docker-compose.infra.yml --env-file .env up -d && cd ..

# 2. Build the services (JDK 17)
./mvnw -q package -DskipTests

# 3. Start each service jar with its DB_PASSWORD and the shared JWT_SECRET, plus
#    RABBITMQ_*/REDIS_*/MINIO_* and the *_SERVICE_URI variables — the same variables as
#    infra/docker-compose.deploy.yml, pointed at localhost. Run the gateway with
#    SERVER_PORT=8082, which web/proxy.conf.json targets.

# 4. Frontend
cd web && npx ng serve --proxy-config proxy.conf.json   # http://localhost:4200
```

Mailpit's inbox (http://localhost:8025) shows the emails notification-service sends.

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
  gateway/                      Spring Cloud Gateway — routing, CORS, JWT backstop, revocation
  identity-access-service/      Auth, users, roles, RBAC scope assignments
  base-service/                 Faculties, departments, grading scales, course categories
  academic-structure-service/   Calendar, programs, curriculum, catalog, course offerings
  enrollment-service/           Program registration, course enrollment, waitlist
  assignment-grading-service/   Assignments, submissions (MinIO), grades, GPA
  notification-service/         In-app + email notifications, live push (SSE)
  analytics-reporting-service/  Dashboards, CSV/PDF reports, scheduled reports
infra/
  docker-compose.infra.yml   Postgres (ucms + per-service schemas/roles) / Redis / RabbitMQ / MinIO
  init-schemas.sh            Creates each service's schema and schema-scoped database role
  docker-compose.deploy.yml  Deploys app services onto the shared host's ucms_default network
docs/
  architecture/               Mermaid diagrams, ER schemas, sequence flows, data governance standard
  ARCHITECTURE_ANALYSIS.md    Why this phase was scoped and built the way it was
```
