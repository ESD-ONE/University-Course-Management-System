package com.ucms.grading.contract;

import com.ucms.grading.KeycloakTestTokens;
import com.ucms.grading.client.AcademicClient;
import com.ucms.grading.client.BaseClient;
import com.ucms.grading.client.EnrollmentClient;
import com.ucms.grading.client.IdentityClient;
import com.ucms.grading.domain.Assignment;
import com.ucms.grading.messaging.EventPublisher;
import com.ucms.grading.repository.AssignmentRepository;
import com.ucms.grading.storage.FileStorage;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    static final UUID LECTURER_ID = UUID.fromString("88888888-8888-8888-8888-888888888888");
    static final UUID OFFERING_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired MockMvc mockMvc;
    @Autowired AssignmentRepository assignments;
    @MockBean AcademicClient academic;
    @MockBean EnrollmentClient enrollment;
    @MockBean IdentityClient identity;
    @MockBean BaseClient base;
    @MockBean FileStorage storage;
    @MockBean RabbitTemplate rabbitTemplate;

    @BeforeEach
    void seed() {
        when(academic.getOffering(OFFERING_ID)).thenReturn(new AcademicClient.Offering(OFFERING_ID, "COSC5105",
                "Introduction to Programming", 3, UUID.randomUUID(), UUID.randomUUID(), "2025/2026", "ACTIVE",
                "Semester 1", 40, LECTURER_ID, "PUBLISHED"));
        when(enrollment.isEnrolled(STUDENT_ID, OFFERING_ID)).thenReturn(true);
    }

    // ---- one method per event contract ----

    public void assignmentPublished() throws Exception {
        Assignment assignment = assignment(Assignment.Status.DRAFT);
        mockMvc.perform(patch("/assignments/" + assignment.getId() + "/status")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(LECTURER_ID, "LECTURER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(status().isOk());
    }

    public void submissionReceived() throws Exception {
        submit(assignment(Assignment.Status.OPEN));
    }

    public void gradePosted() throws Exception {
        String submissionId = submit(assignment(Assignment.Status.OPEN));
        mockMvc.perform(put("/submissions/" + submissionId + "/grade")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(LECTURER_ID, "LECTURER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"marks\":92,\"feedback\":\"Great work\"}"))
                .andExpect(status().isOk());
    }

    private Assignment assignment(Assignment.Status status) {
        Assignment assignment = new Assignment();
        assignment.setCourseOfferingId(OFFERING_ID);
        assignment.setTitle("Essay 1");
        assignment.setDescription("Write an essay.");
        assignment.setDueDate(OffsetDateTime.now().plusDays(7));
        assignment.setMaxMarks(100);
        assignment.setStatus(status);
        return assignments.save(assignment);
    }

    private String submit(Assignment assignment) throws Exception {
        String body = mockMvc.perform(multipart("/assignments/" + assignment.getId() + "/submissions")
                        .file(new MockMultipartFile("file", "essay.zip", "application/zip", "PK...".getBytes()))
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(STUDENT_ID, "STUDENT")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"submissionId\":\"([^\"]+)\".*", "$1");
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
