package com.ucms.identity.config;

import com.ucms.identity.messaging.EventPublisher;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    /** Declared by every publishing service (idempotent on the broker), so whichever
     * service starts first creates it. */
    @Bean
    @ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
    public TopicExchange ucmsEventsExchange() {
        return new TopicExchange(EventPublisher.EXCHANGE, true, false);
    }
}
