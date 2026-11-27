package com.ucms.enrollment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import com.ucms.enrollment.repository.RegistrationLogRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What this service really puts on the broker: the routing key, the shared event
 * envelope, and that nothing is published for an enrollment that did not commit. The
 * unit tests mock the publisher, so this is the only place a real message is sent.
 * Not @Transactional: events go out after commit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class EventPublisherIT {

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

    @Autowired MockMvc mockMvc;
    @Autowired AmqpAdmin amqpAdmin;
    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired ObjectMapper objectMapper;
    @Autowired CourseEnrollmentRepository enrollments;
    @Autowired RegistrationLogRepository registrationLog;
    @MockBean AcademicClient academic;
    @MockBean IdentityClient identity;

    private UUID offeringId;
    private String queue;

    @BeforeEach
    void listenForEnrollmentEvents() {
        when(identity.getUser(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            return new IdentityClient.User(id, "Student " + id, id + "@uni.edu", "STUDENT", "ACTIVE", "STU-" + id);
        });
        offeringId = UUID.randomUUID();
        when(academic.getOffering(offeringId)).thenReturn(new AcademicClient.Offering(offeringId, UUID.randomUUID(),
                UUID.randomUUID(), "COSC5105", "Intro to Programming", 3, "CORE", UUID.randomUUID(), "BSC-CS",
                "BSc Computer Science", UUID.randomUUID(), 1, UUID.randomUUID(), "2025/2026", "ACTIVE",
                UUID.randomUUID(), "Semester 1", null, null, 1, UUID.randomUUID(), "PUBLISHED", true));

        TopicExchange exchange = new TopicExchange(EventPublisher.EXCHANGE, true, false);
        amqpAdmin.declareExchange(exchange);
        // A plain named queue: an anonymous one is exclusive to the connection that made it.
        Queue q = new Queue("it.enrollment-events." + UUID.randomUUID(), false, false, false);
        amqpAdmin.declareQueue(q);
        Binding binding = BindingBuilder.bind(q).to(exchange).with("enrollment.#");
        amqpAdmin.declareBinding(binding);
        queue = q.getName();
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.deleteQueue(queue);
        enrollments.deleteAll();
        registrationLog.deleteAll();
    }

    @Test
    void enrolling_publishesStudentEnrolledWithTheSharedEnvelope() throws Exception {
        UUID student = UUID.randomUUID();
        Instant before = Instant.now().minusSeconds(1);

        enroll(student).andExpect(status().isCreated());

        Message message = rabbitTemplate.receive(queue, 10_000);
        assertThat(message).as("an event on ucms.events").isNotNull();
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("enrollment.student-enrolled");

        JsonNode envelope = objectMapper.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
        assertThat(UUID.fromString(envelope.path("eventId").asText())).isNotNull();
        assertThat(envelope.path("type").asText()).isEqualTo("enrollment.student-enrolled");
        assertThat(Instant.parse(envelope.path("occurredAt").asText())).isAfter(before);
        assertThat(envelope.path("data").path("studentId").asText()).isEqualTo(student.toString());
        assertThat(envelope.path("data").path("offeringId").asText()).isEqualTo(offeringId.toString());
    }

    @Test
    void aRejectedEnrollment_publishesNothing() throws Exception {
        enroll(UUID.randomUUID()).andExpect(status().isCreated());
        assertThat(rabbitTemplate.receive(queue, 10_000)).isNotNull();

        // The offering has one seat, so this one is refused and its transaction rolls back.
        enroll(UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_FULL"));

        assertThat(rabbitTemplate.receive(queue, 2_000)).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions enroll(UUID student) throws Exception {
        return mockMvc.perform(post("/course-enrollments")
                .header("Authorization", "Bearer " + KeycloakTestTokens.user(student, "STUDENT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"offeringId\":\"" + offeringId + "\",\"joinWaitlistIfFull\":false}"));
    }
}
