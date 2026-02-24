package com.example.payment.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentErrorCode enum.
 */
public class PaymentErrorCodeTest {

    @Test
    void testValidationErrors() {
        assertEquals(400, PaymentErrorCode.INVALID_CARD_NUMBER.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INVALID_EXPIRY.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INVALID_CVV.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INVALID_AMOUNT.getHttpStatus());
        assertEquals(400, PaymentErrorCode.MISSING_REQUIRED_FIELD.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INVALID_REQUEST.getHttpStatus());

        assertFalse(PaymentErrorCode.INVALID_CARD_NUMBER.isRetryable());
        assertFalse(PaymentErrorCode.INVALID_REQUEST.isRetryable());
    }

    @Test
    void testStateErrors() {
        assertEquals(409, PaymentErrorCode.INVALID_STATE_TRANSITION.getHttpStatus());
        assertEquals(409, PaymentErrorCode.ALREADY_CAPTURED.getHttpStatus());
        assertEquals(409, PaymentErrorCode.ALREADY_VOIDED.getHttpStatus());
        assertEquals(409, PaymentErrorCode.ALREADY_REFUNDED.getHttpStatus());
        assertEquals(409, PaymentErrorCode.NOT_CAPTURABLE.getHttpStatus());
        assertEquals(409, PaymentErrorCode.NOT_REFUNDABLE.getHttpStatus());
        assertEquals(409, PaymentErrorCode.IDEMPOTENCY_CONFLICT.getHttpStatus());

        assertFalse(PaymentErrorCode.INVALID_STATE_TRANSITION.isRetryable());
    }

    @Test
    void testDeclineErrors() {
        assertEquals(400, PaymentErrorCode.CARD_DECLINED.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INSUFFICIENT_FUNDS.getHttpStatus());
        assertEquals(400, PaymentErrorCode.CARD_EXPIRED.getHttpStatus());
        assertEquals(400, PaymentErrorCode.INVALID_CARD.getHttpStatus());
        assertEquals(400, PaymentErrorCode.FRAUD_SUSPECTED.getHttpStatus());

        assertFalse(PaymentErrorCode.CARD_DECLINED.isRetryable());
    }

    @Test
    void testGatewayErrors() {
        assertEquals(504, PaymentErrorCode.GATEWAY_TIMEOUT.getHttpStatus());
        assertEquals(503, PaymentErrorCode.GATEWAY_UNAVAILABLE.getHttpStatus());
        assertEquals(502, PaymentErrorCode.GATEWAY_COMMUNICATION_ERROR.getHttpStatus());
        assertEquals(502, PaymentErrorCode.GATEWAY_INVALID_RESPONSE.getHttpStatus());
        assertEquals(502, PaymentErrorCode.GATEWAY_ERROR.getHttpStatus());

        assertTrue(PaymentErrorCode.GATEWAY_TIMEOUT.isRetryable());
        assertTrue(PaymentErrorCode.GATEWAY_UNAVAILABLE.isRetryable());
        assertTrue(PaymentErrorCode.GATEWAY_COMMUNICATION_ERROR.isRetryable());
        assertFalse(PaymentErrorCode.GATEWAY_INVALID_RESPONSE.isRetryable());
        assertTrue(PaymentErrorCode.GATEWAY_ERROR.isRetryable());
    }

    @Test
    void testResourceErrors() {
        assertEquals(404, PaymentErrorCode.TRANSACTION_NOT_FOUND.getHttpStatus());
        assertEquals(404, PaymentErrorCode.ORDER_NOT_FOUND.getHttpStatus());

        assertFalse(PaymentErrorCode.TRANSACTION_NOT_FOUND.isRetryable());
    }

    @Test
    void testInternalErrors() {
        assertEquals(500, PaymentErrorCode.DATABASE_ERROR.getHttpStatus());
        assertEquals(500, PaymentErrorCode.INTERNAL_ERROR.getHttpStatus());
        assertEquals(500, PaymentErrorCode.UNEXPECTED_ERROR.getHttpStatus());

        assertTrue(PaymentErrorCode.DATABASE_ERROR.isRetryable());
        assertFalse(PaymentErrorCode.INTERNAL_ERROR.isRetryable());
    }

    @Test
    void testCategoryValidationError() {
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.INVALID_CARD_NUMBER.getCategory());
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.INVALID_EXPIRY.getCategory());
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.INVALID_CVV.getCategory());
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.INVALID_AMOUNT.getCategory());
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.MISSING_REQUIRED_FIELD.getCategory());
        assertEquals("VALIDATION_ERROR", PaymentErrorCode.INVALID_REQUEST.getCategory());
    }

    @Test
    void testCategoryStateError() {
        assertEquals("STATE_ERROR", PaymentErrorCode.INVALID_STATE_TRANSITION.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.ALREADY_CAPTURED.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.ALREADY_VOIDED.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.ALREADY_REFUNDED.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.NOT_CAPTURABLE.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.NOT_REFUNDABLE.getCategory());
        assertEquals("STATE_ERROR", PaymentErrorCode.IDEMPOTENCY_CONFLICT.getCategory());
    }

    @Test
    void testCategoryDeclineError() {
        assertEquals("DECLINE_ERROR", PaymentErrorCode.CARD_DECLINED.getCategory());
        assertEquals("DECLINE_ERROR", PaymentErrorCode.INSUFFICIENT_FUNDS.getCategory());
        assertEquals("DECLINE_ERROR", PaymentErrorCode.CARD_EXPIRED.getCategory());
        assertEquals("DECLINE_ERROR", PaymentErrorCode.FRAUD_SUSPECTED.getCategory());
    }

    @Test
    void testCategoryNotFound() {
        assertEquals("NOT_FOUND", PaymentErrorCode.TRANSACTION_NOT_FOUND.getCategory());
        assertEquals("NOT_FOUND", PaymentErrorCode.ORDER_NOT_FOUND.getCategory());
    }

    @Test
    void testCategoryGatewayError() {
        assertEquals("GATEWAY_ERROR", PaymentErrorCode.GATEWAY_TIMEOUT.getCategory());
        assertEquals("GATEWAY_ERROR", PaymentErrorCode.GATEWAY_UNAVAILABLE.getCategory());
        assertEquals("GATEWAY_ERROR", PaymentErrorCode.GATEWAY_COMMUNICATION_ERROR.getCategory());
        assertEquals("GATEWAY_ERROR", PaymentErrorCode.GATEWAY_INVALID_RESPONSE.getCategory());
        assertEquals("GATEWAY_ERROR", PaymentErrorCode.GATEWAY_ERROR.getCategory());
    }

    @Test
    void testCategoryInternalError() {
        assertEquals("INTERNAL_ERROR", PaymentErrorCode.DATABASE_ERROR.getCategory());
        assertEquals("INTERNAL_ERROR", PaymentErrorCode.INTERNAL_ERROR.getCategory());
        assertEquals("INTERNAL_ERROR", PaymentErrorCode.UNEXPECTED_ERROR.getCategory());
    }

    @ParameterizedTest
    @EnumSource(PaymentErrorCode.class)
    void testAllCodesHaveRequiredFields(PaymentErrorCode code) {
        assertNotNull(code.getCode());
        assertFalse(code.getCode().isBlank());

        assertNotNull(code.getDefaultMessage());
        assertFalse(code.getDefaultMessage().isBlank());

        assertTrue(code.getHttpStatus() >= 400 && code.getHttpStatus() < 600);

        assertNotNull(code.getCategory());
        assertFalse(code.getCategory().isBlank());
    }
}

