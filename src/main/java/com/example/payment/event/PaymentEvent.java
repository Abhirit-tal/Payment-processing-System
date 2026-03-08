package com.example.payment.event;

import java.time.Instant;
import java.util.Map;

/**
 * Represents a payment event for async processing via the in-memory event queue.
 *
 * @param eventType   Type of event (e.g., PURCHASE_SUCCESS, REFUND_COMPLETED, WEBHOOK_RECEIVED)
 * @param orderId     Associated order ID (nullable for webhook events)
 * @param transactionId Provider transaction ID
 * @param timestamp   When the event occurred
 * @param payload     Additional event data
 */
public record PaymentEvent(
    String eventType,
    Long orderId,
    String transactionId,
    Instant timestamp,
    Map<String, Object> payload
) {
    // Event type constants
    public static final String PURCHASE_SUCCESS = "PURCHASE_SUCCESS";
    public static final String PURCHASE_FAILED = "PURCHASE_FAILED";
    public static final String AUTHORIZE_SUCCESS = "AUTHORIZE_SUCCESS";
    public static final String CAPTURE_SUCCESS = "CAPTURE_SUCCESS";
    public static final String VOID_SUCCESS = "VOID_SUCCESS";
    public static final String REFUND_SUCCESS = "REFUND_SUCCESS";
    public static final String WEBHOOK_RECEIVED = "WEBHOOK_RECEIVED";
    public static final String SUBSCRIPTION_CREATED = "SUBSCRIPTION_CREATED";
    public static final String SUBSCRIPTION_CANCELLED = "SUBSCRIPTION_CANCELLED";

    public static PaymentEvent of(String eventType, Long orderId, String txId, Map<String, Object> payload) {
        return new PaymentEvent(eventType, orderId, txId, Instant.now(), payload);
    }

    public static PaymentEvent of(String eventType, Long orderId, String txId) {
        return new PaymentEvent(eventType, orderId, txId, Instant.now(), Map.of());
    }
}

