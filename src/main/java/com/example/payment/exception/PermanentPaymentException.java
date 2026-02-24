package com.example.payment.exception;

/**
 * Exception for permanent payment failures that should not be retried.
 *
 * <p>This exception should be thrown for errors that will not succeed
 * on retry with the same parameters, such as:</p>
 * <ul>
 *   <li>Validation errors</li>
 *   <li>Invalid card data</li>
 *   <li>Business rule violations</li>
 * </ul>
 */
public class PermanentPaymentException extends PaymentException {

    private final String errorCode;

    public PermanentPaymentException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public PermanentPaymentException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public boolean isRetryable() {
        return false;
    }
}

