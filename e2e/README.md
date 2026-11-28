# End-to-end tests

These tests drive a real browser through the core user journeys, against the whole system
running together: the web app, the gateway, Keycloak and all seven services.

They do not run in the main Jenkins build. They run on GitHub Actions
(`.github/workflows/e2e.yml`):

- every night at 01:00 UTC, against `develop`
- on a pull request that changes the tests or their stack
- on demand: Actions tab, "End-to-end tests", "Run workflow"

A failed run keeps the Playwright report (screenshots and traces) and the container logs
as artifacts for 14 days.

## What you need

- Docker, with about 6 GB of memory available to it
- JDK 17 or later, and Node 20 or later

## Run the tests

```bash
# 1. Start the system (a few minutes the first time). It is at http://localhost:8091.
../infra/e2e-up.sh

# 2. Install the test tools (once).
npm ci
npx playwright install chromium firefox webkit

# 3. Run everything, on all three browsers.
npx playwright test

# 4. Stop the system and delete its data.
../infra/e2e-down.sh
```

Useful variations:

```bash
npx playwright test --project=chromium        # one browser
npx playwright test student-enrollment        # one journey
npx playwright test --ui                      # watch and step through
npx playwright show-report                    # open the last report
```

### Firefox does not start

On macOS 27, Firefox fails to start under Playwright 1.65.1. Run the other two there:

```bash
npx playwright test --project=chromium --project=webkit
```

Firefox runs on Linux, which is where the scheduled run is.

### Port 8091 is taken

Start the system on another port and tell the tests where it is:

```bash
E2E_WEB_PORT=8191 ../infra/e2e-up.sh
E2E_BASE_URL=http://localhost:8191 npx playwright test
E2E_WEB_PORT=8191 ../infra/e2e-down.sh
```

### After changing application code

```bash
../infra/e2e-up.sh --rebuild
```

## The test system

`infra/docker-compose.e2e.yml` starts a complete, separate copy of UCMS with its own
containers, network and data. It can run beside the real stack on the same machine. Its
secrets are throwaway values in `infra/e2e.env`.

It is built from the same images and `Dockerfile`s as production. When you add a service
or a setting to `infra/docker-compose.deploy.yml` or `infra/docker-compose.infra.yml`, add
it here too.

On an ARM machine (Apple Silicon) the Java services run under emulation, because their
base image exists for amd64 only. They start more slowly but behave the same.

Two things differ from production on purpose, both because every test browser comes from
one machine:

- The web container's per-IP limits (sign-in, token exchange, live streams) are raised.
  `e2e-up.sh` generates its nginx file from `web/nginx.conf` with only those numbers
  changed. The per-user limits in the gateway are unchanged.
- Each service runs as one replica.

## How the tests are organised

| Path | What it is |
|---|---|
| `global-setup.ts` | Runs once per run. Signs in as the seeded admin, creates a second admin for data setup, a lecturer and two students, registers the students in a degree program, and saves a signed-in session for each. |
| `fixtures/test.ts` | What a spec imports. Gives it a page already signed in as a role (`adminPage`, `lecturerPage`, `studentPage`, `otherStudentPage`) and an API client for a role (`api('lecturer')`). |
| `fixtures/ui.ts` | Helpers for controls that need care, such as the row "..." menu. |
| `fixtures/seed.ts` | Creates the data a test needs through the API: a course offering, an enrollment, an assignment. |
| `tests/*.spec.ts` | One file per journey. |

| Spec | Journey |
|---|---|
| `smoke` | A visitor is asked to sign in; each role reaches its dashboard |
| `admin-setup` | The admin creates a student account, a faculty with a department, and a course |
| `student-enrollment` | A student enrolls, a second joins the waitlist and is promoted when the first drops |
| `assignment-lifecycle` | A lecturer publishes an assignment, a student submits, the lecturer grades, the student sees the grade |
| `notifications` | A student is told about a new assignment while the app is open |
| `reports` | The admin generates and downloads a CSV and a PDF report |
| `access-control` | Each role's menu, and what happens when a role reaches for another role's page or API |
| `session` | After signing out, the user must sign in again and the old token is rejected |

## Rules for new tests

- **Create your own data.** Use `fixtures/seed.ts` and give everything a unique name with
  `unique()`. Tests run in parallel against one system and must not see each other's data.
- **Find elements the way a user does:** by role, label and visible text. If a control has
  no accessible name, add an `aria-label` to the app instead of using a CSS selector.
- **Never wait a fixed time.** Assert on what the user would see; Playwright waits for it.
- **Check the saved result, not the confirmation message.** Reload or open the list and
  look for the record.
- **Do not sign out a shared account.** The accounts above are shared by every test. A
  test that signs out creates its own account (see `session.spec.ts`).
- **Mind the limits a real user has.** The gateway limits each user (for example six
  report runs a minute), and the tests run in parallel as the same few users. A test
  that needs a quota or a clean notification list to itself creates its own account
  (see `reports.spec.ts` and `notifications.spec.ts`). Data setup goes through
  `api('admin')`, which is a separate admin from the one `adminPage` signs in as.

## When a test fails

The report (`npx playwright show-report`) has a screenshot of the failure. In CI, or with
`--trace on`, it also has a trace you can step through.

If setup fails before any test runs, the system is usually not up or not on the address
the tests use. Check `docker compose -f ../infra/docker-compose.e2e.yml ps` and
`E2E_BASE_URL`.
