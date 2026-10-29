package com.ucms.identity.service;

import com.ucms.identity.security.JwtValidator;
import com.ucms.identity.security.TokenRevocationStore;
import org.springframework.stereotype.Service;

/**
 * Sign-in now happens at Keycloak (docs/keycloak-integration.md); what remains here is
 * the logout half of G-11. The browser ends its Keycloak session itself, which stops any
 * further token refresh; this puts the access token it was holding on the Redis
 * revocation list the Gateway checks, so that token stops working immediately instead
 * of at its (short) natural expiry.
 */
@Service
public class AuthService {

    private final JwtValidator jwtValidator;
    private final TokenRevocationStore revocationStore;

    public AuthService(JwtValidator jwtValidator, TokenRevocationStore revocationStore) {
        this.jwtValidator = jwtValidator;
        this.revocationStore = revocationStore;
    }

    public void logout(String token) {
        if (token == null) return;
        var expiresAt = jwtValidator.verify(token).jwt().getExpiresAt();
        if (expiresAt != null) {
            revocationStore.revoke(token, expiresAt);
        }
    }
}
