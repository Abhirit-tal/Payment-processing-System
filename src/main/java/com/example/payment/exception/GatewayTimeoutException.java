package com.example.payment.exception;

/**
 * Exception thrown when a gateway timeout occurs.
 *
 * <p>Timeouts are transient failures that may be retriable.</p>
 */
public class GatewayTimeoutException extends TransientPaymentException {

    public GatewayTimeoutException(String message) {
        super(message, 2000); // Suggest 2 second delay for timeouts
    }

    public GatewayTimeoutException(String message, Throwable cause) {
        super(message, cause, 2000);
    }

    @Override
    public String getErrorCode() {
        return "GATEWAY_TIMEOUT";
    }
}

