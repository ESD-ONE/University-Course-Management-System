package com.ucms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.notification.config.LivePushFanoutConfig;
import com.ucms.notification.service.LivePushFanout;
import com.ucms.notification.service.LivePushHub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Two notification-service replicas sharing one Redis: a push made on replica A must be
 * delivered by replica B too, because the user's live stream may be held by B.
 */
@Testcontainers(disabledWithoutDocker = true)
class LivePushFanoutTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** A replica: its hub records what it would send to its browsers. */
    static final class Replica {
        final List<String> delivered = new CopyOnWriteArrayList<>();
        final LettuceConnectionFactory factory;
        final RedisMessageListenerContainer listener;
        final LivePushHub hub;

        Replica() throws Exception {
            factory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
            factory.afterPropertiesSet();
            factory.start();
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            LivePushFanoutConfig config = new LivePushFanoutConfig();
            hub = new LivePushHub(new ObjectMapper(), Optional.of(config.livePushFanout(redis))) {
                @Override
                public void deliver(UUID userId, String json) {
                    delivered.add(userId + " " + json);
                }
            };
            listener = config.livePushListener(factory, hub);
            listener.afterPropertiesSet();
            listener.start();
        }

        void stop() throws Exception {
            listener.stop();
            listener.destroy();
            factory.destroy();
        }
    }

    Replica a;
    Replica b;

    @BeforeEach
    void start() throws Exception {
        a = new Replica();
        b = new Replica();
        // Subscriptions are set up asynchronously; wait until both are listening.
        StringRedisTemplate probe = new StringRedisTemplate(a.factory);
        await().until(() -> {
            Long receivers = probe.execute(c -> c.publish(LivePushFanout.CHANNEL.getBytes(), "probe".getBytes()), true);
            return receivers != null && receivers >= 2;
        });
    }

    @AfterEach
    void stop() throws Exception {
        a.stop();
        b.stop();
    }

    @Test
    void aPushOnOneReplicaIsDeliveredByEveryReplica() {
        UUID student = UUID.randomUUID();
        a.hub.push(student, Map.of("title", "Enrolled in CS101"));

        String expected = student + " {\"title\":\"Enrolled in CS101\"}";
        await().untilAsserted(() -> {
            assertThat(a.delivered).contains(expected);
            assertThat(b.delivered).contains(expected);
        });
    }

    @Test
    void malformedMessagesAreIgnored() {
        List<String> got = new CopyOnWriteArrayList<>();
        LivePushFanout.onMessage("not-a-uuid\n{}".getBytes(), (id, json) -> got.add(json));
        LivePushFanout.onMessage("no-separator".getBytes(), (id, json) -> got.add(json));
        assertThat(got).isEmpty();
    }

    @Test
    void withoutFanoutAPushIsDeliveredLocally() {
        List<String> delivered = new CopyOnWriteArrayList<>();
        LivePushHub alone = new LivePushHub(new ObjectMapper(), Optional.empty()) {
            @Override
            public void deliver(UUID userId, String json) {
                delivered.add(json);
            }
        };
        alone.push(UUID.randomUUID(), Map.of("x", 1));
        assertThat(delivered).containsExactly("{\"x\":1}");
    }
}
