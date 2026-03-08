package com.example.payment.repository;

import com.example.payment.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for audit log entities.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Find all audit logs for a specific entity (unpaginated - legacy).
     */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    /**
     * Find all audit logs for a specific entity (paginated).
     */
    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId, Pageable pageable);

    /**
     * Find audit logs by action type.
     */
    List<AuditLog> findByActionOrderByCreatedAtDesc(String action);

    /**
     * Find audit logs by action type (paginated).
     */
    Page<AuditLog> findByAction(String action, Pageable pageable);

    /**
     * Find audit logs within a time range.
     */
    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);

    /**
     * Find audit logs within a time range (paginated).
     */
    Page<AuditLog> findByCreatedAtBetween(Instant start, Instant end, Pageable pageable);

    /**
     * Find audit logs by actor.
     */
    List<AuditLog> findByActorOrderByCreatedAtDesc(String actor);

    /**
     * Find audit logs by actor (paginated).
     */
    Page<AuditLog> findByActor(String actor, Pageable pageable);
}

