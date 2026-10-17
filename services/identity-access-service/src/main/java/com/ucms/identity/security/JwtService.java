package com.ucms.identity.security;

import com.ucms.identity.web.dto.AssignmentClaim;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@EnableConfigurationProperties(JwtProperties.class)
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;

    @Autowired
    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = properties.getExpirationMinutes();
    }

    public String issueToken(UUID userId, String email, String role) {
        return issueToken(userId, email, role, List.of());
    }

    /** assignments is embedded as a claim so every downstream service can resolve
     * scope-based permissions locally — see docs/SYSTEM_ARCHITECTURE_REPORT.md
     * section 12.3. Each entry is serialized as a plain map (jjwt-jackson handles
     * record serialization transparently). */
    public String issueToken(UUID userId, String email, String role, List<AssignmentClaim> assignments) {
        Instant now = Instant.now();
        List<Map<String, Object>> assignmentClaims = assignments.stream()
                .map(a -> Map.<String, Object>of(
                        "id", a.id().toString(),
                        "roleId", a.roleId().toString(),
                        "scopeType", a.scopeType(),
                        "scopeId", a.scopeId() == null ? "" : a.scopeId().toString(),
                        "overrides", a.overrides().entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue))))
                .toList();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role)
                .claim("assignments", assignmentClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public Instant expiryOf(String token) {
        return parse(token).getExpiration().toInstant();
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
