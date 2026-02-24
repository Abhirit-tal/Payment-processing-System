package com.example.payment.service;

import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for PaymentStateMachine.
 */
public class PaymentStateMachineExtendedTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    @Nested
    class CanTransitionTests {

        @Test
        void testCreatedToPending() {
            assertTrue(stateMachine.canTransition(PaymentState.CREATED, PaymentState.PENDING));
        }

        @Test
        void testPendingTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.AUTHORIZED));
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.CAPTURED));
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.DECLINED));
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.ERROR));
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.HELD_FOR_REVIEW));
        }

        @Test
        void testAuthorizedTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.CAPTURED));
            assertTrue(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.VOIDED));
            assertTrue(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.ERROR));
        }

        @Test
        void testCapturedTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.REFUNDED));
            assertTrue(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.PARTIALLY_REFUNDED));
        }

        @Test
        void testPartiallyRefundedTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.PARTIALLY_REFUNDED, PaymentState.REFUNDED));
            assertTrue(stateMachine.canTransition(PaymentState.PARTIALLY_REFUNDED, PaymentState.PARTIALLY_REFUNDED));
        }

        @Test
        void testErrorTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.ERROR, PaymentState.PENDING));
        }

        @Test
        void testHeldForReviewTransitions() {
            assertTrue(stateMachine.canTransition(PaymentState.HELD_FOR_REVIEW, PaymentState.AUTHORIZED));
            assertTrue(stateMachine.canTransition(PaymentState.HELD_FOR_REVIEW, PaymentState.DECLINED));
        }

        @Test
        void testTerminalStatesCannotTransition() {
            assertFalse(stateMachine.canTransition(PaymentState.DECLINED, PaymentState.PENDING));
            assertFalse(stateMachine.canTransition(PaymentState.VOIDED, PaymentState.PENDING));
            assertFalse(stateMachine.canTransition(PaymentState.REFUNDED, PaymentState.PENDING));
        }

        @Test
        void testNullFromState() {
            assertFalse(stateMachine.canTransition(null, PaymentState.PENDING));
        }

        @Test
        void testNullToState() {
            assertFalse(stateMachine.canTransition(PaymentState.CREATED, null));
        }

        @Test
        void testBothNullStates() {
            assertFalse(stateMachine.canTransition(null, null));
        }

        @Test
        void testInvalidTransitions() {
            assertFalse(stateMachine.canTransition(PaymentState.CREATED, PaymentState.CAPTURED));
            assertFalse(stateMachine.canTransition(PaymentState.CREATED, PaymentState.REFUNDED));
            assertFalse(stateMachine.canTransition(PaymentState.PENDING, PaymentState.REFUNDED));
            assertFalse(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.REFUNDED));
            assertFalse(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.AUTHORIZED));
        }
    }

    @Nested
    class ValidateTransitionTests {

        @Test
        void testValidTransitionDoesNotThrow() {
            assertDoesNotThrow(() ->
                stateMachine.validateTransition(PaymentState.CREATED, PaymentState.PENDING, 1L));
        }

        @Test
        void testInvalidTransitionThrows() {
            InvalidStateTransitionException ex = assertThrows(
                InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentState.CREATED, PaymentState.CAPTURED, 1L)
            );

            assertEquals(PaymentState.CREATED, ex.getFromState());
            assertEquals(PaymentState.CAPTURED, ex.getToState());
            assertEquals(1L, ex.getOrderId());
        }

        @Test
        void testNullFromStateThrows() {
            assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(null, PaymentState.PENDING, 1L));
        }

        @Test
        void testNullToStateThrows() {
            assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentState.CREATED, null, 1L));
        }
    }

    @Nested
    class GetAllowedTransitionsTests {

        @Test
        void testCreatedAllowedTransitions() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.CREATED);
            assertEquals(1, allowed.size());
            assertTrue(allowed.contains(PaymentState.PENDING));
        }

        @Test
        void testPendingAllowedTransitions() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.PENDING);
            assertEquals(5, allowed.size());
            assertTrue(allowed.contains(PaymentState.AUTHORIZED));
            assertTrue(allowed.contains(PaymentState.CAPTURED));
            assertTrue(allowed.contains(PaymentState.DECLINED));
            assertTrue(allowed.contains(PaymentState.ERROR));
            assertTrue(allowed.contains(PaymentState.HELD_FOR_REVIEW));
        }

        @Test
        void testTerminalStateHasNoTransitions() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.DECLINED);
            assertTrue(allowed.isEmpty());
        }

        @Test
        void testNullStateReturnsEmptySet() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(null);
            assertNotNull(allowed);
            assertTrue(allowed.isEmpty());
        }

        @Test
        void testReturnedSetIsUnmodifiable() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.CREATED);
            assertThrows(UnsupportedOperationException.class,
                () -> allowed.add(PaymentState.CAPTURED));
        }
    }

    @Nested
    class IsTerminalTests {

        @Test
        void testTerminalStates() {
            assertTrue(stateMachine.isTerminal(PaymentState.DECLINED));
            assertTrue(stateMachine.isTerminal(PaymentState.VOIDED));
            assertTrue(stateMachine.isTerminal(PaymentState.REFUNDED));
        }

        @Test
        void testNonTerminalStates() {
            assertFalse(stateMachine.isTerminal(PaymentState.CREATED));
            assertFalse(stateMachine.isTerminal(PaymentState.PENDING));
            assertFalse(stateMachine.isTerminal(PaymentState.AUTHORIZED));
            assertFalse(stateMachine.isTerminal(PaymentState.CAPTURED));
            assertFalse(stateMachine.isTerminal(PaymentState.ERROR));
        }

        @Test
        void testNullState() {
            assertFalse(stateMachine.isTerminal(null));
        }
    }

    @Nested
    class DetermineStateForTransactionTests {

        @Test
        void testSuccessfulPurchase() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("purchase", true, false));
        }

        @Test
        void testSuccessfulCapture() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("capture", true, false));
        }

        @Test
        void testSuccessfulAuthorize() {
            assertEquals(PaymentState.AUTHORIZED,
                stateMachine.determineStateForTransaction("authorize", true, false));
        }

        @Test
        void testSuccessfulVoid() {
            assertEquals(PaymentState.VOIDED,
                stateMachine.determineStateForTransaction("void", true, false));
        }

        @Test
        void testSuccessfulRefund() {
            assertEquals(PaymentState.REFUNDED,
                stateMachine.determineStateForTransaction("refund", true, false));
        }

        @Test
        void testSuccessfulUnknownType() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("unknown", true, false));
        }

        @Test
        void testFailedRetriable() {
            assertEquals(PaymentState.ERROR,
                stateMachine.determineStateForTransaction("purchase", false, true));
        }

        @Test
        void testFailedNonRetriable() {
            assertEquals(PaymentState.DECLINED,
                stateMachine.determineStateForTransaction("purchase", false, false));
        }

        @Test
        void testCaseInsensitive() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("PURCHASE", true, false));
            assertEquals(PaymentState.AUTHORIZED,
                stateMachine.determineStateForTransaction("AUTHORIZE", true, false));
        }
    }

    @Nested
    class GetTransitionErrorMessageTests {

        @Test
        void testNullFromState() {
            String msg = stateMachine.getTransitionErrorMessage(null, PaymentState.PENDING);
            assertEquals("Current state is null", msg);
        }

        @Test
        void testNullToState() {
            String msg = stateMachine.getTransitionErrorMessage(PaymentState.CREATED, null);
            assertEquals("Target state is null", msg);
        }

        @Test
        void testTerminalState() {
            String msg = stateMachine.getTransitionErrorMessage(PaymentState.DECLINED, PaymentState.PENDING);
            assertTrue(msg.contains("terminal state"));
            assertTrue(msg.contains("declined"));
        }

        @Test
        void testInvalidTransition() {
            String msg = stateMachine.getTransitionErrorMessage(PaymentState.CREATED, PaymentState.CAPTURED);
            assertTrue(msg.contains("not allowed"));
            assertTrue(msg.contains("created"));
            assertTrue(msg.contains("captured"));
            assertTrue(msg.contains("PENDING")); // The allowed transition
        }

        @Test
        void testStateWithNoTransitions() {
            String msg = stateMachine.getTransitionErrorMessage(PaymentState.VOIDED, PaymentState.PENDING);
            assertTrue(msg.contains("terminal state") || msg.contains("No transitions allowed"));
        }
    }
}

