package com.example.payment.exception;

/**
 * Exception for transient payment failures that may be retriable.
 *
 * <p>This exception should be thrown for errors that are potentially
 * temporary and may succeed on retry, such as:</p>
 * <ul>
 *   <li>Network timeouts</li>
 *   <li>Gateway temporarily unavailable</li>
 *   <li>Communication errors</li>
 * </ul>
 */
public class TransientPaymentException extends PaymentException {

    private final int suggestedRetryDelayMs;

    public TransientPaymentException(String message) {
        super(message);
        this.suggestedRetryDelayMs = 1000; // Default 1 second
    }

    public TransientPaymentException(String message, Throwable cause) {
        super(message, cause);
        this.suggestedRetryDelayMs = 1000;
    }

    public TransientPaymentException(String message, int suggestedRetryDelayMs) {
        super(message);
        this.suggestedRetryDelayMs = suggestedRetryDelayMs;
    }

    public TransientPaymentException(String message, Throwable cause, int suggestedRetryDelayMs) {
        super(message, cause);
        this.suggestedRetryDelayMs = suggestedRetryDelayMs;
    }

    @Override
    public String getErrorCode() {
        return "TRANSIENT_ERROR";
    }

    @Override
    public boolean isRetryable() {
        return true;
    }

    /**
     * @return Suggested delay before retry in milliseconds
     */
    public int getSuggestedRetryDelayMs() {
        return suggestedRetryDelayMs;
    }
}

