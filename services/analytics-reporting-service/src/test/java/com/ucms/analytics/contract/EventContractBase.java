package com.ucms.analytics.contract;

import com.ucms.analytics.KeycloakTestTokens;
import com.ucms.analytics.client.SourceClients;
import com.ucms.analytics.domain.Report;
import com.ucms.analytics.messaging.EventPublisher;
import com.ucms.analytics.repository.ReportRepository;
import com.ucms.analytics.service.ScheduleService;
import com.ucms.analytics.storage.FileStorage;
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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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
@SpringBootTest(properties = {"ucms.events.enabled=true", "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@AutoConfigureMessageVerifier
@Import(EventContractBase.PublishedEvents.class)
public abstract class EventContractBase {

    @Autowired MockMvc mockMvc;
    @Autowired ReportRepository reports;
    @Autowired ScheduleService schedules;
    @MockBean SourceClients sources;
    @MockBean FileStorage storage;
    @MockBean RabbitTemplate rabbitTemplate;

    // ---- one method per event contract ----

    public void reportGenerated() throws Exception {
        Report report = new Report();
        report.setName("Course Pass Rate");
        report.setDescription("Course Pass Rate");
        report.setFormat("CSV");
        report = reports.save(report);

        String created = mockMvc.perform(post("/scheduled-reports")
                        .header("Authorization", "Bearer " + KeycloakTestTokens.user(UUID.randomUUID(), "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"" + report.getId() + "\",\"frequency\":\"DAILY\",\"time\":\"06:00\","
                                + "\"recipients\":\"registrar@uni.edu\"}"))
                .andReturn().getResponse().getContentAsString();
        schedules.runOne(UUID.fromString(created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1")));
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
