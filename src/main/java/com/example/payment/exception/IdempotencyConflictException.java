package com.example.payment.exception;

/**
 * Exception thrown when an idempotency key conflict is detected.
 */
public class IdempotencyConflictException extends PaymentException {

    public IdempotencyConflictException(String message) {
        super(message);
    }

    @Override
    public String getErrorCode() {
        return "IDEMPOTENCY_CONFLICT";
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}

