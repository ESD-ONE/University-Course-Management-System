package com.ucms.enrollment.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * A token Keycloak issued, checked and reduced to what this service authorizes on.
 *
 * @param subject UCMS user id (the "uid" claim) for a person, or the service account's
 *                id for another service
 * @param role    ADMIN / LECTURER / STUDENT for a person; ADMIN for a trusted service,
 *                which is what the services' own HS256 tokens carried before Keycloak
 * @param service true if the caller is another backend service, not a person
 */
public record VerifiedToken(UUID subject, String role, boolean service, Jwt jwt) {
}
