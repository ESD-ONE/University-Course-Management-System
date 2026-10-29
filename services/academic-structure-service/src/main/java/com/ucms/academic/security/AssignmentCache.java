package com.ucms.academic.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * A user's scope assignments, which PermissionResolver needs and Keycloak's tokens don't
 * carry. Read from Redis; on a miss, fetched from identity-access-service's
 * GET /internal/assignments/{userId} (as this service's own Keycloak service account) and
 * cached for the access-token lifetime. identity-access-service drops the entry whenever
 * that user's assignments change, so an admin's change applies from the next request.
 *
 * The key format ("ucms:assignments:" + userId) is a contract shared with
 * identity-access-service's AssignmentCacheInvalidator — change both together.
 */
@Component
public class AssignmentCache {

    static final String KEY_PREFIX = "ucms:assignments:";
    private static final Logger log = LoggerFactory.getLogger(AssignmentCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RestClient identity;
    private final JwtValidator jwt;
    private final Duration ttl;

    public AssignmentCache(StringRedisTemplate redis, ObjectMapper objectMapper, JwtValidator jwt,
                           RestClient.Builder builder,
                           @Value("${ucms.identity-service-uri}") String identityServiceUri,
                           @Value("${ucms.assignment-cache.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.jwt = jwt;
        this.identity = builder.baseUrl(identityServiceUri).build();
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public List<AssignmentClaim> forUser(UUID userId) {
        String key = KEY_PREFIX + userId;
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return objectMapper.readValue(cached, new TypeReference<List<AssignmentClaim>>() {});
            }
        } catch (Exception ex) {
            // Redis is an optimization here, not a dependency: fall through to the source.
            log.warn("Assignment cache read failed, asking Identity & Access directly: {}", ex.getMessage());
        }

        List<AssignmentClaim> fresh;
        try {
            fresh = identity.get().uri("/internal/assignments/{userId}", userId)
                    .header("Authorization", "Bearer " + jwt.mintServiceToken())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AssignmentClaim>>() {});
        } catch (Exception ex) {
            throw new AssignmentsUnavailableException(ex);
        }
        if (fresh == null) fresh = List.of();

        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(fresh), ttl);
        } catch (Exception ex) {
            log.warn("Assignment cache write failed: {}", ex.getMessage());
        }
        return fresh;
    }
}
