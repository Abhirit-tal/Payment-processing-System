package com.example.payment.service;

import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentStateMachine.
 *
 * Tests cover:
 * - Valid state transitions
 * - Invalid state transition attempts
 * - Terminal state behavior
 * - State transition matrix completeness
 */
class PaymentStateMachineTest {

    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new PaymentStateMachine();
    }

    @Nested
    @DisplayName("Valid State Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("CREATED -> PENDING should be valid")
        void createdToPending() {
            assertTrue(stateMachine.canTransition(PaymentState.CREATED, PaymentState.PENDING));
        }

        @Test
        @DisplayName("PENDING -> AUTHORIZED should be valid")
        void pendingToAuthorized() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.AUTHORIZED));
        }

        @Test
        @DisplayName("PENDING -> CAPTURED should be valid (purchase flow)")
        void pendingToCaptured() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.CAPTURED));
        }

        @Test
        @DisplayName("PENDING -> DECLINED should be valid")
        void pendingToDeclined() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.DECLINED));
        }

        @Test
        @DisplayName("PENDING -> ERROR should be valid")
        void pendingToError() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.ERROR));
        }

        @Test
        @DisplayName("PENDING -> HELD_FOR_REVIEW should be valid")
        void pendingToHeldForReview() {
            assertTrue(stateMachine.canTransition(PaymentState.PENDING, PaymentState.HELD_FOR_REVIEW));
        }

        @Test
        @DisplayName("AUTHORIZED -> CAPTURED should be valid")
        void authorizedToCaptured() {
            assertTrue(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.CAPTURED));
        }

        @Test
        @DisplayName("AUTHORIZED -> VOIDED should be valid")
        void authorizedToVoided() {
            assertTrue(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.VOIDED));
        }

        @Test
        @DisplayName("CAPTURED -> REFUNDED should be valid")
        void capturedToRefunded() {
            assertTrue(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.REFUNDED));
        }

        @Test
        @DisplayName("CAPTURED -> PARTIALLY_REFUNDED should be valid")
        void capturedToPartiallyRefunded() {
            assertTrue(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.PARTIALLY_REFUNDED));
        }

        @Test
        @DisplayName("PARTIALLY_REFUNDED -> REFUNDED should be valid")
        void partiallyRefundedToRefunded() {
            assertTrue(stateMachine.canTransition(PaymentState.PARTIALLY_REFUNDED, PaymentState.REFUNDED));
        }

        @Test
        @DisplayName("ERROR -> PENDING should be valid (retry)")
        void errorToPending() {
            assertTrue(stateMachine.canTransition(PaymentState.ERROR, PaymentState.PENDING));
        }

        @Test
        @DisplayName("HELD_FOR_REVIEW -> AUTHORIZED should be valid")
        void heldForReviewToAuthorized() {
            assertTrue(stateMachine.canTransition(PaymentState.HELD_FOR_REVIEW, PaymentState.AUTHORIZED));
        }

        @Test
        @DisplayName("HELD_FOR_REVIEW -> DECLINED should be valid")
        void heldForReviewToDeclined() {
            assertTrue(stateMachine.canTransition(PaymentState.HELD_FOR_REVIEW, PaymentState.DECLINED));
        }
    }

    @Nested
    @DisplayName("Invalid State Transitions")
    class InvalidTransitions {

        @Test
        @DisplayName("CREATED -> CAPTURED should be invalid (must go through PENDING)")
        void createdToCaptured() {
            assertFalse(stateMachine.canTransition(PaymentState.CREATED, PaymentState.CAPTURED));
        }

        @Test
        @DisplayName("CREATED -> AUTHORIZED should be invalid")
        void createdToAuthorized() {
            assertFalse(stateMachine.canTransition(PaymentState.CREATED, PaymentState.AUTHORIZED));
        }

        @Test
        @DisplayName("AUTHORIZED -> REFUNDED should be invalid (must capture first)")
        void authorizedToRefunded() {
            assertFalse(stateMachine.canTransition(PaymentState.AUTHORIZED, PaymentState.REFUNDED));
        }

        @Test
        @DisplayName("CAPTURED -> AUTHORIZED should be invalid (can't go backward)")
        void capturedToAuthorized() {
            assertFalse(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.AUTHORIZED));
        }

        @Test
        @DisplayName("CAPTURED -> VOIDED should be invalid (can only void auth)")
        void capturedToVoided() {
            assertFalse(stateMachine.canTransition(PaymentState.CAPTURED, PaymentState.VOIDED));
        }

        @Test
        @DisplayName("PENDING -> VOIDED should be invalid")
        void pendingToVoided() {
            assertFalse(stateMachine.canTransition(PaymentState.PENDING, PaymentState.VOIDED));
        }
    }

    @Nested
    @DisplayName("Terminal States")
    class TerminalStates {

        @Test
        @DisplayName("DECLINED is terminal - no outgoing transitions")
        void declinedIsTerminal() {
            assertTrue(stateMachine.isTerminal(PaymentState.DECLINED));
            assertTrue(stateMachine.getAllowedTransitions(PaymentState.DECLINED).isEmpty());
        }

        @Test
        @DisplayName("VOIDED is terminal - no outgoing transitions")
        void voidedIsTerminal() {
            assertTrue(stateMachine.isTerminal(PaymentState.VOIDED));
            assertTrue(stateMachine.getAllowedTransitions(PaymentState.VOIDED).isEmpty());
        }

        @Test
        @DisplayName("REFUNDED is terminal - no outgoing transitions")
        void refundedIsTerminal() {
            assertTrue(stateMachine.isTerminal(PaymentState.REFUNDED));
            assertTrue(stateMachine.getAllowedTransitions(PaymentState.REFUNDED).isEmpty());
        }

        @Test
        @DisplayName("CAPTURED is not terminal - can be refunded")
        void capturedIsNotTerminal() {
            assertFalse(stateMachine.isTerminal(PaymentState.CAPTURED));
            assertFalse(stateMachine.getAllowedTransitions(PaymentState.CAPTURED).isEmpty());
        }
    }

    @Nested
    @DisplayName("Validate Transition with Exception")
    class ValidateTransitionException {

        @Test
        @DisplayName("Valid transition should not throw")
        void validTransitionNoException() {
            assertDoesNotThrow(() ->
                stateMachine.validateTransition(PaymentState.CREATED, PaymentState.PENDING, 1L));
        }

        @Test
        @DisplayName("Invalid transition should throw InvalidStateTransitionException")
        void invalidTransitionThrows() {
            InvalidStateTransitionException ex = assertThrows(
                InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentState.CREATED, PaymentState.CAPTURED, 1L)
            );

            assertEquals(PaymentState.CREATED, ex.getFromState());
            assertEquals(PaymentState.CAPTURED, ex.getToState());
            assertEquals(1L, ex.getOrderId());
        }

        @Test
        @DisplayName("Null from state should throw")
        void nullFromStateThrows() {
            assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(null, PaymentState.PENDING, 1L));
        }

        @Test
        @DisplayName("Null to state should throw")
        void nullToStateThrows() {
            assertThrows(InvalidStateTransitionException.class,
                () -> stateMachine.validateTransition(PaymentState.CREATED, null, 1L));
        }
    }

    @Nested
    @DisplayName("Get Allowed Transitions")
    class GetAllowedTransitions {

        @Test
        @DisplayName("PENDING should have multiple allowed transitions")
        void pendingHasMultipleTransitions() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.PENDING);

            assertEquals(5, allowed.size());
            assertTrue(allowed.contains(PaymentState.AUTHORIZED));
            assertTrue(allowed.contains(PaymentState.CAPTURED));
            assertTrue(allowed.contains(PaymentState.DECLINED));
            assertTrue(allowed.contains(PaymentState.ERROR));
            assertTrue(allowed.contains(PaymentState.HELD_FOR_REVIEW));
        }

        @Test
        @DisplayName("CREATED should only allow PENDING")
        void createdOnlyAllowsPending() {
            Set<PaymentState> allowed = stateMachine.getAllowedTransitions(PaymentState.CREATED);

            assertEquals(1, allowed.size());
            assertTrue(allowed.contains(PaymentState.PENDING));
        }

        @Test
        @DisplayName("Null state returns empty set")
        void nullStateReturnsEmpty() {
            assertTrue(stateMachine.getAllowedTransitions(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("Determine State for Transaction")
    class DetermineState {

        @Test
        @DisplayName("Successful purchase returns CAPTURED")
        void successfulPurchase() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("purchase", true, false));
        }

        @Test
        @DisplayName("Successful authorize returns AUTHORIZED")
        void successfulAuthorize() {
            assertEquals(PaymentState.AUTHORIZED,
                stateMachine.determineStateForTransaction("authorize", true, false));
        }

        @Test
        @DisplayName("Successful capture returns CAPTURED")
        void successfulCapture() {
            assertEquals(PaymentState.CAPTURED,
                stateMachine.determineStateForTransaction("capture", true, false));
        }

        @Test
        @DisplayName("Successful void returns VOIDED")
        void successfulVoid() {
            assertEquals(PaymentState.VOIDED,
                stateMachine.determineStateForTransaction("void", true, false));
        }

        @Test
        @DisplayName("Failed with retriable error returns ERROR")
        void failedRetriable() {
            assertEquals(PaymentState.ERROR,
                stateMachine.determineStateForTransaction("purchase", false, true));
        }

        @Test
        @DisplayName("Failed non-retriable returns DECLINED")
        void failedNonRetriable() {
            assertEquals(PaymentState.DECLINED,
                stateMachine.determineStateForTransaction("purchase", false, false));
        }
    }

    @Nested
    @DisplayName("Error Messages")
    class ErrorMessages {

        @Test
        @DisplayName("Terminal state error message")
        void terminalStateMessage() {
            String message = stateMachine.getTransitionErrorMessage(PaymentState.DECLINED, PaymentState.CAPTURED);
            assertTrue(message.contains("terminal state"));
        }

        @Test
        @DisplayName("Invalid transition error message includes allowed states")
        void invalidTransitionMessage() {
            String message = stateMachine.getTransitionErrorMessage(PaymentState.CREATED, PaymentState.CAPTURED);
            assertTrue(message.contains("not allowed"));
            assertTrue(message.contains("PENDING") || message.contains("pending"));
        }
    }
}

