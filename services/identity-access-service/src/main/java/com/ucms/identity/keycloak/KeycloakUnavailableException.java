package com.ucms.identity.keycloak;

/** Keycloak couldn't be reached or rejected an admin call; surfaced as 503 so the account
 *  change is rolled back rather than left half-applied. */
public class KeycloakUnavailableException extends RuntimeException {
    public KeycloakUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
