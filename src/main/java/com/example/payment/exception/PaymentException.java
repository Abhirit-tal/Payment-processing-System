package com.example.payment.exception;

/**
 * Base exception class for all payment-related exceptions.
 *
 * <p>This exception hierarchy provides structured error handling with:
 * <ul>
 *   <li>Error codes for client identification</li>
 *   <li>Retry indicators for resilience</li>
 *   <li>Provider error details when applicable</li>
 * </ul>
 * </p>
 *
 * <h2>Exception Hierarchy:</h2>
 * <pre>
 * PaymentException (base)
 * ├── InvalidStateTransitionException
 * ├── TransientPaymentException (retriable)
 * │   ├── GatewayTimeoutException
 * │   └── GatewayCommunicationException
 * ├── PermanentPaymentException (non-retriable)
 * │   ├── GatewayDeclinedException
 * │   ├── ValidationException
 * │   └── InvalidRequestException
 * └── IdempotencyConflictException
 * </pre>
 */
public abstract class PaymentException extends RuntimeException {

    private String providerErrorCode;
    private String providerErrorMessage;
    private String requestId;

    protected PaymentException(String message) {
        super(message);
    }

    protected PaymentException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @return A unique error code identifying this type of error
     */
    public abstract String getErrorCode();

    /**
     * @return true if this error is potentially retriable
     */
    public abstract boolean isRetryable();

    /**
     * @return The provider-specific error code, if available
     */
    public String getProviderErrorCode() {
        return providerErrorCode;
    }

    public void setProviderErrorCode(String providerErrorCode) {
        this.providerErrorCode = providerErrorCode;
    }

    /**
     * @return The provider-specific error message, if available
     */
    public String getProviderErrorMessage() {
        return providerErrorMessage;
    }

    public void setProviderErrorMessage(String providerErrorMessage) {
        this.providerErrorMessage = providerErrorMessage;
    }

    /**
     * @return The request ID for correlation
     */
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /**
     * Set provider error details in a fluent style.
     */
    public PaymentException withProviderError(String code, String message) {
        this.providerErrorCode = code;
        this.providerErrorMessage = message;
        return this;
    }

    /**
     * Set request ID in a fluent style.
     */
    public PaymentException withRequestId(String requestId) {
        this.requestId = requestId;
        return this;
    }
}

