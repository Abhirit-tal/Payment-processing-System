package com.example.payment.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Entity for tracking all audit events in the payment system.
 *
 * <p>Audit logs capture:</p>
 * <ul>
 *   <li>Payment state transitions</li>
 *   <li>User actions on transactions</li>
 *   <li>Sensitive operations (refunds, voids)</li>
 *   <li>Error events for troubleshooting</li>
 * </ul>
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(length = 255)
    private String actor;

    @Column(name = "actor_ip", length = 45)
    private String actorIp;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Constructors
    public AuditLog() {}

    public AuditLog(String entityType, Long entityId, String action) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.action = action;
        this.createdAt = Instant.now();
    }

    // Builder pattern
    public static AuditLogBuilder builder() {
        return new AuditLogBuilder();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getActorIp() { return actorIp; }
    public void setActorIp(String actorIp) { this.actorIp = actorIp; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    /**
     * Builder class for AuditLog.
     */
    public static class AuditLogBuilder {
        private final AuditLog auditLog = new AuditLog();

        public AuditLogBuilder entityType(String entityType) {
            auditLog.setEntityType(entityType);
            return this;
        }

        public AuditLogBuilder entityId(Long entityId) {
            auditLog.setEntityId(entityId);
            return this;
        }

        public AuditLogBuilder action(String action) {
            auditLog.setAction(action);
            return this;
        }

        public AuditLogBuilder actor(String actor) {
            auditLog.setActor(actor);
            return this;
        }

        public AuditLogBuilder actorIp(String actorIp) {
            auditLog.setActorIp(actorIp);
            return this;
        }

        public AuditLogBuilder oldValue(String oldValue) {
            auditLog.setOldValue(oldValue);
            return this;
        }

        public AuditLogBuilder newValue(String newValue) {
            auditLog.setNewValue(newValue);
            return this;
        }

        public AuditLogBuilder metadata(String metadata) {
            auditLog.setMetadata(metadata);
            return this;
        }

        public AuditLog build() {
            return auditLog;
        }
    }
}

