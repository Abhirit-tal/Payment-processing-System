package com.example.payment.model;

/**
 * Enumeration of gateway response types from Authorize.Net.
 *
 * <p>These response codes are mapped from Authorize.Net's response codes to
 * provide a normalized view of transaction outcomes.</p>
 *
 * <h2>Authorize.Net Response Code Mapping:</h2>
 * <ul>
 *   <li>1 = Approved</li>
 *   <li>2 = Declined</li>
 *   <li>3 = Error</li>
 *   <li>4 = Held for Review</li>
 * </ul>
 *
 * @see <a href="https://developer.authorize.net/api/reference/responseCodes.html">Authorize.Net Response Codes</a>
 */
public enum GatewayResponseType {

    /**
     * Transaction approved by gateway and issuing bank.
     */
    APPROVED(1, "approved", false, false),

    /**
     * Transaction declined by gateway or issuing bank.
     * This is a permanent failure - do not retry with same card.
     */
    DECLINED(2, "declined", false, true),

    /**
     * Gateway error occurred during processing.
     * May be transient (network issue) or permanent (invalid data).
     */
    ERROR(3, "error", true, true),

    /**
     * Transaction held for review (fraud detection).
     * Requires manual approval or will auto-decline after timeout.
     */
    HELD_FOR_REVIEW(4, "held_for_review", false, false),

    /**
     * Unknown or unmapped response code.
     */
    UNKNOWN(0, "unknown", false, true);

    private final int code;
    private final String name;
    private final boolean potentiallyRetriable;
    private final boolean failure;

    GatewayResponseType(int code, String name, boolean potentiallyRetriable, boolean failure) {
        this.code = code;
        this.name = name;
        this.potentiallyRetriable = potentiallyRetriable;
        this.failure = failure;
    }

    /**
     * @return The numeric response code from Authorize.Net
     */
    public int getCode() {
        return code;
    }

    /**
     * @return The string name of this response type
     */
    public String getName() {
        return name;
    }

    /**
     * @return true if this response type might be retriable (e.g., network errors)
     */
    public boolean isPotentiallyRetriable() {
        return potentiallyRetriable;
    }

    /**
     * @return true if this response type represents a failure outcome
     */
    public boolean isFailure() {
        return failure;
    }

    /**
     * @return true if this represents a successful transaction
     */
    public boolean isSuccess() {
        return this == APPROVED;
    }

    /**
     * Convert the appropriate PaymentState based on this response type and transaction type.
     *
     * @param isCapture true if this was a capture/purchase transaction
     * @return The corresponding PaymentState
     */
    public PaymentState toPaymentState(boolean isCapture) {
        switch (this) {
            case APPROVED:
                return isCapture ? PaymentState.CAPTURED : PaymentState.AUTHORIZED;
            case DECLINED:
                return PaymentState.DECLINED;
            case ERROR:
                return PaymentState.ERROR;
            case HELD_FOR_REVIEW:
                return PaymentState.HELD_FOR_REVIEW;
            default:
                return PaymentState.ERROR;
        }
    }

    /**
     * Parse a GatewayResponseType from Authorize.Net's numeric response code.
     *
     * @param code The numeric response code (1-4)
     * @return The corresponding GatewayResponseType
     */
    public static GatewayResponseType fromCode(int code) {
        for (GatewayResponseType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return UNKNOWN;
    }

    /**
     * Parse a GatewayResponseType from a string response code.
     *
     * @param code The string response code
     * @return The corresponding GatewayResponseType
     */
    public static GatewayResponseType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        try {
            return fromCode(Integer.parseInt(code.trim()));
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }
}

