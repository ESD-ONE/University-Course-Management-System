package com.ucms.enrollment.contract;

import com.ucms.enrollment.KeycloakTestTokens;
import com.ucms.enrollment.client.AcademicClient;
import com.ucms.enrollment.client.IdentityClient;
import com.ucms.enrollment.messaging.EventPublisher;
import com.ucms.enrollment.repository.CourseEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.contract.verifier.converter.YamlContract;
import org.springframework.cloud.contract.verifier.messaging.MessageVerifierReceiver;
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class of the tests generated from src/test/resources/contracts/events. Each
 * contract there is an event as notification-service reads it. A generated test calls one
 * method below, which makes this service publish that event the way it really does, and
 * then checks the published JSON against the contract.
 *
 * The real services and the real EventPublisher run. Two things differ from production.
 * RabbitTemplate is a mock, so no broker is needed and the test can read what was sent.
 * And the services publish only after their transaction commits, while these tests roll
 * back to leave no data behind, so the test runs the pending "after commit" step itself.
 */
@SpringBootTest(properties = "ucms.events.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@AutoConfigureMessageVerifier
@Import(EventContractBase.PublishedEvents.class)
public abstract class EventContractBase {

    static final UUID STUDENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    static final UUID OTHER_STUDENT_ID = UUID.fromString("a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1");
    static final UUID ADMIN_ID = UUID.fromString("abababab-abab-abab-abab-abababababab");
    static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID PROGRAM_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    static final UUID YEAR_ID = UUID.fromString("66666666-6666-6666-6666-666666666666");

    @Autowired MockMvc mockMvc;
    @Autowired CourseEnrollmentRepository courseEnrollments;
    @MockBean AcademicClient academic;
    @MockBean IdentityClient identity;
    @MockBean RabbitTemplate rabbitTemplate;

    @BeforeEach
    void seed() {
        when(academic.getProgram(PROGRAM_ID)).thenReturn(new AcademicClient.Program(PROGRAM_ID, "BSC-CS",
                "BSc in Computer Science", UUID.randomUUID(), 4, 120, "ACTIVE"));
        when(academic.listAcademicYears()).thenReturn(List.of(new AcademicClient.AcademicYear(YEAR_ID, "2025/2026", "ACTIVE")));
        when(identity.getUser(STUDENT_ID)).thenReturn(new IdentityClient.User(STUDENT_ID, "K. Perera",
                "k.perera@uni.edu", "STUDENT", "ACTIVE", "STU-2025-0001"));
        when(identity.getUser(OTHER_STUDENT_ID)).thenReturn(new IdentityClient.User(OTHER_STUDENT_ID, "A. Jayawardena",
                "a.jayawardena@uni.edu", "STUDENT", "ACTIVE", "STU-2025-0002"));
    }

    // ---- one method per event contract ----

    public void studentEnrolled() throws Exception {
        offeringWithSeats(5);
        enroll(STUDENT_ID, false);
    }

    public void studentWaitlisted() throws Exception {
        offeringWithSeats(1);
        enroll(OTHER_STUDENT_ID, false);
        enroll(STUDENT_ID, true);
    }

    public void waitlistPromoted() throws Exception {
        offeringWithSeats(1);
        enroll(OTHER_STUDENT_ID, false);
        enroll(STUDENT_ID, true);
        drop(OTHER_STUDENT_ID);
    }

    public void courseDropped() throws Exception {
        offeringWithSeats(5);
        enroll(STUDENT_ID, false);
        drop(STUDENT_ID);
    }

    public void programRegistered() throws Exception {
        String body = "{\"studentId\":\"" + STUDENT_ID + "\",\"degreeProgramId\":\"" + PROGRAM_ID
                + "\",\"intakeAcademicYearId\":\"" + YEAR_ID + "\",\"currentYearOfStudy\":1}";
        mockMvc.perform(post("/program-enrollments")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(ADMIN_ID, "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void offeringWithSeats(int capacity) {
        when(academic.getOffering(OFFERING_ID)).thenReturn(new AcademicClient.Offering(OFFERING_ID, UUID.randomUUID(),
                UUID.randomUUID(), "COSC5105", "Introduction to Programming", 3, "CORE", PROGRAM_ID, "BSC-CS",
                "BSc in Computer Science", UUID.randomUUID(), 1, YEAR_ID, "2025/2026", "ACTIVE", UUID.randomUUID(),
                "Semester 1", null, null, capacity, UUID.randomUUID(), "PUBLISHED", true));
    }

    private void enroll(UUID student, boolean joinWaitlistIfFull) throws Exception {
        mockMvc.perform(post("/course-enrollments")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(student, "STUDENT"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"offeringId\":\"" + OFFERING_ID + "\",\"joinWaitlistIfFull\":" + joinWaitlistIfFull + "}"))
                .andExpect(status().isCreated());
    }

    private void drop(UUID student) throws Exception {
        UUID enrollmentId = courseEnrollments
                .findByStudentIdAndCourseOfferingIdAndDeletedAtIsNull(student, OFFERING_ID).orElseThrow().getId();
        mockMvc.perform(delete("/course-enrollments/" + enrollmentId)
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(student, "STUDENT")))
                .andExpect(status().isNoContent());
    }

    /** Hands the generated tests the last event this service sent to the exchange. */
    static class PublishedEvents {
        @Bean
        MessageVerifierReceiver<Message<?>> publishedEvents(RabbitTemplate rabbitTemplate) {
            return new MessageVerifierReceiver<>() {
                @Override
                public Message<?> receive(String destination, long timeout, TimeUnit timeUnit, YamlContract contract) {
                    // What a commit would do: send the events the services queued up.
                    TransactionSynchronizationManager.getSynchronizations().stream()
                            .filter(s -> s.getClass().getName().startsWith(EventPublisher.class.getName()))
                            .forEach(TransactionSynchronization::afterCommit);
                    ArgumentCaptor<String> routingKey = ArgumentCaptor.forClass(String.class);
                    ArgumentCaptor<Object> json = ArgumentCaptor.forClass(Object.class);
                    verify(rabbitTemplate, atLeastOnce()).convertAndSend(eq(destination), routingKey.capture(), json.capture());
                    return MessageBuilder.withPayload(json.getValue())
                            .setHeader("amqp_receivedRoutingKey", routingKey.getValue())
                            .build();
                }

                @Override
                public Message<?> receive(String destination, YamlContract contract) {
                    return receive(destination, 0, TimeUnit.SECONDS, contract);
                }
            };
        }
    }
}
