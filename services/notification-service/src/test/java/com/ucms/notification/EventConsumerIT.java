package com.ucms.notification;

import com.ucms.notification.client.EnrollmentClient;
import com.ucms.notification.client.IdentityClient;
import com.ucms.notification.config.RabbitConfig;
import com.ucms.notification.repository.NotificationRepository;
import com.ucms.notification.service.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Events arriving from a real broker: the queue bindings, the listener, duplicate
 * delivery and the dead-letter queue. The unit tests call NotificationService directly,
 * so nothing else exercises the path from the exchange to a stored notification.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class EventConsumerIT {

    private static final String DEAD_LETTER_QUEUE = "notification.events.dlq";

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
    @Autowired NotificationRepository notifications;
    @MockBean IdentityClient identity;
    @MockBean EnrollmentClient enrollment;
    @MockBean EmailSender email;

    @BeforeEach
    void anyIdIsAnActiveStudent() {
        when(identity.getUser(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new IdentityClient.User(id, "Student " + id, id + "@uni.edu", "STUDENT", "ACTIVE");
        });
        when(identity.profile(anyString())).thenReturn(new IdentityClient.Profile(false, true, false, true));
    }

    @Test
    void anEnrollmentEventFromTheBroker_createsANotification() {
        UUID student = UUID.randomUUID();

        publish("enrollment.student-enrolled", studentEnrolled(UUID.randomUUID(), student));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notifications.countByUserIdAndReadAtIsNull(student)).isEqualTo(1));
    }

    @Test
    void theSameEventDeliveredTwice_createsOneNotification() {
        UUID student = UUID.randomUUID();
        UUID marker = UUID.randomUUID();
        String event = studentEnrolled(UUID.randomUUID(), student);

        publish("enrollment.student-enrolled", event);
        publish("enrollment.student-enrolled", event);
        // Messages are handled in order, so once this later one is in, both copies were.
        publish("enrollment.student-enrolled", studentEnrolled(UUID.randomUUID(), marker));

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(notifications.countByUserIdAndReadAtIsNull(marker)).isEqualTo(1));
        assertThat(notifications.countByUserIdAndReadAtIsNull(student)).isEqualTo(1);
    }

    @Test
    void aMalformedMessage_goesToTheDeadLetterQueue_andLaterMessagesStillArrive() {
        UUID student = UUID.randomUUID();

        publish("enrollment.student-enrolled", "not json");
        publish("enrollment.student-enrolled", studentEnrolled(UUID.randomUUID(), student));

        await().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(notifications.countByUserIdAndReadAtIsNull(student)).isEqualTo(1));
        Message dead = rabbitTemplate.receive(DEAD_LETTER_QUEUE, 20_000);
        assertThat(dead).as("the malformed message on " + DEAD_LETTER_QUEUE).isNotNull();
        assertThat(new String(dead.getBody(), StandardCharsets.UTF_8)).isEqualTo("not json");
    }

    private void publish(String routingKey, String body) {
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, body);
    }

    /** The shared envelope, as enrollment-service's EventPublisher writes it. */
    private static String studentEnrolled(UUID eventId, UUID student) {
        return "{\"eventId\":\"" + eventId + "\",\"type\":\"enrollment.student-enrolled\","
                + "\"occurredAt\":\"" + Instant.now() + "\","
                + "\"data\":{\"studentId\":\"" + student + "\",\"offeringId\":\"" + UUID.randomUUID() + "\","
                + "\"courseCode\":\"COSC5105\",\"courseName\":\"Intro to Programming\",\"onBehalf\":false}}";
    }
}
