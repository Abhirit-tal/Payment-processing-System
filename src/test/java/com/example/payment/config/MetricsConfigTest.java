package com.example.payment.config;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventQueue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for MetricsConfig.
 */
public class MetricsConfigTest {

    private MeterRegistry meterRegistry;
    private PaymentEventQueue eventQueue;
    private MetricsConfig metricsConfig;

    @BeforeEach
    void setup() {
        meterRegistry = new SimpleMeterRegistry();
        RabbitTemplate mockRabbitTemplate = mock(RabbitTemplate.class);
        eventQueue = new PaymentEventQueue(mockRabbitTemplate);
        eventQueue.start();
        metricsConfig = new MetricsConfig(meterRegistry, eventQueue);
        metricsConfig.init();
    }

    @Test
    void purchaseSuccessIncrementsCounter() {
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.PURCHASE_SUCCESS, 1L, "tx-1"));
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.PURCHASE_SUCCESS, 2L, "tx-2"));

        Counter counter = meterRegistry.find("payment_events_total")
                .tag("type", "purchase_success").counter();
        assertNotNull(counter);
        assertEquals(2.0, counter.count());
    }

    @Test
    void purchaseFailedIncrementsCounter() {
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.PURCHASE_FAILED, 1L, "tx-1"));

        Counter counter = meterRegistry.find("payment_events_total")
                .tag("type", "purchase_failed").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void webhookReceivedIncrementsCounter() {
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.WEBHOOK_RECEIVED, null, "notif-1"));

        Counter counter = meterRegistry.find("webhook_events_total").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void subscriptionCreatedIncrementsCounter() {
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.SUBSCRIPTION_CREATED, null, "sub-1"));

        Counter counter = meterRegistry.find("subscription_events_total")
                .tag("type", "created").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void queueSizeGaugeRegistered() {
        Number queueSize = meterRegistry.find("payment_queue_size").gauge().value();
        assertNotNull(queueSize);
        assertEquals(0.0, queueSize.doubleValue());
    }

    @Test
    void allEventTypesHandled() {
        // Verify no exception for any event type
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.AUTHORIZE_SUCCESS, 1L, "tx"));
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.CAPTURE_SUCCESS, 1L, "tx"));
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.VOID_SUCCESS, 1L, "tx"));
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.REFUND_SUCCESS, 1L, "tx"));
        metricsConfig.onEvent(PaymentEvent.of(PaymentEvent.SUBSCRIPTION_CANCELLED, null, "sub"));

        // Verify counters exist and are positive
        assertNotNull(meterRegistry.find("payment_events_total").tag("type", "authorize_success").counter());
        assertNotNull(meterRegistry.find("payment_events_total").tag("type", "capture_success").counter());
        assertNotNull(meterRegistry.find("payment_events_total").tag("type", "void_success").counter());
        assertNotNull(meterRegistry.find("payment_events_total").tag("type", "refund_success").counter());
        assertNotNull(meterRegistry.find("subscription_events_total").tag("type", "cancelled").counter());
    }
}

