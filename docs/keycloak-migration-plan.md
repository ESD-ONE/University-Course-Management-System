# Keycloak OIDC Migration — Implementation Plan

**Date:** 2026-10-27 · status: proposed, not started — nothing in this plan has been implemented.

## Diagrams

Four Mermaid diagrams in `docs/architecture/` (source `.mmd` + rendered `.png`,
same convention as the rest of `docs/architecture/`) work through this plan
visually — start with the first to see the shape, then the rest as sequences:

| File | Shows |
|---|---|
| [`keycloak-01-topology.mmd`/`.png`](architecture/keycloak-01-topology.png) | Every component's relationship to Keycloak at once: the SPA's PKCE client, the Gateway's client-less JWKS validation, each backend service's own scoped `client_credentials` client, and the Redis assignments cache. Supersedes nothing — it's a new layer on top of `microservices-architecture.mmd`, which still shows the business-to-business calls. |
| [`keycloak-02-login-flow.mmd`/`.png`](architecture/keycloak-02-login-flow.png) | A user logging in via Authorization Code + PKCE against Keycloak's own hosted page — supersedes `flow-04-login.mmd` once this plan is implemented. |
| [`keycloak-03-service-to-service.mmd`/`.png`](architecture/keycloak-03-service-to-service.png) | One concrete `client_credentials` call (Enrollment → Academic Structure), replacing today's self-minted blanket-admin service token. |
| [`keycloak-04-assignments-cache.mmd`/`.png`](architecture/keycloak-04-assignments-cache.png) | How the scope-based `assignments` claim survives without living in the Keycloak token — the Redis-cache mechanism from the decision below, end to end. |

## Context

UCMS currently hand-rolls its own auth: every one of the 8 services (gateway + 7
backend services) independently decodes a JWT signed with one shared HMAC secret
(`JWT_SECRET`). That shared secret means any service can forge a token for any
user or any other service, and every service's own inter-service calls self-mint
a "service token" carrying a blanket Global-Administrator assignment
(`mintServiceToken()`, repeated verbatim in each service's `JwtValidator`) — both
already-known weaknesses in this project. On top of that, identity-access-service
hand-rolls password hashing, account-status handling, and session bookkeeping that
a real identity provider gives for free.

The decision made: replace this with **Keycloak as the actual OIDC token issuer**
for the whole system (not just a password-check backend), with the Angular app
redirecting to Keycloak's own hosted login page (Authorization Code + PKCE, via
`keycloak-js`/`keycloak-angular`), rather than keeping the current custom branded
login form.

The guiding constraint: this is a **live, already-tested system** (216+ unit
tests, 55 system tests, 27 UI tests) with a non-standard, scope-based
authorization engine (`PermissionResolver`/`CurrentAssignments`/`AssignmentClaim`/
`RoleCatalog`, in academic-structure-service only) sitting on top of the JWT
today. This plan is built to touch that engine as little as possible and to roll
out in phases that keep the system working at every step — never a big-bang
cutover.

## Key facts this plan is built on (verified against the code)

- `PermissionResolver`/`CurrentAssignments`/`AssignmentClaim`/`RoleCatalog` — the
  scope-based (GLOBAL/DEPARTMENT, per-permission override) authorization engine —
  exist **only** in `academic-structure-service`. Every other service (`base`,
  `enrollment`, `assignment-grading`, `notification`, `analytics-reporting`,
  `gateway`) just turns the flat `role` claim into one `ROLE_*` authority.
- The principal every filter builds is a **bare `UUID`**:
  `new UsernamePasswordAuthenticationToken(userId, ..., authorities)`, consumed
  via `@AuthenticationPrincipal UUID currentUserId` (confirmed in
  `services/base-service/src/main/java/com/ucms/base/web/DepartmentController.java:33`
  and the `JwtAuthFilter` in base/enrollment). Spring's default
  `oauth2ResourceServer().jwt()` would set the principal to a `Jwt` object
  instead, breaking every `@AuthenticationPrincipal UUID` call site — so a
  **custom `JwtAuthenticationConverter`** that rebuilds this exact shape is
  required, not optional polish.
