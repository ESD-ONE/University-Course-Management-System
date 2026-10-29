package com.ucms.keycloak;

import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Lets Keycloak verify the BCrypt hashes identity-access-service's BCryptPasswordEncoder
 * produced, so existing accounts move to Keycloak with their passwords unchanged.
 *
 * policyCheck() returns false unless the realm's hash algorithm is itself "bcrypt", so on
 * a user's first successful login Keycloak re-hashes the password with the realm's default
 * algorithm — the BCrypt credentials phase themselves out as people sign in.
 */
public class BCryptPasswordHashProvider implements PasswordHashProvider {

    public static final String ID = "bcrypt";
    private static final int DEFAULT_COST = 10;

    @Override
    public boolean policyCheck(PasswordPolicy policy, PasswordCredentialModel credential) {
        return ID.equals(policy.getHashAlgorithm());
    }

    @Override
    public PasswordCredentialModel encodedCredential(String rawPassword, int iterations) {
        int cost = iterations > 0 ? iterations : DEFAULT_COST;
        String hash = BCrypt.hashpw(rawPassword, BCrypt.gensalt(cost));
        return PasswordCredentialModel.createFromValues(ID, new byte[0], cost, hash);
    }

    @Override
    public String encode(String rawPassword, int iterations) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(iterations > 0 ? iterations : DEFAULT_COST));
    }

    @Override
    public boolean verify(String rawPassword, PasswordCredentialModel credential) {
        String hash = credential.getPasswordSecretData().getValue();
        if (hash == null || !hash.startsWith("$2")) return false;
        try {
            // Spring's BCryptPasswordEncoder writes $2a$; jBCrypt reads $2a$ and $2b$ the same way.
            return BCrypt.checkpw(rawPassword, hash.replaceFirst("^\\$2b\\$", "\\$2a\\$"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    @Override
    public void close() {
    }
}
