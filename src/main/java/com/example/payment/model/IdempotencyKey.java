package com.example.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for tracking idempotency keys to prevent duplicate payment requests.
 *
 * <p>This implements the standard idempotency pattern used by payment providers
 * like Stripe. When a client provides an Idempotency-Key header, we store the
 * request/response so that retries return the same result.</p>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> What TTL should we use for idempotency keys?</p>
 * <p><strong>A:</strong> 24 hours is standard (Stripe uses 24h). This covers most
 * retry scenarios while preventing unbounded storage growth. Keys older than TTL
 * are eligible for cleanup.</p>
 */
@Entity
@Table(name = "idempotency_keys", indexes = {
    @Index(name = "idx_idempotency_expires_at", columnList = "expires_at")
})
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", unique = true, nullable = false, length = 255)
    private String key;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Column(name = "request_method", length = 10)
    private String requestMethod;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // Constructors
    public IdempotencyKey() {}

    public IdempotencyKey(String key, String requestHash, String requestPath, String requestMethod) {
        this.key = key;
        this.requestHash = requestHash;
        this.requestPath = requestPath;
        this.requestMethod = requestMethod;
        this.createdAt = Instant.now();
        this.expiresAt = Instant.now().plusSeconds(24 * 60 * 60); // 24 hours
    }

    // Check if the request is still being processed
    public boolean isLocked() {
        return lockedAt != null && completedAt == null;
    }

    // Check if the request has completed
    public boolean isCompleted() {
        return completedAt != null;
    }

    // Check if the key has expired
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }

    public String getRequestPath() { return requestPath; }
    public void setRequestPath(String requestPath) { this.requestPath = requestPath; }

    public String getRequestMethod() { return requestMethod; }
    public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

    public String getResponseBody() { return responseBody; }
    public void setResponseBody(String responseBody) { this.responseBody = responseBody; }

    public Integer getResponseStatus() { return responseStatus; }
    public void setResponseStatus(Integer responseStatus) { this.responseStatus = responseStatus; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getLockedAt() { return lockedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}

