package com.example.payment.event;

/**
 * Interface for payment event listeners.
 * Implement this to react to payment events from the queue.
 */
@FunctionalInterface
public interface PaymentEventListener {
    void onEvent(PaymentEvent event);
}

