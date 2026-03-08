package com.example.payment.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Default event listener that logs all payment events.
 * In production, this would be replaced/augmented with notification services,
 * metrics publishers, or message broker publishers.
 */
@Component
public class LoggingPaymentEventListener implements PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingPaymentEventListener.class);

    private final PaymentEventQueue eventQueue;

    public LoggingPaymentEventListener(PaymentEventQueue eventQueue) {
        this.eventQueue = eventQueue;
    }

    @PostConstruct
    public void register() {
        eventQueue.addListener(this);
    }

    @Override
    public void onEvent(PaymentEvent event) {
        log.info("[EVENT] type={}, orderId={}, txId={}, timestamp={}",
                event.eventType(), event.orderId(), event.transactionId(), event.timestamp());

        // In production, this is where you'd:
        // - Send email notifications
        // - Update external dashboards
        // - Publish to a message broker for downstream consumers
        // - Update analytics/metrics
    }
}

