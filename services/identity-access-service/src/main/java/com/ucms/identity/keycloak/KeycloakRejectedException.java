package com.ucms.identity.keycloak;

/** Keycloak refused the change on its merits — almost always the realm's password policy
 *  (too short, same as a recent password, contains the email). Surfaced as 400 with
 *  Keycloak's own explanation; the account change is rolled back. */
public class KeycloakRejectedException extends RuntimeException {
    public KeycloakRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
