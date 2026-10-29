package com.ucms.grading;

import io.jsonwebtoken.Jwts;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens shaped like the ones Keycloak issues (RS256, iss, azp, realm_access.roles, uid),
 * signed with a test-only key whose public half is ucms.keycloak.public-key in
 * application-test.yml.
 */
public final class KeycloakTestTokens {

    public static final String ISSUER = "http://keycloak.test/auth/realms/ucms";
    private static final PrivateKey KEY = loadKey();

    private KeycloakTestTokens() {}

    /** A person signed in through the web app. */
    public static String user(UUID uid, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("azp", "ucms-web");
        claims.put("uid", uid.toString());
        claims.put("realm_access", Map.of("roles", List.of(role, "default-roles-ucms")));
        return sign(claims, Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    /** Another backend service, holding the given "svc:..." roles. */
    public static String service(String... svcRoles) {
        List<String> roles = new ArrayList<>(List.of("service"));
        roles.addAll(List.of(svcRoles));
        return sign(Map.of("azp", "svc-test", "realm_access", Map.of("roles", roles)),
                Instant.now().plus(5, ChronoUnit.MINUTES));
    }

    public static String sign(Map<String, Object> claims, Instant expiresAt) {
        return sign(claims, expiresAt, ISSUER);
    }

    public static String sign(Map<String, Object> claims, Instant expiresAt, String issuer) {
        return Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(issuer)
                .claims(claims)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiresAt))
                .signWith(KEY, Jwts.SIG.RS256)
                .compact();
    }

    private static PrivateKey loadKey() {
        try (InputStream in = KeycloakTestTokens.class.getResourceAsStream("/keycloak-test-private.pem")) {
            String pem = new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("Could not load the test signing key", e);
        }
    }
}
