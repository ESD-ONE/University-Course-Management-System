/**
 * Base URL for the Gateway's Identity & Access routes. The Gateway strips the first
 * two path segments (StripPrefix=2 on /api/identity/**), so everything after this
 * base maps 1:1 to identity-access-service's own controller paths (/auth/login, /users, ...).
 *
 * Relative, not hardcoded to a host: in production this app is served by the same
 * nginx container that reverse-proxies /api/** straight through to the Gateway (see
 * web/nginx.conf), so the browser never needs to know the Gateway's host/port — no
 * CORS, works the same whether reached by IP, a future domain, or an SSH tunnel. In
 * local dev, ng serve's proxy.conf.json forwards /api the same way, to the local
 * Gateway on :8082.
 */
export const API_BASE_URL = '/api/identity';

/** Same Gateway, routed to reference-data-service instead (StripPrefix=2 on /api/reference/**). */
export const REFERENCE_API_BASE_URL = '/api/reference';

/** Same Gateway, routed to academic-structure-service instead (StripPrefix=2 on /api/academic/**). */
export const ACADEMIC_API_BASE_URL = '/api/academic';
