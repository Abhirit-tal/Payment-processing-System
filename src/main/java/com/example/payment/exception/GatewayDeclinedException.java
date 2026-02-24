package com.example.payment.exception;

/**
 * Exception thrown when a payment is declined by the gateway or issuing bank.
 *
 * <p>Decline responses are permanent failures for the specific card/transaction
 * and should not be retried with the same payment method.</p>
 */
public class GatewayDeclinedException extends PermanentPaymentException {

    private final String declineCode;
    private final String avsResult;
    private final String cvvResult;

    public GatewayDeclinedException(String message, String declineCode) {
        super(message, "CARD_DECLINED");
        this.declineCode = declineCode;
        this.avsResult = null;
        this.cvvResult = null;
    }

    public GatewayDeclinedException(String message, String declineCode,
                                   String avsResult, String cvvResult) {
        super(message, "CARD_DECLINED");
        this.declineCode = declineCode;
        this.avsResult = avsResult;
        this.cvvResult = cvvResult;
    }

    /**
     * @return The decline reason code from the gateway
     */
    public String getDeclineCode() {
        return declineCode;
    }

    /**
     * @return AVS (Address Verification System) result code
     */
    public String getAvsResult() {
        return avsResult;
    }

    /**
     * @return CVV verification result code
     */
    public String getCvvResult() {
        return cvvResult;
    }
}

