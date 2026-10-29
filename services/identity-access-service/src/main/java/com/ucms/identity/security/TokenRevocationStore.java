package com.ucms.identity.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * Closes G-11's remaining limitation: logout now actually stops a token working. The
 * revoked token's SHA-256 is written to Redis with a TTL equal to its remaining
 * lifetime; the Gateway's JwtBackstopFilter rejects any token whose hash is present.
 * Since every browser request passes through the Gateway, this is enforced in one place
 * without making each backend service's JWT validation stateful.
 *
 * The key format ("ucms:revoked:" + Base64(SHA-256(token))) is a contract shared with
 * the Gateway — change both together.
 */
@Component
public class TokenRevocationStore {

    public static final String KEY_PREFIX = "ucms:revoked:";
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationStore.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;

    public TokenRevocationStore(StringRedisTemplate redis,
                                 @Value("${ucms.revocation.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.enabled = enabled;
    }

    /** Returns false if the revocation couldn't be recorded (Redis down) — the session
     * row is still revoked either way; the caller decides how to surface it. */
    public boolean revoke(String token, Instant expiresAt) {
        if (!enabled) return false;
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) return true;
        try {
            redis.opsForValue().set(KEY_PREFIX + hash(token), "1", ttl);
            return true;
        } catch (Exception ex) {
            log.warn("Could not record token revocation in Redis: {}", ex.getMessage());
            return false;
        }
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
