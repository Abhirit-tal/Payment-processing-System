package com.example.payment.exception;

import com.example.payment.model.PaymentState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for all exception classes.
 */
public class ExceptionTest {

    // ========== InvalidStateTransitionException Tests ==========

    @Test
    void testInvalidStateTransitionExceptionBasicConstructor() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                PaymentState.CREATED, PaymentState.CAPTURED, 123L);

        assertEquals(PaymentState.CREATED, ex.getFromState());
        assertEquals(PaymentState.CAPTURED, ex.getToState());
        assertEquals(123L, ex.getOrderId());
        assertEquals("INVALID_STATE_TRANSITION", ex.getErrorCode());
        assertFalse(ex.isRetryable());
        assertTrue(ex.getMessage().contains("created"));
        assertTrue(ex.getMessage().contains("captured"));
        assertTrue(ex.getMessage().contains("123"));
    }

    @Test
    void testInvalidStateTransitionExceptionWithCustomMessage() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                PaymentState.PENDING, PaymentState.REFUNDED, 456L, "Custom error message");

        assertEquals(PaymentState.PENDING, ex.getFromState());
        assertEquals(PaymentState.REFUNDED, ex.getToState());
        assertEquals(456L, ex.getOrderId());
        assertEquals("Custom error message", ex.getMessage());
    }

    @Test
    void testInvalidStateTransitionExceptionWithNullStates() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                null, null, 789L);

        assertNull(ex.getFromState());
        assertNull(ex.getToState());
        assertTrue(ex.getMessage().contains("null"));
    }

    // ========== TransientPaymentException Tests ==========

    @Test
    void testTransientPaymentExceptionDefaultRetryDelay() {
        TransientPaymentException ex = new TransientPaymentException("Network error");

        assertEquals("Network error", ex.getMessage());
        assertEquals("TRANSIENT_ERROR", ex.getErrorCode());
        assertTrue(ex.isRetryable());
        assertEquals(1000, ex.getSuggestedRetryDelayMs());
    }

    @Test
    void testTransientPaymentExceptionWithCause() {
        Exception cause = new RuntimeException("Root cause");
        TransientPaymentException ex = new TransientPaymentException("Network error", cause);

        assertEquals("Network error", ex.getMessage());
        assertEquals(cause, ex.getCause());
        assertEquals(1000, ex.getSuggestedRetryDelayMs());
    }

    @Test
    void testTransientPaymentExceptionWithCustomRetryDelay() {
        TransientPaymentException ex = new TransientPaymentException("Timeout", 5000);

        assertEquals(5000, ex.getSuggestedRetryDelayMs());
    }

    @Test
    void testTransientPaymentExceptionWithCauseAndCustomRetryDelay() {
        Exception cause = new RuntimeException("Root cause");
        TransientPaymentException ex = new TransientPaymentException("Timeout", cause, 3000);

        assertEquals(cause, ex.getCause());
        assertEquals(3000, ex.getSuggestedRetryDelayMs());
    }

    // ========== GatewayTimeoutException Tests ==========

    @Test
    void testGatewayTimeoutException() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Gateway timed out");

        assertEquals("Gateway timed out", ex.getMessage());
        assertEquals("GATEWAY_TIMEOUT", ex.getErrorCode());
        assertTrue(ex.isRetryable());
        assertEquals(2000, ex.getSuggestedRetryDelayMs());
    }

    @Test
    void testGatewayTimeoutExceptionWithCause() {
        Exception cause = new RuntimeException("Socket timeout");
        GatewayTimeoutException ex = new GatewayTimeoutException("Gateway timed out", cause);

        assertEquals(cause, ex.getCause());
        assertEquals(2000, ex.getSuggestedRetryDelayMs());
    }

    // ========== PermanentPaymentException Tests ==========

    @Test
    void testPermanentPaymentException() {
        PermanentPaymentException ex = new PermanentPaymentException("Validation failed", "VALIDATION_ERROR");

        assertEquals("Validation failed", ex.getMessage());
        assertEquals("VALIDATION_ERROR", ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    @Test
    void testPermanentPaymentExceptionWithCause() {
        Exception cause = new IllegalArgumentException("Invalid amount");
        PermanentPaymentException ex = new PermanentPaymentException("Validation failed", "VALIDATION_ERROR", cause);

        assertEquals(cause, ex.getCause());
        assertFalse(ex.isRetryable());
    }

    // ========== GatewayDeclinedException Tests ==========

    @Test
    void testGatewayDeclinedExceptionBasic() {
        GatewayDeclinedException ex = new GatewayDeclinedException("Card declined", "D001");

        assertEquals("Card declined", ex.getMessage());
        assertEquals("CARD_DECLINED", ex.getErrorCode());
        assertEquals("D001", ex.getDeclineCode());
        assertNull(ex.getAvsResult());
        assertNull(ex.getCvvResult());
        assertFalse(ex.isRetryable());
    }

    @Test
    void testGatewayDeclinedExceptionWithAllDetails() {
        GatewayDeclinedException ex = new GatewayDeclinedException(
                "Card declined - insufficient funds", "D005", "A", "M");

        assertEquals("Card declined - insufficient funds", ex.getMessage());
        assertEquals("D005", ex.getDeclineCode());
        assertEquals("A", ex.getAvsResult());
        assertEquals("M", ex.getCvvResult());
    }

    // ========== IdempotencyConflictException Tests ==========

    @Test
    void testIdempotencyConflictException() {
        IdempotencyConflictException ex = new IdempotencyConflictException(
                "Duplicate idempotency key with different request");

        assertEquals("Duplicate idempotency key with different request", ex.getMessage());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.getErrorCode());
        assertFalse(ex.isRetryable());
    }

    // ========== PaymentException Common Methods Tests ==========

    @Test
    void testPaymentExceptionProviderError() {
        TransientPaymentException ex = new TransientPaymentException("Error");

        ex.setProviderErrorCode("E001");
        ex.setProviderErrorMessage("Provider error message");

        assertEquals("E001", ex.getProviderErrorCode());
        assertEquals("Provider error message", ex.getProviderErrorMessage());
    }

    @Test
    void testPaymentExceptionRequestId() {
        TransientPaymentException ex = new TransientPaymentException("Error");

        ex.setRequestId("req-12345");

        assertEquals("req-12345", ex.getRequestId());
    }

    @Test
    void testPaymentExceptionFluentMethods() {
        TransientPaymentException ex = new TransientPaymentException("Error");

        PaymentException result = ex
                .withProviderError("E002", "Provider message")
                .withRequestId("req-abc");

        assertSame(ex, result);
        assertEquals("E002", ex.getProviderErrorCode());
        assertEquals("Provider message", ex.getProviderErrorMessage());
        assertEquals("req-abc", ex.getRequestId());
    }
}

