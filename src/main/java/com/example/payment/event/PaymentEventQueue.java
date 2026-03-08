package com.example.payment.event;

import com.example.payment.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Hybrid event queue: publishes to RabbitMQ for durability AND dispatches locally.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we use RabbitMQ/Kafka or an in-memory queue?</p>
 * <p><strong>A:</strong> For production, we need durable event processing. RabbitMQ provides
 * persistent queues, acknowledgment-based delivery, and dead-letter handling. We publish
 * all events to RabbitMQ durable queues AND dispatch locally via a RabbitListener consumer.
 * This ensures events survive application crashes and supports multi-instance scaling.</p>
 *
 * <p><strong>Q:</strong> What happens if RabbitMQ is unavailable?</p>
 * <p><strong>A:</strong> Events fall back to the in-memory LinkedBlockingQueue for local
 * dispatch. Critical state changes are already persisted via AuditService before publishing.
 * RabbitMQ connection recovery is handled automatically by Spring AMQP.</p>
 */
@Component
public class PaymentEventQueue {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventQueue.class);

    private final LinkedBlockingQueue<PaymentEvent> fallbackQueue = new LinkedBlockingQueue<>(10000);
    private final List<PaymentEventListener> listeners = new CopyOnWriteArrayList<>();
    private final RabbitTemplate rabbitTemplate;

    public PaymentEventQueue(@Autowired(required = false) RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
        if (rabbitTemplate == null) {
            log.info("RabbitMQ not available — running in in-memory-only mode");
        }
    }

    @PostConstruct
    public void start() {
        log.info("Payment event queue started (RabbitMQ-backed with local fallback)");
    }

    @PreDestroy
    public void stop() {
        // Drain any fallback queue events
        int remaining = fallbackQueue.size();
        if (remaining > 0 && rabbitTemplate != null) {
            log.warn("Shutting down with {} events in fallback queue. Attempting flush to RabbitMQ...", remaining);
            PaymentEvent event;
            while ((event = fallbackQueue.poll()) != null) {
                try {
                    String routingKey = resolveRoutingKey(event);
                    rabbitTemplate.convertAndSend(RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE, routingKey, event);
                } catch (Exception e) {
                    log.error("Failed to flush event to RabbitMQ on shutdown: {}", event.eventType());
                }
            }
        } else if (remaining > 0) {
            log.warn("Shutting down with {} events in fallback queue. RabbitMQ not available — events lost.", remaining);
        }
        log.info("Payment event queue stopped");
    }

    /**
     * Publish a payment event to RabbitMQ (primary) with in-memory fallback.
     *
     * @param event The payment event
     * @return true if the event was successfully published
     */
    public boolean publish(PaymentEvent event) {
        // Try RabbitMQ first if available
        if (rabbitTemplate != null) {
            try {
                String routingKey = resolveRoutingKey(event);
                String exchange = isWebhookEvent(event)
                        ? RabbitMQConfig.WEBHOOK_EVENTS_EXCHANGE
                        : RabbitMQConfig.PAYMENT_EVENTS_EXCHANGE;

                rabbitTemplate.convertAndSend(exchange, routingKey, event);
                log.debug("Published event to RabbitMQ: type={}, orderId={}, txId={}",
                        event.eventType(), event.orderId(), event.transactionId());
                return true;
            } catch (Exception e) {
                log.warn("RabbitMQ publish failed, falling back to in-memory queue: {}", e.getMessage());
            }
        }

        // Fallback to in-memory dispatch
        boolean offered = fallbackQueue.offer(event);
        if (offered) {
            dispatch(event);
        } else {
            log.error("Event queue full! Dropped event: type={}", event.eventType());
        }
        return offered;
    }

    /**
     * RabbitMQ consumer for payment events — dispatches to registered listeners.
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_EVENTS_QUEUE)
    public void consumePaymentEvent(PaymentEvent event) {
        log.debug("Consumed payment event from RabbitMQ: type={}", event.eventType());
        dispatch(event);
    }

    /**
     * RabbitMQ consumer for webhook events — dispatches to registered listeners.
     */
    @RabbitListener(queues = RabbitMQConfig.WEBHOOK_EVENTS_QUEUE)
    public void consumeWebhookEvent(PaymentEvent event) {
        log.debug("Consumed webhook event from RabbitMQ: type={}", event.eventType());
        dispatch(event);
    }

    /**
     * Register an event listener.
     */
    public void addListener(PaymentEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Get current fallback queue size (for metrics).
     */
    public int getQueueSize() {
        return fallbackQueue.size();
    }

    private void dispatch(PaymentEvent event) {
        for (PaymentEventListener listener : listeners) {
            try {
                listener.onEvent(event);
            } catch (Exception e) {
                log.error("Listener {} failed for event {}: {}",
                        listener.getClass().getSimpleName(), event.eventType(), e.getMessage());
            }
        }
    }

    private String resolveRoutingKey(PaymentEvent event) {
        if (isWebhookEvent(event)) {
            return "webhook." + event.eventType().toLowerCase();
        }
        return "payment." + event.eventType().toLowerCase();
    }

    private boolean isWebhookEvent(PaymentEvent event) {
        return PaymentEvent.WEBHOOK_RECEIVED.equals(event.eventType());
    }
}

