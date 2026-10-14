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

| Service | Port (local) | Database |
|---|---|---|
| `gateway` | 8080 | — |
| `identity-access-service` | 8081 | `identity_db` |

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
DB_HOST=localhost DB_PORT=5434 DB_NAME=identity_db \
DB_USER=ucms_admin DB_PASSWORD=<see infra/.env.ucms-server, not committed> \
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
mvn verify
```

## Project layout

```
services/
  gateway/                   Spring Cloud Gateway — single entry point, routes to services
  identity-access-service/   Auth, users, roles — auth_db / identity_db
infra/
  docker-compose.deploy.yml  Deploys app services onto the shared host's ucms_default network
docs/
  architecture/               Mermaid diagrams, ER schemas, sequence flows, data governance standard
  ARCHITECTURE_ANALYSIS.md    Why this phase was scoped and built the way it was
```
