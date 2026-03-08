package com.example.payment.repository;

import com.example.payment.model.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;

/**
 * Repository for idempotency key entities.
 */
@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * Find an idempotency key by its key value.
     */
    Optional<IdempotencyKey> findByKey(String key);

    /**
     * Check if a key exists and is not expired.
     * Uses pessimistic write lock (SELECT ... FOR UPDATE) to prevent race conditions
     * when multiple instances process the same idempotency key concurrently.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM IdempotencyKey k WHERE k.key = :key AND k.expiresAt > :now")
    Optional<IdempotencyKey> findValidKey(String key, Instant now);

    /**
     * Delete expired keys (for cleanup job).
     */
    @Modifying
    @Query("DELETE FROM IdempotencyKey k WHERE k.expiresAt < :now")
    int deleteExpiredKeys(Instant now);

    /**
     * Check if a key exists.
     */
    boolean existsByKey(String key);
}

