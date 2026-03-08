package com.example.payment.repository;

import com.example.payment.model.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByNotificationId(String notificationId);
    boolean existsByNotificationId(String notificationId);

    /**
     * Find failed webhook events eligible for retry.
     * Events must have status='failed', retry_count < maxRetries,
     * and either no next_retry_at or next_retry_at has passed.
     */
    @Query("SELECT w FROM WebhookEvent w WHERE w.status = 'failed' " +
           "AND w.retryCount < :maxRetries " +
           "AND (w.nextRetryAt IS NULL OR w.nextRetryAt <= :now) " +
           "ORDER BY w.createdAt ASC")
    List<WebhookEvent> findRetriableFailedEvents(int maxRetries, Instant now);
}

