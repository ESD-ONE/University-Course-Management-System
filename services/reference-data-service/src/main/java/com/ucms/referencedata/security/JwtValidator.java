package com.ucms.referencedata.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * This service only validates tokens, never issues them — Identity & Access is the
 * source of truth for login. Shares the same JWT_SECRET so a token it issued verifies
 * here too. A central Gateway-level validation/cache (see flow-04-login.mmd) is still
 * deferred, same as identity-access-service's own JwtAuthFilter notes.
 */
@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtValidator {

    private final SecretKey key;

    @Autowired
    public JwtValidator(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public UUID subjectOf(String token) {
        return UUID.fromString(parse(token).getSubject());
    }

    public String roleOf(String token) {
        return parse(token).get("role", String.class);
    }
}
