package com.ucms.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Notification's own durable queue on the shared "ucms.events" topic exchange, bound to
 * every event family it reacts to. Messages that still fail after the listener's retries
 * are dead-lettered to notification.events.dlq for inspection rather than looping.
 */
@Configuration
@ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    public static final String EXCHANGE = "ucms.events";
    public static final String QUEUE = "notification.events";
    private static final String DLX = "ucms.events.dlx";
    private static final String DLQ = "notification.events.dlq";

    @Bean
    public Declarables notificationTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        Binding enrollment = BindingBuilder.bind(queue).to(exchange).with("enrollment.#");
        Binding grading = BindingBuilder.bind(queue).to(exchange).with("grading.#");
        Binding identity = BindingBuilder.bind(queue).to(exchange).with("identity.#");
        Binding analytics = BindingBuilder.bind(queue).to(exchange).with("analytics.#");
        Binding dead = BindingBuilder.bind(dlq).to(dlx).with(DLQ);
        return new Declarables(exchange, dlx, queue, dlq, enrollment, grading, identity, analytics, dead);
    }
}
