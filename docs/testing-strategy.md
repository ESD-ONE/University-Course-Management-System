# UCMS Test Strategy

| | |
|---|---|
| Status | Final |
| Author | Nimanthi Jayathilake |
| Date | 2026-09-29 |
| Applies to | All backend services, the gateway, the Angular frontend and the CI/CD pipeline |

## 1. Purpose

This document defines how the University Course Management System (UCMS) is tested. It
sets the test levels, the tools, the environments, the pipeline stages and the rules for
releasing. Every team member who writes or reviews code follows it.

## 2. Objectives

- Find defects at the lowest and cheapest level that can catch them.
- Prove that each service works alone, and that the services work together.
- Protect the core user journeys for admins, lecturers and students on every release.
- Keep the pipeline fast enough that developers get feedback within minutes.
- Give a clear, measurable rule for when a release can go to production.

## 3. Scope

**In scope**

- The eight backend modules: gateway, identity-access, base, academic-structure,
  enrollment, assignment-grading, notification and analytics-reporting.
- The Angular web application.
- Integration with PostgreSQL, RabbitMQ, Redis, MinIO and Keycloak.
- The build, deploy and rollback process.

**Out of scope**

- Testing the third-party products themselves (Keycloak, PostgreSQL, RabbitMQ, MinIO).
  Only our configuration and our use of them is tested.
- Real email delivery through an external mail provider.

## 4. Approach

Testing is risk-based and follows the test pyramid: many fast tests at the bottom, and a
small number of slow tests at the top.

### 4.1 Risk areas

These areas get the deepest testing at every level.

| Risk area | What can go wrong |
|---|---|
| Enrollment | Two students take the last seat; the waitlist promotes the wrong student; enrollment happens outside the window |
| Grading | Wrong marks, letter grade or GPA; a grade is changed after it is final |
| Access control | A user sees or changes data outside their role or department |
| File submissions | A file is lost, is too large, or is downloaded by the wrong user |
| Notifications | A message is missed, duplicated, or sent to the wrong user |
| Reports | Wrong figures; unsafe content in an exported file |

### 4.2 Principles

- The developer who writes a change also writes its unit and API tests.
- A test runs against the real technology whenever the behaviour depends on it (for
  example, SQL runs against PostgreSQL).
- Tests are independent. Each test creates its own data and can run in any order.
- Every fixed defect gets an automated regression test.
- A failing test blocks the merge. Tests are fixed or removed, never ignored.

## 5. Test levels

| Level | Purpose | Tooling | When it runs |
|---|---|---|---|
| Unit | Pure logic in one class or function | JUnit 5, Mockito, Jasmine/Karma | Every commit |
| Service API | Each endpoint of one service: roles, validation, error codes | Spring Boot Test, MockMvc | Every commit |
| Integration | One service with its real dependencies | Testcontainers | Every pull request |
| Contract | The request, response and event formats shared by two services | Spring Cloud Contract | Every pull request |
| End-to-end | Full user journeys through the browser | Playwright | Nightly and before release |
| Non-functional | Load, resilience, security, accessibility | k6, OWASP ZAP, axe | Before release |
| Smoke | The deployed system is alive and reachable | Scripted HTTP checks | After every deploy |

### 5.1 Unit tests

- **Backend:** calculation and rule logic, such as GPA, letter grades, report schedules
  and rate-limit calculations.
- **Frontend:** services, guards, interceptors, components and shared UI controls.
- External calls are replaced with mocks. A unit test never needs a network or a
  database.

### 5.2 Service API tests

- Every endpoint is tested for the success case, validation errors, and "not found".
- Every endpoint is tested with each role, with no token, and with a user from another
  department.
- Other services are mocked at this level.

### 5.3 Integration tests

Each service is tested with its real dependencies running in containers.

| Dependency | What is tested |
|---|---|
| PostgreSQL | Flyway migrations from an empty schema, repository queries, constraints, locking |
| RabbitMQ | Publishing and consuming events, redelivery, duplicate handling |
| Redis | Token revocation, rate limiting, live-push fan-out across replicas |
| MinIO | Upload, download, size limit and file type handling |
| Keycloak | Login, token roles, logout revocation, password change, user sync |

Concurrency tests belong here. They run parallel requests against PostgreSQL to prove
rules such as "the last seat goes to one student only".

### 5.4 Contract tests

- **HTTP contracts:** each service that calls another service has a contract for that
  call. The provider's build fails if it breaks the contract.
- **Event contracts:** each event type on the `ucms.events` exchange has a contract
  shared by the publisher and every consumer.

