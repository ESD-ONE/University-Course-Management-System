package com.ucms.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Scope assignments no longer travel inside the token (Keycloak issues it, and doesn't
 * know about them). academic-structure-service — their only consumer — caches each user's
 * assignments in Redis and fetches them from GET /internal/assignments/{userId} on a miss.
 * Whenever a user's assignments change here, their cache entry is dropped after the
 * change commits, so the next request sees it.
 *
 * The key format ("ucms:assignments:" + userId) is a contract shared with
 * academic-structure-service's AssignmentCache — change both together.
 */
@Component
public class AssignmentCacheInvalidator {

    public static final String KEY_PREFIX = "ucms:assignments:";
    private static final Logger log = LoggerFactory.getLogger(AssignmentCacheInvalidator.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public AssignmentCacheInvalidator(StringRedisTemplate redis,
                                      @Value("${ucms.assignment-cache.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    public void evict(UUID userId) {
        if (!enabled || userId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(userId);
                }
            });
        } else {
            delete(userId);
        }
    }

    private void delete(UUID userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (Exception ex) {
            // The entry still expires on its own TTL (the access-token lifetime).
            log.warn("Could not drop cached assignments for {}: {}", userId, ex.getMessage());
        }
    }
}
