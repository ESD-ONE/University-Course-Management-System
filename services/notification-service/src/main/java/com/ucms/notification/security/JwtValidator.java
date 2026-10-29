package com.ucms.notification.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies Keycloak-issued tokens: RS256 signature against the realm's published keys,
 * expiry, issuer, and that the token was issued to one of UCMS's own clients. Then
 * reduces it to the caller this service authorizes on (see {@link VerifiedToken}).
 *
 * A person's token must carry the "uid" claim (their UCMS user id) and one account role.
 * Another service's token is accepted only if that service holds this service's
 * "svc:..." realm role — so each service can call only the services it's been granted.
 */
@Service
@EnableConfigurationProperties(KeycloakProperties.class)
public class JwtValidator {

    static final String SERVICE_ROLE = "service";
    private static final List<String> ACCOUNT_ROLES = List.of("ADMIN", "LECTURER", "STUDENT");
    private static final String WEB_CLIENT = "ucms-web";

    private final JwtDecoder decoder;
    private final KeycloakProperties properties;
    private final ClientCredentialsTokenProvider serviceTokens;

    public JwtValidator(KeycloakProperties properties, ClientCredentialsTokenProvider serviceTokens) {
        this.properties = properties;
        this.serviceTokens = serviceTokens;
        NimbusJwtDecoder nimbus = properties.getPublicKey() != null && !properties.getPublicKey().isBlank()
                ? NimbusJwtDecoder.withPublicKey(parsePublicKey(properties.getPublicKey())).build()
                : NimbusJwtDecoder.withJwkSetUri(properties.jwksUri()).build();
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.expectedIssuer()),
                JwtValidator::issuedToUcmsClient));
        this.decoder = nimbus;
    }

    /** Throws if the token is invalid, expired, or from a caller not allowed here. */
    public VerifiedToken verify(String token) {
        Jwt jwt = decoder.decode(token);
        List<String> roles = realmRoles(jwt);
        if (roles.contains(SERVICE_ROLE)) {
            if (properties.getServiceRole() == null || !roles.contains(properties.getServiceRole())) {
                throw new BadJwtException("Service " + jwt.getClaimAsString("azp") + " may not call this service");
            }
            return new VerifiedToken(UUID.fromString(jwt.getSubject()), "ADMIN", true, jwt);
        }
        String uid = jwt.getClaimAsString("uid");
        if (uid == null) {
            throw new BadJwtException("Token has no uid claim — the account is not linked to a UCMS user");
        }
        String role = ACCOUNT_ROLES.stream().filter(roles::contains).findFirst()
                .orElseThrow(() -> new BadJwtException("Token carries no UCMS account role"));
        return new VerifiedToken(UUID.fromString(uid), role, false, jwt);
    }

    public UUID subjectOf(String token) {
        return verify(token).subject();
    }

    public String roleOf(String token) {
        return verify(token).role();
    }

    /** A short-lived token, from Keycloak, for calling another service as this service. */
    public String mintServiceToken() {
        return serviceTokens.token();
    }

    @SuppressWarnings("unchecked")
    private static List<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null || !(realmAccess.get("roles") instanceof List<?> roles)) return List.of();
        return (List<String>) roles;
    }

    private static OAuth2TokenValidatorResult issuedToUcmsClient(Jwt jwt) {
        String azp = jwt.getClaimAsString("azp");
        if (WEB_CLIENT.equals(azp) || (azp != null && azp.startsWith("svc-"))) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Token was not issued to a UCMS client", null));
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            String base64 = pem.replaceAll("-----(BEGIN|END) PUBLIC KEY-----", "").replaceAll("\\s", "");
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (Exception e) {
            throw new IllegalArgumentException("ucms.keycloak.public-key is not a valid RSA public key", e);
        }
    }
}
