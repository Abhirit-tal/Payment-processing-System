package com.example.payment.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GatewayResponseType enum.
 */
public class GatewayResponseTypeTest {

    @Test
    void testApprovedProperties() {
        GatewayResponseType type = GatewayResponseType.APPROVED;
        assertEquals(1, type.getCode());
        assertEquals("approved", type.getName());
        assertFalse(type.isPotentiallyRetriable());
        assertFalse(type.isFailure());
        assertTrue(type.isSuccess());
    }

    @Test
    void testDeclinedProperties() {
        GatewayResponseType type = GatewayResponseType.DECLINED;
        assertEquals(2, type.getCode());
        assertEquals("declined", type.getName());
        assertFalse(type.isPotentiallyRetriable());
        assertTrue(type.isFailure());
        assertFalse(type.isSuccess());
    }

    @Test
    void testErrorProperties() {
        GatewayResponseType type = GatewayResponseType.ERROR;
        assertEquals(3, type.getCode());
        assertEquals("error", type.getName());
        assertTrue(type.isPotentiallyRetriable());
        assertTrue(type.isFailure());
        assertFalse(type.isSuccess());
    }

    @Test
    void testHeldForReviewProperties() {
        GatewayResponseType type = GatewayResponseType.HELD_FOR_REVIEW;
        assertEquals(4, type.getCode());
        assertEquals("held_for_review", type.getName());
        assertFalse(type.isPotentiallyRetriable());
        assertFalse(type.isFailure());
        assertFalse(type.isSuccess());
    }

    @Test
    void testUnknownProperties() {
        GatewayResponseType type = GatewayResponseType.UNKNOWN;
        assertEquals(0, type.getCode());
        assertEquals("unknown", type.getName());
        assertFalse(type.isPotentiallyRetriable());
        assertTrue(type.isFailure());
        assertFalse(type.isSuccess());
    }

    @Test
    void testFromCodeInt() {
        assertEquals(GatewayResponseType.APPROVED, GatewayResponseType.fromCode(1));
        assertEquals(GatewayResponseType.DECLINED, GatewayResponseType.fromCode(2));
        assertEquals(GatewayResponseType.ERROR, GatewayResponseType.fromCode(3));
        assertEquals(GatewayResponseType.HELD_FOR_REVIEW, GatewayResponseType.fromCode(4));
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode(0));
    }

    @Test
    void testFromCodeIntUnknown() {
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode(5));
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode(-1));
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode(100));
    }

    @Test
    void testFromCodeString() {
        assertEquals(GatewayResponseType.APPROVED, GatewayResponseType.fromCode("1"));
        assertEquals(GatewayResponseType.DECLINED, GatewayResponseType.fromCode("2"));
        assertEquals(GatewayResponseType.ERROR, GatewayResponseType.fromCode("3"));
        assertEquals(GatewayResponseType.HELD_FOR_REVIEW, GatewayResponseType.fromCode("4"));
    }

    @Test
    void testFromCodeStringNull() {
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode((String) null));
    }

    @Test
    void testFromCodeStringBlank() {
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode(""));
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode("   "));
    }

    @Test
    void testFromCodeStringInvalid() {
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode("abc"));
        assertEquals(GatewayResponseType.UNKNOWN, GatewayResponseType.fromCode("not_a_number"));
    }

    @Test
    void testFromCodeStringWithWhitespace() {
        assertEquals(GatewayResponseType.APPROVED, GatewayResponseType.fromCode(" 1 "));
        assertEquals(GatewayResponseType.DECLINED, GatewayResponseType.fromCode("  2  "));
    }

    @Test
    void testToPaymentStateCapture() {
        assertEquals(PaymentState.CAPTURED, GatewayResponseType.APPROVED.toPaymentState(true));
        assertEquals(PaymentState.DECLINED, GatewayResponseType.DECLINED.toPaymentState(true));
        assertEquals(PaymentState.ERROR, GatewayResponseType.ERROR.toPaymentState(true));
        assertEquals(PaymentState.HELD_FOR_REVIEW, GatewayResponseType.HELD_FOR_REVIEW.toPaymentState(true));
        assertEquals(PaymentState.ERROR, GatewayResponseType.UNKNOWN.toPaymentState(true));
    }

    @Test
    void testToPaymentStateAuthOnly() {
        assertEquals(PaymentState.AUTHORIZED, GatewayResponseType.APPROVED.toPaymentState(false));
        assertEquals(PaymentState.DECLINED, GatewayResponseType.DECLINED.toPaymentState(false));
        assertEquals(PaymentState.ERROR, GatewayResponseType.ERROR.toPaymentState(false));
        assertEquals(PaymentState.HELD_FOR_REVIEW, GatewayResponseType.HELD_FOR_REVIEW.toPaymentState(false));
        assertEquals(PaymentState.ERROR, GatewayResponseType.UNKNOWN.toPaymentState(false));
    }

    @ParameterizedTest
    @EnumSource(GatewayResponseType.class)
    void testAllTypesHaveValidName(GatewayResponseType type) {
        assertNotNull(type.getName());
        assertFalse(type.getName().isBlank());
    }
}