- The frontend's `currentUser`/`hasDepartmentScope` signals come from
  `GET /users/me` (`UserService.me()`), not from decoding the JWT client-side —
  so no frontend authorization-display logic needs to change, only token
  acquisition.
- `AssignmentService.create/setOverride/revoke` in identity-access-service are
  the only three mutation points for role-scope assignments — the natural
  cache-invalidation hook.
- The gateway's `pom.xml` has **no Spring Security dependency today** (just
  `jjwt-*`) — adding OIDC support there is a net-new dependency.
- identity-access-service's password hashing is plain
  `new BCryptPasswordEncoder()` (confirmed in `SecurityConfig.java`) — standard
  `$2a$`/`$2b$` hashes, directly importable into Keycloak via a BCrypt
  password-hash SPI with **no forced password reset**.
- `infra/init-schemas.sh` only runs against an *empty* data directory — adding a
  Keycloak schema/role there won't retroactively provision it on the live
  Contabo Postgres; that needs the same kind of one-time manual `psql` step the
  base-service rename already required.

## Decision: how the `assignments` claim survives (Redis-cache lookup, not a Keycloak SPI)

Keycloak issues a minimal standard token (`sub`, `email`, `realm_access.roles`)
only. **Only academic-structure-service** needs more than that, so only it gets a
new `AssignmentCache` (wraps `StringRedisTemplate`, new dependency for that
service only): key `ucms:assignments:<userId>` → JSON `AssignmentClaim[]`, TTL
matched to Keycloak's access-token lifespan (so it's never staler than today's
token-embedded claim was), invalidated by a `DEL` added to
identity-access-service's three `AssignmentService` mutation methods, with a
synchronous fallback to a new `GET /internal/assignments/{userId}` on
identity-access-service (same pattern as the existing `RoleCatalogClient` →
`/roles` sync) on a cache miss.

This is populated from the new `UcmsJwtAuthenticationConverter` at the exact
point `jwtValidator.assignmentsOf(token)` is called today
(`authToken.setDetails(...)`), so `PermissionResolver`, `CurrentAssignments`,
`AssignmentClaim`, `RoleCatalog`, and every controller that calls
`PermissionResolver` are **untouched**.

Rejected alternative: a custom Keycloak protocol-mapper SPI calling back into
identity-access-service at token-issuance time, preserving a fully
self-contained token. Rejected because it means writing, packaging, and
redeploying custom Java against Keycloak's SPI surface on every Keycloak
upgrade — reusing the Redis + `RestClient` pattern this codebase already has
(mirroring `RoleCatalogClient`) is lower-risk and lower-effort for the same
result.

## Realm/client topology

One realm `ucms`. One public PKCE client `ucms-web` (`standardFlowEnabled`,
`directAccessGrantsEnabled: false` — no resource-owner password grant, matching
the decision to drop the custom login form). One confidential,
`client_credentials` client per backend service, each granted only the realm
roles it actually needs, mapped from its real `client/*Client.java` calls:

| Client | Needs (realm roles) | Why |
|---|---|---|
| `ucms-web` | — (public, PKCE) | Browser login |
| `svc-identity-access` | none | Never calls another service |
| `svc-base` | `academic:read` | Department-deletion dependents check (G-05) |
| `svc-academic-structure` | `identity:read` | `RoleCatalogClient` role/permission sync |
| `svc-enrollment` | `academic:read`, `identity:read` | `AcademicClient`, `IdentityClient` |
| `svc-assignment-grading` | `academic:read`, `enrollment:read`, `base:read`, `identity:read` | `AcademicClient`, `EnrollmentClient`, `BaseClient`, `IdentityClient` |
| `svc-notification` | `identity:read`, `enrollment:read` | `IdentityClient`, `EnrollmentClient` |
| `svc-analytics-reporting` | `identity:read`, `academic:read`, `enrollment:read`, `assignment-grading:read` | `SourceClients` |

