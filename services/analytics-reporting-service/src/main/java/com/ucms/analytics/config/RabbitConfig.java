package com.ucms.analytics.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Analytics' own queue — consumes the same events as Notification, independently
 *  (flow-02: "both consume the same event independently and in parallel"). */
@Configuration
@ConditionalOnProperty(name = "ucms.events.enabled", havingValue = "true", matchIfMissing = true)
public class RabbitConfig {

    public static final String QUEUE = "analytics.events";
    private static final String DLX = "ucms.events.dlx";
    private static final String DLQ = "analytics.events.dlq";

    @Bean
    public Declarables analyticsTopology() {
        TopicExchange exchange = new TopicExchange("ucms.events", true, false);
        DirectExchange dlx = new DirectExchange(DLX, true, false);
        Queue queue = QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", DLQ)
                .build();
        Queue dlq = QueueBuilder.durable(DLQ).build();
        return new Declarables(exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with("enrollment.#"),
                BindingBuilder.bind(queue).to(exchange).with("grading.#"),
                BindingBuilder.bind(queue).to(exchange).with("identity.#"),
                BindingBuilder.bind(dlq).to(dlx).with(DLQ));
    }
}
