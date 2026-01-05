package com.example.payment.dto;

import com.example.payment.validation.ValidCardExpiry;
import com.example.payment.validation.ValidCardNumber;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Payment request DTOs")
public class PaymentRequests {
    @ValidCardExpiry
    @Schema(description = "Card details (number, expiry and CVV)")
    public static class Card {
        @NotBlank
        @ValidCardNumber
        @Schema(description = "Card number (PAN)", example = "4111111111111111")
        private String number;

        @Min(1)
        @Max(12)
        @Schema(description = "Expiry month (1-12)", example = "12")
        private int expMonth;

        @Min(2023)
        @Schema(description = "Expiry year (YYYY)", example = "2030")
        private int expYear;

        @NotBlank
        @Pattern(regexp = "\\d{3,4}")
        @Schema(description = "Card verification value (3 or 4 digits)", example = "123")
        private String cvv;

        // getters and setters
        public String getNumber() { return number; }
        public void setNumber(String number) { this.number = number; }
        public int getExpMonth() { return expMonth; }
        public void setExpMonth(int expMonth) { this.expMonth = expMonth; }
        public int getExpYear() { return expYear; }
        public void setExpYear(int expYear) { this.expYear = expYear; }
        public String getCvv() { return cvv; }
        public void setCvv(String cvv) { this.cvv = cvv; }
    }

    @Schema(description = "Purchase (authorize + capture) request")
    public static class PurchaseRequest {
        @NotNull
        @DecimalMin("0.01")
        @Schema(description = "Amount to charge", example = "12.34")
        private BigDecimal amount;

        @NotBlank
        @Schema(description = "Currency code (ISO 4217)", example = "USD")
        private String currency = "USD";

        @NotNull
        @Valid
        @Schema(description = "Card details")
        private Card card;

        @Schema(description = "Optional external order id", example = "ext-100")
        private String orderId;

        // getters and setters
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public Card getCard() { return card; }
        public void setCard(Card card) { this.card = card; }
        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
    }

    public static class AuthorizeRequest extends PurchaseRequest {}

    public static class CaptureRequest {
        @NotBlank
        private String transactionId;

        private BigDecimal amount;

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    public static class RefundRequest {
        @NotBlank
        private String transactionId;

        private BigDecimal amount;

        @Size(min = 4, max = 4)
        private String last4;

        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public String getLast4() { return last4; }
        public void setLast4(String last4) { this.last4 = last4; }
    }

    public static class CancelRequest {
        @NotBlank
        private String transactionId;
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    }
}
