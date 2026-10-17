package com.ucms.academic.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Parses the JWT's "assignments" claim into AssignmentClaim objects. Tolerates a
     * missing/malformed claim (returns empty list) so older tokens issued before this
     * claim existed don't break authentication — see docs/SYSTEM_ARCHITECTURE_REPORT.md
     * section 12.3. */
    @SuppressWarnings("unchecked")
    public List<AssignmentClaim> assignmentsOf(String token) {
        List<AssignmentClaim> result = new ArrayList<>();
        Object raw = parse(token).get("assignments");
        if (!(raw instanceof List<?> list)) return result;
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) continue;
            try {
                UUID id = UUID.fromString((String) map.get("id"));
                UUID roleId = UUID.fromString((String) map.get("roleId"));
                String scopeType = (String) map.get("scopeType");
                String scopeIdStr = (String) map.get("scopeId");
                UUID scopeId = (scopeIdStr == null || scopeIdStr.isEmpty()) ? null : UUID.fromString(scopeIdStr);
                Map<UUID, Boolean> overrides = new HashMap<>();
                Object rawOverrides = map.get("overrides");
                if (rawOverrides instanceof Map<?, ?> overrideMap) {
                    overrideMap.forEach((k, v) -> overrides.put(UUID.fromString((String) k), (Boolean) v));
                }
                result.add(new AssignmentClaim(id, roleId, scopeType, scopeId, overrides));
            } catch (Exception ignored) {
                // skip malformed entries rather than failing authentication entirely
            }
        }
        return result;
    }
}