This is a real least-privilege improvement over today's blanket
Global-Administrator self-mint. The flat `role` (ADMIN/LECTURER/STUDENT) becomes
three realm roles assigned 1:1 per user. **The gateway gets no client of its
own** — it only ever validates against the realm's public JWKS, never requests a
token (confirmed: `JwtBackstopFilter` today only ever calls
`jwtValidator.parse(token)`, never mints anything).

## Password/user migration

Install a BCrypt password-hash-provider SPI jar into Keycloak
(`/opt/keycloak/providers/`) so existing `BCryptPasswordEncoder` hashes import
verbatim. A migration script (`infra/keycloak/migrate-users.py` or a
`kcadm.sh`-driven script, **not** committed with real data) reads
`identity_access.users` and creates each Keycloak user via the Admin REST API
with:

- **`id` explicitly pinned to the existing app UUID** — so Keycloak's `sub`
  claim becomes, by construction, the same UUID every service's FK columns
  already use, with zero translation layer needed at request time.
- `enabled` mirroring `UserStatus` (ACTIVE only — SUSPENDED/INACTIVE disabled in
  Keycloak too).
- One `credentials` entry: `type: "password"`, `algorithm: "bcrypt"`,
  `hashedSaltedValue: <existing hash>`, `temporary: false`.
- The realm role matching the user's current `role`.

A nullable `keycloak_id` column is added to `users` (new Flyway migration)
purely as an audit/reconciliation field, to catch the rare case the ID-pinning
didn't take.

## Service wiring changes (same pattern × gateway + 7 services)

| Today | → | Keycloak-era |
|---|---|---|
| `security/JwtProperties.java` (`JWT_SECRET`) | → | removed; `spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_ISSUER_URI}` |
| `security/JwtValidator.java` parse/subjectOf/roleOf/assignmentsOf | → | removed; Spring's `NimbusJwtDecoder` auto-configured from the issuer's JWKS |
| `security/JwtAuthFilter.java` | → | removed as a filter; replaced by a new `security/UcmsJwtAuthenticationConverter.java` wired via `.jwt(j -> j.jwtAuthenticationConverter(...))`, rebuilding the exact same `UsernamePasswordAuthenticationToken(UUID, ...)` shape (plus `.setDetails(assignmentCache.get(userId))` for academic-structure-service only) |
| `JwtValidator.mintServiceToken()` | → | same method name/call sites (zero churn in `client/*Client.java`), implementation swapped to a new `ClientCredentialsTokenProvider` that fetches+caches a token from Keycloak's token endpoint |
| gateway's `JwtBackstopFilter.java` (HMAC parse) | → | same class, decoder swapped to a reactive `ReactiveJwtDecoder` against Keycloak's JWKS (new `spring-boot-starter-oauth2-resource-server`/`-security` deps in `services/gateway/pom.xml`); public-path skip list, 401 shape, and Redis revocation check unchanged until the cleanup phase |

Academic-structure-service additionally gets `AssignmentCache.java` +
`spring-boot-starter-data-redis`. `JwtValidator.mintServiceToken()` stays as a
thin facade so no `client/*Client.java` call site needs to change.

## Frontend wiring

Add `keycloak-js` + `keycloak-angular` (Angular-19-compatible versions) —
purpose-built for the hosted-redirect PKCE flow, vs. a more generic OIDC
library. `app.config.ts`'s `provideAppInitializer(restoreSession)` becomes
`provideKeycloak({ url, realm: 'ucms', clientId: 'ucms-web', initOptions: {
onLoad: 'check-sso', pkceMethod: 'S256', ... } })`. `auth.service.ts` **keeps its
exact public surface** (`currentUser`, `isAuthenticated`, `hasDepartmentScope`,
`getToken()`, `logout()`) so `auth.guard.ts` and every component using it need
**zero edits** — only its internals swap to wrap keycloak-angular's `Keycloak`
instance; the manual `scheduleExpiry`/`tokenExpiry()` exp-timer and `TOKEN_KEY`
localStorage handling (added this session, before this plan was proposed) are
deleted since keycloak-js owns token storage/refresh itself.
`login.component.ts`/`.html` and its route are deleted; `guestGuard`'s redirect
becomes a call to `keycloak.login()`.