### 5.5 End-to-end tests

The suite stays small: about 10 to 15 journeys on the most important paths.

| Role | Journey |
|---|---|
| Admin | Create a user; set up a faculty, department, program, course and offering |
| Student | Register in a program, enroll in a course, join the waitlist, submit an assignment, view grades |
| Lecturer | Publish an assignment, grade it, and the student receives a notification |
| Admin or lecturer | Generate and download a CSV and a PDF report |
| All roles | Each role sees only its own menu items and data |

### 5.6 Non-functional tests

| Type | What is tested |
|---|---|
| Load | An enrollment window opening, with the gateway rate limits switched on |
| Resilience | A service or replica stops: gateway fallback, read retry, rolling deploy with no dropped requests |
| Security | Baseline ZAP scan, access control across roles and departments, token tampering, CSV formula injection, dependency and image scans |
| Accessibility | The main screens against WCAG 2.1 AA |

### 5.7 Smoke tests

After each deploy: log in, open the dashboard, and call one endpoint per service through
the gateway. A failed smoke test triggers a rollback.

## 6. Environments

| Environment | Purpose | Tests that run there |
|---|---|---|
| Local | Developer feedback | Unit, service API, integration |
| CI (Jenkins) | Gate for every build | Unit, service API, integration, contract, static analysis, security scans |
| Staging | Production-like check before release | End-to-end, non-functional, smoke |
| Production | Live system | Smoke only |

A release deploys to staging first, and to production only after the staging checks
pass.

## 7. Test data

- Each automated test creates the data it needs and does not depend on other tests.
- Staging has a seeded, repeatable data set with at least one user for each role:
  admin, department head, lecturer and student.
- Production data is never used in tests.
- Test credentials and keys are for test use only and are never valid in production.

## 8. CI/CD pipeline and quality gates

| Stage | What runs | Gate |
|---|---|---|
| 1. Build and test (backend) | Unit, service API, integration and contract tests | All tests pass |
| 2. Build and test (frontend) | Frontend unit tests with coverage, production build | All tests pass |
| 3. Code quality | SonarQube analysis for backend and frontend | Quality gate passes; coverage on new code is at least 80% |
| 4. Security scan | Dependency scan, npm audit, container image scan | No high or critical finding that has a fix |
| 5. Deploy to staging | Rolling deploy, then smoke test | Smoke test passes |
| 6. Staging checks | End-to-end and non-functional tests | All pass |
| 7. Deploy to production | Rolling deploy, then smoke test | Smoke test passes, otherwise roll back |

A test that needs Docker fails the build if Docker is not available. It is never skipped
silently.

## 9. Roles and responsibilities

| Activity | Owner |
|---|---|
| Unit and service API tests | The developer who writes the change |
| Integration and contract tests | The developer, reviewed by QA |
| End-to-end and non-functional tests | QA |
| Pipeline and quality gates | QA lead with DevOps |
| Release sign-off | QA lead |

## 10. Defect management

| Severity | Meaning | Release rule |
|---|---|---|
| Critical | Data loss, security breach, or a core journey is blocked | Blocks release |
| High | A main feature fails with no workaround | Blocks release |
| Medium | A feature fails but a workaround exists | Fix in the next release |
| Low | Cosmetic or minor issue | Backlog |

Each defect records the steps to reproduce, the expected and actual result, the
environment and the build number.

## 11. Entry and exit criteria

**A change can be merged when:**

- It has unit and API tests for the new behaviour.
- All pipeline stages up to the security scan pass.
- A second developer has reviewed it.

**A release can go to production when:**

- All unit, API, integration and contract tests pass.
- The end-to-end journeys pass on staging.
- No critical or high defects are open.
- No high or critical vulnerability with an available fix is open.
- Coverage on new code is at least 80%.
- The load test meets the targets in section 13.
- The QA lead has signed off.

## 12. Reporting

- Jenkins publishes test results and coverage for every build.
- SonarQube shows the quality and coverage trend for backend and frontend.
- QA writes a short test summary for each release: what was tested, the results, the
  open defects and the known risks.

## 13. Key decisions

| Topic | Decision |
|---|---|
| Coverage threshold | 80% line coverage on new code, for backend and frontend |
| Contract-testing tool | Spring Cloud Contract, for both HTTP calls and events |
| Load test profile | 500 concurrent users during an enrollment window, held for 15 minutes |
| Load test targets | 95% of reads under 500 ms, 95% of writes under 1 second, error rate under 1% |
| Browsers for end-to-end tests | Latest Chrome, Firefox, Safari and Edge on desktop |
