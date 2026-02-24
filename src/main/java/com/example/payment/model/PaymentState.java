package com.example.payment.model;

/**
 * Enumeration of all possible payment states in the system.
 *
 * <p>This state machine enforces valid transitions to ensure payment integrity.
 * Invalid state transitions will be rejected at runtime.</p>
 *
 * <h2>State Transition Diagram:</h2>
 * <pre>
 * CREATED → PENDING → AUTHORIZED → CAPTURED → REFUNDED
 *                  ↘            ↘
 *                   DECLINED    VOIDED
 *                  ↘
 *                   ERROR → (retry) → PENDING
 *                  ↘
 *                   HELD_FOR_REVIEW → AUTHORIZED/DECLINED
 * </pre>
 */
public enum PaymentState {

    /**
     * Initial state when an order is created but payment not yet initiated.
     */
    CREATED("created", false, false),

    /**
     * Payment request sent to gateway, awaiting response.
     */
    PENDING("pending", false, false),

    /**
     * Authorization approved, funds held but not yet captured.
     * Only applicable for two-step (authorize + capture) flows.
     */
    AUTHORIZED("authorized", false, false),

    /**
     * Payment successfully captured. Funds will be settled.
     */
    CAPTURED("captured", false, false),

    /**
     * Authorization voided before capture. No funds transferred.
     */
    VOIDED("voided", true, false),

    /**
     * Full refund processed. All captured funds returned.
     */
    REFUNDED("refunded", true, false),

    /**
     * Partial refund processed. Some captured funds returned.
     */
    PARTIALLY_REFUNDED("partially_refunded", false, false),

    /**
     * Payment declined by gateway or issuing bank.
     */
    DECLINED("declined", true, true),

    /**
     * Gateway error occurred. May be retriable depending on error type.
     */
    ERROR("error", false, true),

    /**
     * Transaction flagged for fraud review. Requires manual approval.
     */
    HELD_FOR_REVIEW("held_for_review", false, false);

    private final String code;
    private final boolean terminal;
    private final boolean failure;

    PaymentState(String code, boolean terminal, boolean failure) {
        this.code = code;
        this.terminal = terminal;
        this.failure = failure;
    }

    /**
     * @return The string code representation of this state (for API responses)
     */
    public String getCode() {
        return code;
    }

    /**
     * @return true if this is a terminal state (no further transitions allowed)
     */
    public boolean isTerminal() {
        return terminal;
    }

    /**
     * @return true if this state represents a failure outcome
     */
    public boolean isFailure() {
        return failure;
    }

    /**
     * @return true if this state represents a successful payment capture
     */
    public boolean isSuccessfulCapture() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }

    /**
     * @return true if a refund operation is allowed from this state
     */
    public boolean isRefundable() {
        return this == CAPTURED || this == PARTIALLY_REFUNDED;
    }

    /**
     * @return true if a void/cancel operation is allowed from this state
     */
    public boolean isVoidable() {
        return this == AUTHORIZED;
    }

    /**
     * @return true if a capture operation is allowed from this state
     */
    public boolean isCapturable() {
        return this == AUTHORIZED;
    }

    /**
     * Parse a state from its string code representation.
     *
     * @param code The string code (e.g., "authorized", "captured")
     * @return The corresponding PaymentState
     * @throws IllegalArgumentException if the code is not recognized
     */
    public static PaymentState fromCode(String code) {
        if (code == null) {
            throw new IllegalArgumentException("Payment state code cannot be null");
        }
        for (PaymentState state : values()) {
            if (state.code.equalsIgnoreCase(code)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown payment state code: " + code);
    }

    /**
     * Safely parse a state from string, returning null for unknown codes.
     *
     * @param code The string code
     * @return The corresponding PaymentState or null if not found
     */
    public static PaymentState fromCodeOrNull(String code) {
        if (code == null) {
            return null;
        }
        for (PaymentState state : values()) {
            if (state.code.equalsIgnoreCase(code)) {
                return state;
            }
        }
        return null;
    }
}

