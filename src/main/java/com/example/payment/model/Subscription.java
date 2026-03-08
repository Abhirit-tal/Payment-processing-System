package com.example.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;

/**
 * JPA entity representing a recurring billing subscription.
 *
 * <p>Maps to the Authorize.Net ARB (Automated Recurring Billing) subscription.
 * Stores local state and the gateway subscription ID for lifecycle management.</p>
 */
@Entity
@Table(name = "subscriptions", indexes = {
    @Index(name = "idx_subscription_gateway_id", columnList = "gateway_subscription_id"),
    @Index(name = "idx_subscription_status", columnList = "status")
})
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "gateway_subscription_id", unique = true)
    private String gatewaySubscriptionId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "interval_length", nullable = false)
    private int intervalLength;

    @Column(name = "interval_unit", nullable = false, length = 20)
    private String intervalUnit; // "days" or "months"

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(nullable = false, length = 30)
    private String status = "active"; // active, suspended, cancelled, expired

    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "total_billed", nullable = false)
    private int totalBilled = 0;

    @Column(name = "last_billed_at")
    private Instant lastBilledAt;

    @Column(name = "billing_failures", nullable = false)
    private int billingFailures = 0;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getGatewaySubscriptionId() { return gatewaySubscriptionId; }
    public void setGatewaySubscriptionId(String gatewaySubscriptionId) {
        this.gatewaySubscriptionId = gatewaySubscriptionId;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public int getIntervalLength() { return intervalLength; }
    public void setIntervalLength(int intervalLength) { this.intervalLength = intervalLength; }

    public String getIntervalUnit() { return intervalUnit; }
    public void setIntervalUnit(String intervalUnit) { this.intervalUnit = intervalUnit; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCardLast4() { return cardLast4; }
    public void setCardLast4(String cardLast4) { this.cardLast4 = cardLast4; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }

    public LocalDate getNextBillingDate() { return nextBillingDate; }
    public void setNextBillingDate(LocalDate nextBillingDate) { this.nextBillingDate = nextBillingDate; }

    public int getTotalBilled() { return totalBilled; }
    public void setTotalBilled(int totalBilled) { this.totalBilled = totalBilled; }

    public Instant getLastBilledAt() { return lastBilledAt; }
    public void setLastBilledAt(Instant lastBilledAt) { this.lastBilledAt = lastBilledAt; }

    public int getBillingFailures() { return billingFailures; }
    public void setBillingFailures(int billingFailures) { this.billingFailures = billingFailures; }
}

