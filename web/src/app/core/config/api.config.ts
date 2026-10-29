/**
 * Keycloak, which runs the login page and issues every token. In production the web
 * container proxies /auth to it on the app's own origin (web/nginx.conf). Under
 * `ng serve` (port 4200) the browser talks to the local Keycloak container directly.
 */
export const KEYCLOAK_URL =
  location.port === '4200' ? 'http://localhost:8180/auth' : `${location.origin}/auth`;

/**
 * Base URL for the Gateway's Identity & Access routes. The Gateway strips the first
 * two path segments (StripPrefix=2 on /api/identity/**), so everything after this
 * base maps 1:1 to identity-access-service's own controller paths (/auth/logout, /users, ...).
 *
 * Relative, not hardcoded to a host: in production this app is served by the same
 * nginx container that reverse-proxies /api/** straight through to the Gateway (see
 * web/nginx.conf), so the browser never needs to know the Gateway's host/port — no
 * CORS, works the same whether reached by IP, a future domain, or an SSH tunnel. In
 * local dev, ng serve's proxy.conf.json forwards /api the same way, to the local
 * Gateway on :8082.
 */
export const API_BASE_URL = '/api/identity';

/**
 * Same Gateway, routed to base-service instead (StripPrefix=2 on /api/base/**).
 * base-service is the renamed reference-data-service (infrastructure rename only —
 * see docs/reference-data-vs-base-analysis.md); it still only serves Faculty/Department/
 * GradingScale/CourseCategory data, which is why the Angular feature/service/model files
 * that consume this constant keep their "Reference Data" naming — that's the business
 * feature name, independent of which microservice happens to serve it.
 */
export const BASE_API_BASE_URL = '/api/base';

/** Same Gateway, routed to academic-structure-service instead (StripPrefix=2 on /api/academic/**). */
export const ACADEMIC_API_BASE_URL = '/api/academic';

export const ENROLLMENT_API_BASE_URL = '/api/enrollment';

export const GRADING_API_BASE_URL = '/api/grading';

export const NOTIFICATION_API_BASE_URL = '/api/notification';

export const ANALYTICS_API_BASE_URL = '/api/analytics';