## Infra

Add a `ucms-keycloak` service to `docker-compose.infra.yml` (own `keycloak`
schema + role in the same shared Postgres via `init-schemas.sh`,
`KC_DB_SCHEMA=keycloak`, healthcheck against Keycloak's `/health/ready`,
127.0.0.1-only port, same secret convention via the untracked `.env`), with a
committed realm export at `infra/keycloak/ucms-realm.json` (structure only — no
credentials) imported via `--import-realm`. `docker-compose.deploy.yml` gets
`KEYCLOAK_ISSUER_URI` on every service (replacing `JWT_SECRET` once that service
has cut over) and `depends_on: ucms-keycloak: condition: service_healthy` added
everywhere. The one-time manual schema/role step needed against the
already-provisioned Contabo Postgres must be documented in
`docs/implementation-change-log.md` when this is implemented, the same way the
base-service rename was.

## Phased rollout (system stays live and testable at every step)

1. **Stand up Keycloak + realm + migrate users.** Fully additive — old auth
   untouched. Verify by logging in directly against Keycloak's token endpoint
   with a migrated user's real password (proves the BCrypt SPI works, no reset
   needed).
2. **Gateway dual-accepts both token shapes** (branch on JWT `alg`: HS256 →
   legacy path, RS256 → new Keycloak JWKS path). Verify with a new filter test
   feeding both token types through; existing gateway behavior for real traffic
   (still 100% legacy) is unaffected.
3. **Pilot one backend service: `base-service`** (simplest security package, no
   assignments/`PermissionResolver`) — prove the `UcmsJwtAuthenticationConverter`
   pattern end to end, dual-accepting both token types for now. Verify with its
   existing JUnit suite + a new converter test + a manual Keycloak-token request
   against a live instance.
4. **Roll the same swap to the other six services**, academic-structure-service
   last (it's the most complex — add `AssignmentCache` there once the pattern is
   proven elsewhere). Each verified the same way: existing per-service tests +
   a new converter test.
5. **Service-to-service calls move to client_credentials**, one service at a
   time, ordered so each swap happens after its downstream dependency already
   accepts Keycloak tokens (mirrors the existing `depends_on` chain). Verify via
   each service's existing integration tests that already exercise its
   `client/*` classes.
6. **Frontend cutover** to the `keycloak-js`/`keycloak-angular` redirect flow —
   safe as a direct cutover by this point since every backend already accepts
   Keycloak tokens. Delete the old login component/route. Verify by rewriting
   `docs/test-evidence/ui_test.js`/`expiry_test.js` for the new flow and
   re-running the full 27 UI + 55 system tests against the cut-over stack.
7. **Cleanup**: remove `JWT_SECRET` everywhere, delete
   `JwtProperties`/old `JwtAuthFilter`/`JwtService`/the `/auth/login`
   endpoint/the `alg`-dual-accept code, and retire the Redis revoked-token list
   (Keycloak's own logout invalidates the session; set a short Access Token
   Lifespan — 5–10 min with refresh rotation — in the realm config so the
   residual exposure window is smaller than what the current
   fail-open-on-Redis-outage behavior already tolerates). Verify with the full
   regression suite (216+ unit, 55 system, 27 UI) plus a manual
   logout-then-retry check against the new TTL.

Each phase is its own commit/PR-sized unit — nothing here gets committed or
pushed without confirmation, per the standing project rule.

## Open decisions before implementation starts

- Confirm Keycloak version to pin (affects the BCrypt SPI jar's packaging and
  the health-endpoint path).
- Confirm Access Token Lifespan / refresh-token rotation settings for the
  realm (affects the Redis revocation-list retirement rationale in Phase 7).
- Confirm whether `svc-identity-access` needs any realm role at all (currently
  assessed as none, since it has no outbound service-to-service calls).
