package com.ucms.notification.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ucms.notification.config.RabbitConfig;
import com.ucms.notification.service.NotificationService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/** Consumes the shared event envelope {eventId, type, occurredAt, data}. A malformed
 *  message is rejected straight to the dead-letter queue; a processing failure (e.g.
 *  Enrollment briefly down during a fan-out) is retried, then dead-lettered. */
@Component
@ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
public class EventListener {

    private final NotificationService notifications;
    private final ObjectMapper objectMapper;

    public EventListener(NotificationService notifications, ObjectMapper objectMapper) {
        this.notifications = notifications;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    @SuppressWarnings("unchecked")
    public void onEvent(String message) {
        Map<String, Object> envelope;
        UUID eventId;
        try {
            envelope = objectMapper.readValue(message, Map.class);
            eventId = UUID.fromString((String) envelope.get("eventId"));
        } catch (Exception ex) {
            throw new AmqpRejectAndDontRequeueException("Malformed event: " + ex.getMessage());
        }
        Object data = envelope.get("data");
        notifications.handle(eventId, (String) envelope.get("type"), data instanceof Map ? (Map<String, Object>) data : Map.of());
    }
}
