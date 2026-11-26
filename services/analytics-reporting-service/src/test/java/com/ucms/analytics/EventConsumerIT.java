package com.ucms.analytics;

import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.service.SnapshotService;
import com.ucms.analytics.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * Events arriving from a real broker: the queue bindings and the listener that marks
 * the dashboard snapshot stale. Nothing else exercises the path from the exchange to
 * SnapshotService.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EventConsumerIT {

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3.13.7-management-alpine");

    @DynamicPropertySource
    static void useTheBroker(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("ucms.events.enabled", () -> "true");
    }

    @Autowired RabbitTemplate rabbitTemplate;
    @MockBean SnapshotService snapshots;
    @MockBean SourceClients sources;
    @MockBean FileStorage storage;

    @Test
    void enrollmentGradingAndIdentityEvents_markTheDashboardStale() {
        publish("enrollment.student-enrolled");
        publish("grading.grade-posted");
        publish("identity.user-created");

        verify(snapshots, timeout(10_000).atLeast(3)).markDirty();
    }

    private void publish(String type) {
        String envelope = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"type\":\"" + type + "\","
                + "\"occurredAt\":\"" + Instant.now() + "\",\"data\":{}}";
        rabbitTemplate.convertAndSend("ucms.events", type, envelope);
    }
}
