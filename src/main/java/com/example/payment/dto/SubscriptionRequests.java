package com.example.payment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTOs for Subscription / Recurring Billing endpoints.
 */
public class SubscriptionRequests {

    @Schema(description = "Create a new recurring billing subscription")
    public static class CreateSubscriptionRequest {

        @NotBlank
        @Schema(description = "Subscription name", example = "Monthly Premium Plan")
        private String name;

        @NotNull
        @DecimalMin("0.01")
        @Schema(description = "Recurring amount", example = "29.99")
        private BigDecimal amount;

        @Min(1)
        @Max(365)
        @Schema(description = "Billing interval length", example = "1")
        private int intervalLength = 1;

        @NotBlank
        @Pattern(regexp = "days|months", message = "Must be 'days' or 'months'")
        @Schema(description = "Billing interval unit", example = "months")
        private String intervalUnit = "months";

        @NotBlank
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "Date format must be YYYY-MM-DD")
        @Schema(description = "Subscription start date (YYYY-MM-DD)", example = "2026-04-01")
        private String startDate;

        @NotNull
        @Valid
        @Schema(description = "Card details for recurring billing")
        private PaymentRequests.Card card;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public int getIntervalLength() { return intervalLength; }
        public void setIntervalLength(int intervalLength) { this.intervalLength = intervalLength; }
        public String getIntervalUnit() { return intervalUnit; }
        public void setIntervalUnit(String intervalUnit) { this.intervalUnit = intervalUnit; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public PaymentRequests.Card getCard() { return card; }
        public void setCard(PaymentRequests.Card card) { this.card = card; }
    }

    @Schema(description = "Update an existing subscription")
    public static class UpdateSubscriptionRequest {

        @Schema(description = "Updated subscription name", example = "Annual Premium Plan")
        private String name;

        @DecimalMin("0.01")
        @Schema(description = "Updated recurring amount", example = "49.99")
        private BigDecimal amount;

        // Getters and setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }
}

