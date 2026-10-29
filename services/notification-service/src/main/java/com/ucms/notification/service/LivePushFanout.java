package com.ucms.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Broadcasts live pushes to every notification-service replica over a Redis channel. A
 * message is "userId\njson"; each replica's listener ({@link #onMessage}) hands it to
 * its own {@link LivePushHub}. Wired up in config/LivePushFanoutConfig.
 */
public class LivePushFanout {

    public static final String CHANNEL = "ucms:live-push";
    private static final Logger log = LoggerFactory.getLogger(LivePushFanout.class);

    private final StringRedisTemplate redis;

    public LivePushFanout(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** False if Redis couldn't take it — the caller then delivers locally instead. */
    public boolean publish(UUID userId, String json) {
        try {
            redis.convertAndSend(CHANNEL, userId + "\n" + json);
            return true;
        } catch (RuntimeException ex) {
            log.warn("Live push fan-out unavailable, delivering on this replica only: {}", ex.getMessage());
            return false;
        }
    }

    public static void onMessage(byte[] body, BiConsumer<UUID, String> deliver) {
        String message = new String(body, StandardCharsets.UTF_8);
        int split = message.indexOf('\n');
        if (split <= 0) return;
        try {
            deliver.accept(UUID.fromString(message.substring(0, split)), message.substring(split + 1));
        } catch (IllegalArgumentException ex) {
            log.warn("Ignored malformed live-push message");
        }
    }
}
