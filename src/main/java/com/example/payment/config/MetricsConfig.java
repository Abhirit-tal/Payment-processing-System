package com.example.payment.config;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventListener;
import com.example.payment.event.PaymentEventQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Custom Micrometer metrics for payment operations.
 *
 * <p>Registers as a PaymentEventQueue listener to automatically track
 * payment metrics via Prometheus:</p>
 * <ul>
 *   <li>{@code payment_events_total} — Counter by event type</li>
 *   <li>{@code payment_queue_size} — Gauge for current queue depth</li>
 *   <li>{@code webhook_events_total} — Counter for webhook events</li>
 *   <li>{@code subscription_events_total} — Counter for subscription events</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we track metrics in the service layer or via event listener?</p>
 * <p><strong>A:</strong> Via event listener. This decouples metrics collection from business logic.
 * The PaymentEventQueue already dispatches events to all registered listeners, so we simply
 * register a metrics listener. This means zero changes to PaymentService/SubscriptionService.</p>
 */
@Component
public class MetricsConfig implements PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

    private final MeterRegistry meterRegistry;
    private final PaymentEventQueue eventQueue;

    private Counter purchaseSuccessCounter;
    private Counter purchaseFailedCounter;
    private Counter authorizeSuccessCounter;
    private Counter captureSuccessCounter;
    private Counter voidSuccessCounter;
    private Counter refundSuccessCounter;
    private Counter webhookReceivedCounter;
    private Counter subscriptionCreatedCounter;
    private Counter subscriptionCancelledCounter;

    public MetricsConfig(MeterRegistry meterRegistry, PaymentEventQueue eventQueue) {
        this.meterRegistry = meterRegistry;
        this.eventQueue = eventQueue;
    }

    @PostConstruct
    public void init() {
        // Payment counters
        purchaseSuccessCounter = Counter.builder("payment_events_total")
                .tag("type", "purchase_success")
                .description("Total successful purchases")
                .register(meterRegistry);

        purchaseFailedCounter = Counter.builder("payment_events_total")
                .tag("type", "purchase_failed")
                .description("Total failed purchases")
                .register(meterRegistry);

        authorizeSuccessCounter = Counter.builder("payment_events_total")
                .tag("type", "authorize_success")
                .description("Total successful authorizations")
                .register(meterRegistry);

        captureSuccessCounter = Counter.builder("payment_events_total")
                .tag("type", "capture_success")
                .description("Total successful captures")
                .register(meterRegistry);

        voidSuccessCounter = Counter.builder("payment_events_total")
                .tag("type", "void_success")
                .description("Total successful voids")
                .register(meterRegistry);

        refundSuccessCounter = Counter.builder("payment_events_total")
                .tag("type", "refund_success")
                .description("Total successful refunds")
                .register(meterRegistry);

        // Webhook counter
        webhookReceivedCounter = Counter.builder("webhook_events_total")
                .description("Total webhook events received")
                .register(meterRegistry);

        // Subscription counters
        subscriptionCreatedCounter = Counter.builder("subscription_events_total")
                .tag("type", "created")
                .description("Total subscriptions created")
                .register(meterRegistry);

        subscriptionCancelledCounter = Counter.builder("subscription_events_total")
                .tag("type", "cancelled")
                .description("Total subscriptions cancelled")
                .register(meterRegistry);

        // Queue size gauge
        meterRegistry.gauge("payment_queue_size", eventQueue, PaymentEventQueue::getQueueSize);

        // Register as event listener
        eventQueue.addListener(this);
        log.info("Payment metrics registered with Micrometer/Prometheus");
    }

    @Override
    public void onEvent(PaymentEvent event) {
        switch (event.eventType()) {
            case PaymentEvent.PURCHASE_SUCCESS -> purchaseSuccessCounter.increment();
            case PaymentEvent.PURCHASE_FAILED -> purchaseFailedCounter.increment();
            case PaymentEvent.AUTHORIZE_SUCCESS -> authorizeSuccessCounter.increment();
            case PaymentEvent.CAPTURE_SUCCESS -> captureSuccessCounter.increment();
            case PaymentEvent.VOID_SUCCESS -> voidSuccessCounter.increment();
            case PaymentEvent.REFUND_SUCCESS -> refundSuccessCounter.increment();
            case PaymentEvent.WEBHOOK_RECEIVED -> webhookReceivedCounter.increment();
            case PaymentEvent.SUBSCRIPTION_CREATED -> subscriptionCreatedCounter.increment();
            case PaymentEvent.SUBSCRIPTION_CANCELLED -> subscriptionCancelledCounter.increment();
        }
    }
}
