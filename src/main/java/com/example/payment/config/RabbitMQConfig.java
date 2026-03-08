package com.example.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for durable, async payment event processing.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Why upgrade from in-memory queue to RabbitMQ?</p>
 * <p><strong>A:</strong> In-memory LinkedBlockingQueue loses all pending events on crash.
 * For production payment processing, event durability is critical — webhook events,
 * payment notifications, and audit events must survive application restarts.
 * RabbitMQ provides durable queues, acknowledgment-based delivery, dead-letter handling,
 * and multi-instance scalability.</p>
 *
 * <h2>Queue Topology:</h2>
 * <pre>
 * payment-events-exchange (topic)
 *   └─ payment.# → payment-events-queue (durable)
 *
 * webhook-events-exchange (topic)
 *   └─ webhook.# → webhook-events-queue (durable)
 *
 * Dead Letter:
 *   payment-events-dlx → payment-events-dlq
 *   webhook-events-dlx → webhook-events-dlq
 * </pre>
 */
@Configuration
@ConditionalOnBean(ConnectionFactory.class)
public class RabbitMQConfig {

    // ==================== Exchange Names ====================
    public static final String PAYMENT_EVENTS_EXCHANGE = "payment-events-exchange";
    public static final String WEBHOOK_EVENTS_EXCHANGE = "webhook-events-exchange";

    // ==================== Queue Names ====================
    public static final String PAYMENT_EVENTS_QUEUE = "payment-events-queue";
    public static final String WEBHOOK_EVENTS_QUEUE = "webhook-events-queue";

    // ==================== Dead Letter ====================
    public static final String PAYMENT_EVENTS_DLX = "payment-events-dlx";
    public static final String PAYMENT_EVENTS_DLQ = "payment-events-dlq";
    public static final String WEBHOOK_EVENTS_DLX = "webhook-events-dlx";
    public static final String WEBHOOK_EVENTS_DLQ = "webhook-events-dlq";

    // ==================== Routing Keys ====================
    public static final String PAYMENT_ROUTING_KEY = "payment.#";
    public static final String WEBHOOK_ROUTING_KEY = "webhook.#";

    // ==================== Message Converter ====================

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }

    // ==================== Payment Events ====================

    @Bean
    public TopicExchange paymentEventsExchange() {
        return new TopicExchange(PAYMENT_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue paymentEventsQueue() {
        return QueueBuilder.durable(PAYMENT_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", PAYMENT_EVENTS_DLX)
                .build();
    }

    @Bean
    public Binding paymentEventsBinding() {
        return BindingBuilder.bind(paymentEventsQueue())
                .to(paymentEventsExchange())
                .with(PAYMENT_ROUTING_KEY);
    }

    // ==================== Webhook Events ====================

    @Bean
    public TopicExchange webhookEventsExchange() {
        return new TopicExchange(WEBHOOK_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public Queue webhookEventsQueue() {
        return QueueBuilder.durable(WEBHOOK_EVENTS_QUEUE)
                .withArgument("x-dead-letter-exchange", WEBHOOK_EVENTS_DLX)
                .build();
    }

    @Bean
    public Binding webhookEventsBinding() {
        return BindingBuilder.bind(webhookEventsQueue())
                .to(webhookEventsExchange())
                .with(WEBHOOK_ROUTING_KEY);
    }

    // ==================== Dead Letter Queues ====================

    @Bean
    public FanoutExchange paymentEventsDlx() {
        return new FanoutExchange(PAYMENT_EVENTS_DLX);
    }

    @Bean
    public Queue paymentEventsDlq() {
        return QueueBuilder.durable(PAYMENT_EVENTS_DLQ).build();
    }

    @Bean
    public Binding paymentEventsDlqBinding() {
        return BindingBuilder.bind(paymentEventsDlq()).to(paymentEventsDlx());
    }

    @Bean
    public FanoutExchange webhookEventsDlx() {
        return new FanoutExchange(WEBHOOK_EVENTS_DLX);
    }

    @Bean
    public Queue webhookEventsDlq() {
        return QueueBuilder.durable(WEBHOOK_EVENTS_DLQ).build();
    }

    @Bean
    public Binding webhookEventsDlqBinding() {
        return BindingBuilder.bind(webhookEventsDlq()).to(webhookEventsDlx());
    }
}

