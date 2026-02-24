package com.example.payment.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true)
    private String externalId;

    @Column(nullable = false)
    private String currency = "USD";

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentState state = PaymentState.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_state", length = 30)
    private PaymentState previousState;

    @Column(name = "state_changed_at")
    private Instant stateChangedAt;

    /**
     * @deprecated Use {@link #state} instead. Kept for backward compatibility.
     */
    @Deprecated
    @Column(nullable = false)
    private String status; // Legacy field - use state instead

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    // getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public PaymentState getState() {
        return state;
    }

    public void setState(PaymentState state) {
        this.state = state;
    }

    public PaymentState getPreviousState() {
        return previousState;
    }

    public void setPreviousState(PaymentState previousState) {
        this.previousState = previousState;
    }

    public Instant getStateChangedAt() {
        return stateChangedAt;
    }

    public void setStateChangedAt(Instant stateChangedAt) {
        this.stateChangedAt = stateChangedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Transition the order to a new state, recording the previous state.
     * This method should be called through PaymentStateMachine for validation.
     */
    public void transitionTo(PaymentState newState) {
        this.previousState = this.state;
        this.state = newState;
        this.stateChangedAt = Instant.now();
        this.updatedAt = Instant.now();
        // Sync legacy status field for backward compatibility
        this.status = newState.getCode();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

