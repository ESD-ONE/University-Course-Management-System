/**
 * Base URL for the Gateway's Identity & Access routes. The Gateway strips the first
 * two path segments (StripPrefix=2 on /api/identity/**), so everything after this
 * base maps 1:1 to identity-access-service's own controller paths (/auth/login, /users, ...).
 *
 * Hardcoded to the local dev Gateway for now — this app has no build-time environment
 * swap configured yet (Angular 19 dropped the environments schematic by default).
 * Revisit when there's a real deployed Gateway URL to point at.
 */
export const API_BASE_URL = 'http://localhost:8082/api/identity';

/** Same Gateway, routed to reference-data-service instead (StripPrefix=2 on /api/reference/**). */
export const REFERENCE_API_BASE_URL = 'http://localhost:8082/api/reference';

/** Same Gateway, routed to academic-structure-service instead (StripPrefix=2 on /api/academic/**). */
export const ACADEMIC_API_BASE_URL = 'http://localhost:8082/api/academic';
