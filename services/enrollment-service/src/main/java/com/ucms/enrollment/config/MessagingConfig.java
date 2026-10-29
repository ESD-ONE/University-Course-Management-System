package com.ucms.enrollment.config;

import com.ucms.enrollment.messaging.EventPublisher;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessagingConfig {

    @Bean
    @ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
    public TopicExchange ucmsEventsExchange() {
        return new TopicExchange(EventPublisher.EXCHANGE, true, false);
    }
}
