package com.example.payment.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentState enum - testing all state methods and transitions.
 */
public class PaymentStateTest {

    @Test
    void testAllStatesHaveCode() {
        for (PaymentState state : PaymentState.values()) {
            assertNotNull(state.getCode());
            assertFalse(state.getCode().isBlank());
        }
    }

    @Test
    void testTerminalStates() {
        assertTrue(PaymentState.VOIDED.isTerminal());
        assertTrue(PaymentState.REFUNDED.isTerminal());
        assertTrue(PaymentState.DECLINED.isTerminal());

        assertFalse(PaymentState.CREATED.isTerminal());
        assertFalse(PaymentState.PENDING.isTerminal());
        assertFalse(PaymentState.AUTHORIZED.isTerminal());
        assertFalse(PaymentState.CAPTURED.isTerminal());
        assertFalse(PaymentState.PARTIALLY_REFUNDED.isTerminal());
        assertFalse(PaymentState.ERROR.isTerminal());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isTerminal());
    }

    @Test
    void testFailureStates() {
        assertTrue(PaymentState.DECLINED.isFailure());
        assertTrue(PaymentState.ERROR.isFailure());

        assertFalse(PaymentState.CREATED.isFailure());
        assertFalse(PaymentState.PENDING.isFailure());
        assertFalse(PaymentState.AUTHORIZED.isFailure());
        assertFalse(PaymentState.CAPTURED.isFailure());
        assertFalse(PaymentState.VOIDED.isFailure());
        assertFalse(PaymentState.REFUNDED.isFailure());
        assertFalse(PaymentState.PARTIALLY_REFUNDED.isFailure());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isFailure());
    }

    @Test
    void testSuccessfulCaptureStates() {
        assertTrue(PaymentState.CAPTURED.isSuccessfulCapture());
        assertTrue(PaymentState.PARTIALLY_REFUNDED.isSuccessfulCapture());

        assertFalse(PaymentState.CREATED.isSuccessfulCapture());
        assertFalse(PaymentState.PENDING.isSuccessfulCapture());
        assertFalse(PaymentState.AUTHORIZED.isSuccessfulCapture());
        assertFalse(PaymentState.VOIDED.isSuccessfulCapture());
        assertFalse(PaymentState.REFUNDED.isSuccessfulCapture());
        assertFalse(PaymentState.DECLINED.isSuccessfulCapture());
        assertFalse(PaymentState.ERROR.isSuccessfulCapture());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isSuccessfulCapture());
    }

    @Test
    void testRefundableStates() {
        assertTrue(PaymentState.CAPTURED.isRefundable());
        assertTrue(PaymentState.PARTIALLY_REFUNDED.isRefundable());

        assertFalse(PaymentState.CREATED.isRefundable());
        assertFalse(PaymentState.PENDING.isRefundable());
        assertFalse(PaymentState.AUTHORIZED.isRefundable());
        assertFalse(PaymentState.VOIDED.isRefundable());
        assertFalse(PaymentState.REFUNDED.isRefundable());
        assertFalse(PaymentState.DECLINED.isRefundable());
        assertFalse(PaymentState.ERROR.isRefundable());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isRefundable());
    }

    @Test
    void testVoidableStates() {
        assertTrue(PaymentState.AUTHORIZED.isVoidable());

        assertFalse(PaymentState.CREATED.isVoidable());
        assertFalse(PaymentState.PENDING.isVoidable());
        assertFalse(PaymentState.CAPTURED.isVoidable());
        assertFalse(PaymentState.VOIDED.isVoidable());
        assertFalse(PaymentState.REFUNDED.isVoidable());
        assertFalse(PaymentState.PARTIALLY_REFUNDED.isVoidable());
        assertFalse(PaymentState.DECLINED.isVoidable());
        assertFalse(PaymentState.ERROR.isVoidable());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isVoidable());
    }

    @Test
    void testCapturableStates() {
        assertTrue(PaymentState.AUTHORIZED.isCapturable());

        assertFalse(PaymentState.CREATED.isCapturable());
        assertFalse(PaymentState.PENDING.isCapturable());
        assertFalse(PaymentState.CAPTURED.isCapturable());
        assertFalse(PaymentState.VOIDED.isCapturable());
        assertFalse(PaymentState.REFUNDED.isCapturable());
        assertFalse(PaymentState.PARTIALLY_REFUNDED.isCapturable());
        assertFalse(PaymentState.DECLINED.isCapturable());
        assertFalse(PaymentState.ERROR.isCapturable());
        assertFalse(PaymentState.HELD_FOR_REVIEW.isCapturable());
    }

    @Test
    void testFromCodeValidCodes() {
        assertEquals(PaymentState.CREATED, PaymentState.fromCode("created"));
        assertEquals(PaymentState.PENDING, PaymentState.fromCode("pending"));
        assertEquals(PaymentState.AUTHORIZED, PaymentState.fromCode("authorized"));
        assertEquals(PaymentState.CAPTURED, PaymentState.fromCode("captured"));
        assertEquals(PaymentState.VOIDED, PaymentState.fromCode("voided"));
        assertEquals(PaymentState.REFUNDED, PaymentState.fromCode("refunded"));
        assertEquals(PaymentState.PARTIALLY_REFUNDED, PaymentState.fromCode("partially_refunded"));
        assertEquals(PaymentState.DECLINED, PaymentState.fromCode("declined"));
        assertEquals(PaymentState.ERROR, PaymentState.fromCode("error"));
        assertEquals(PaymentState.HELD_FOR_REVIEW, PaymentState.fromCode("held_for_review"));
    }

    @Test
    void testFromCodeCaseInsensitive() {
        assertEquals(PaymentState.CREATED, PaymentState.fromCode("CREATED"));
        assertEquals(PaymentState.CAPTURED, PaymentState.fromCode("Captured"));
        assertEquals(PaymentState.AUTHORIZED, PaymentState.fromCode("AuThOrIzEd"));
    }

    @Test
    void testFromCodeNull() {
        assertThrows(IllegalArgumentException.class, () -> PaymentState.fromCode(null));
    }

    @Test
    void testFromCodeUnknown() {
        assertThrows(IllegalArgumentException.class, () -> PaymentState.fromCode("invalid"));
        assertThrows(IllegalArgumentException.class, () -> PaymentState.fromCode(""));
        assertThrows(IllegalArgumentException.class, () -> PaymentState.fromCode("unknown_state"));
    }

    @Test
    void testFromCodeOrNullValidCodes() {
        assertEquals(PaymentState.CREATED, PaymentState.fromCodeOrNull("created"));
        assertEquals(PaymentState.CAPTURED, PaymentState.fromCodeOrNull("captured"));
    }

    @Test
    void testFromCodeOrNullReturnsNull() {
        assertNull(PaymentState.fromCodeOrNull(null));
        assertNull(PaymentState.fromCodeOrNull("invalid"));
        assertNull(PaymentState.fromCodeOrNull("unknown"));
        assertNull(PaymentState.fromCodeOrNull(""));
    }

    @ParameterizedTest
    @EnumSource(PaymentState.class)
    void testAllStatesRoundTripFromCode(PaymentState state) {
        assertEquals(state, PaymentState.fromCode(state.getCode()));
        assertEquals(state, PaymentState.fromCodeOrNull(state.getCode()));
    }
}

