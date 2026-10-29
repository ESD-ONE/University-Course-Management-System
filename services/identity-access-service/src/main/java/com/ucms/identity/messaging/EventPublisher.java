package com.ucms.identity.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes domain events to the shared "ucms.events" topic exchange, routing key = the
 * event type (e.g. "identity.user-created"). Same contract in every service: a JSON
 * envelope {eventId, type, occurredAt, data}.
 *
 * Published only after the surrounding transaction commits, so a rolled-back write never
 * announces something that didn't happen. Fire-and-forget by design (see
 * flow-01-student-enrollment.mmd): a broker outage is logged, never surfaced to the
 * user whose request already succeeded. There is no outbox table, so an event can be
 * lost if the broker is down at commit time — accepted for notifications/analytics,
 * both of which also reconcile periodically.
 */
@Component
public class EventPublisher {

    public static final String EXCHANGE = "ucms.events";
    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public EventPublisher(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                          @Value("${ucms.events.enabled:true}") boolean enabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
    }

    public void publish(String type, Map<String, Object> data) {
        if (!enabled) return;
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", UUID.randomUUID().toString());
        envelope.put("type", type);
        envelope.put("occurredAt", Instant.now().toString());
        envelope.put("data", data);
        Runnable send = () -> {
            try {
                rabbitTemplate.convertAndSend(EXCHANGE, type, objectMapper.writeValueAsString(envelope));
            } catch (Exception ex) {
                log.warn("Could not publish {} event: {}", type, ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
