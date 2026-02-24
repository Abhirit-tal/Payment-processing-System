package com.example.payment.exception;

import com.example.payment.model.PaymentState;

/**
 * Exception thrown when an invalid payment state transition is attempted.
 *
 * <p>This exception is thrown by the PaymentStateMachine when code attempts
 * to move a payment from one state to another that is not allowed by the
 * state transition rules.</p>
 */
public class InvalidStateTransitionException extends PaymentException {

    private final PaymentState fromState;
    private final PaymentState toState;
    private final Long orderId;

    public InvalidStateTransitionException(PaymentState fromState, PaymentState toState, Long orderId) {
        super(String.format("Invalid state transition from %s to %s for order %d",
                fromState != null ? fromState.getCode() : "null",
                toState != null ? toState.getCode() : "null",
                orderId));
        this.fromState = fromState;
        this.toState = toState;
        this.orderId = orderId;
    }

    public InvalidStateTransitionException(PaymentState fromState, PaymentState toState,
                                          Long orderId, String message) {
        super(message);
        this.fromState = fromState;
        this.toState = toState;
        this.orderId = orderId;
    }

    public PaymentState getFromState() {
        return fromState;
    }

    public PaymentState getToState() {
        return toState;
    }

    public Long getOrderId() {
        return orderId;
    }

    @Override
    public String getErrorCode() {
        return "INVALID_STATE_TRANSITION";
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}

