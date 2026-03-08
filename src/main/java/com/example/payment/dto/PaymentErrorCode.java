package com.example.payment.dto;

/**
 * Enumeration of payment error codes for structured error responses.
 *
 * <p>These codes provide machine-readable error identification for clients
 * to handle different error scenarios programmatically.</p>
 */
public enum PaymentErrorCode {

    // Validation Errors (400)
    INVALID_CARD_NUMBER("INVALID_CARD_NUMBER", "The card number is invalid", 400, false),
    INVALID_EXPIRY("INVALID_EXPIRY", "The card expiration date is invalid", 400, false),
    INVALID_CVV("INVALID_CVV", "The CVV is invalid", 400, false),
    INVALID_AMOUNT("INVALID_AMOUNT", "The amount is invalid", 400, false),
    MISSING_REQUIRED_FIELD("MISSING_REQUIRED_FIELD", "A required field is missing", 400, false),
    INVALID_REQUEST("INVALID_REQUEST", "The request is invalid", 400, false),

    // State Errors (400/409)
    INVALID_STATE_TRANSITION("INVALID_STATE_TRANSITION", "The requested operation is not allowed in the current state", 409, false),
    ALREADY_CAPTURED("ALREADY_CAPTURED", "The transaction has already been captured", 409, false),
    ALREADY_VOIDED("ALREADY_VOIDED", "The transaction has already been voided", 409, false),
    ALREADY_REFUNDED("ALREADY_REFUNDED", "The transaction has already been refunded", 409, false),
    NOT_CAPTURABLE("NOT_CAPTURABLE", "The transaction cannot be captured", 409, false),
    NOT_REFUNDABLE("NOT_REFUNDABLE", "The transaction cannot be refunded", 409, false),

    // Decline Errors (400)
    CARD_DECLINED("CARD_DECLINED", "The card was declined", 400, false),
    INSUFFICIENT_FUNDS("INSUFFICIENT_FUNDS", "Insufficient funds", 400, false),
    CARD_EXPIRED("CARD_EXPIRED", "The card has expired", 400, false),
    INVALID_CARD("INVALID_CARD", "The card is invalid", 400, false),
    FRAUD_SUSPECTED("FRAUD_SUSPECTED", "Transaction flagged for fraud", 400, false),

    // Gateway Errors (502/503)
    GATEWAY_TIMEOUT("GATEWAY_TIMEOUT", "The payment gateway timed out", 504, true),
    GATEWAY_UNAVAILABLE("GATEWAY_UNAVAILABLE", "The payment gateway is unavailable", 503, true),
    GATEWAY_COMMUNICATION_ERROR("GATEWAY_COMMUNICATION_ERROR", "Error communicating with payment gateway", 502, true),
    GATEWAY_INVALID_RESPONSE("GATEWAY_INVALID_RESPONSE", "Invalid response from payment gateway", 502, false),
    GATEWAY_ERROR("GATEWAY_ERROR", "Payment gateway error", 502, true),

    // Resource Errors (404)
    TRANSACTION_NOT_FOUND("TRANSACTION_NOT_FOUND", "Transaction not found", 404, false),
    ORDER_NOT_FOUND("ORDER_NOT_FOUND", "Order not found", 404, false),

    // Idempotency Errors (409)
    IDEMPOTENCY_CONFLICT("IDEMPOTENCY_CONFLICT", "Request conflicts with a previous request", 409, false),

    // Rate Limiting (429)
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "Too many requests", 429, true),

    // Internal Errors (500)
    DATABASE_ERROR("DATABASE_ERROR", "Database error occurred", 500, true),
    INTERNAL_ERROR("INTERNAL_ERROR", "An internal error occurred", 500, false),
    UNEXPECTED_ERROR("UNEXPECTED_ERROR", "An unexpected error occurred", 500, false);

    private final String code;
    private final String defaultMessage;
    private final int httpStatus;
    private final boolean retryable;

    PaymentErrorCode(String code, String defaultMessage, int httpStatus, boolean retryable) {
        this.code = code;
        this.defaultMessage = defaultMessage;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /**
     * Get the error category based on HTTP status code.
     */
    public String getCategory() {
        if (httpStatus >= 400 && httpStatus < 500) {
            if (code.startsWith("INVALID_STATE") || code.startsWith("ALREADY_") ||
                code.equals("NOT_CAPTURABLE") || code.equals("NOT_REFUNDABLE") ||
                code.equals("IDEMPOTENCY_CONFLICT")) {
                return "STATE_ERROR";
            }
            if (code.contains("DECLINED") || code.contains("FUNDS") ||
                code.contains("EXPIRED") || code.contains("FRAUD")) {
                return "DECLINE_ERROR";
            }
            if (code.contains("NOT_FOUND")) {
                return "NOT_FOUND";
            }
            return "VALIDATION_ERROR";
        }
        if (httpStatus >= 500 && httpStatus < 600) {
            if (code.startsWith("GATEWAY_")) {
                return "GATEWAY_ERROR";
            }
            return "INTERNAL_ERROR";
        }
        return "UNKNOWN";
    }
}

