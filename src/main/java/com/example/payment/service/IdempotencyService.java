package com.example.payment.service;

import com.example.payment.exception.IdempotencyConflictException;
import com.example.payment.model.IdempotencyKey;
import com.example.payment.repository.IdempotencyKeyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Service for handling idempotency in payment requests.
 *
 * <p>Implements the idempotency pattern to ensure that duplicate requests
 * (due to retries, network issues, etc.) return the same result without
 * processing the payment twice.</p>
 *
 * <h2>Usage:</h2>
 * <ol>
 *   <li>Client sends request with Idempotency-Key header</li>
 *   <li>Service checks if key exists and is completed → return cached response</li>
 *   <li>Service checks if key exists and is locked → return 409 Conflict</li>
 *   <li>Service creates new key, locks it, processes request, stores result</li>
 * </ol>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we validate that the request body matches for duplicate keys?</p>
 * <p><strong>A:</strong> Yes - we hash the request body and compare. If a client reuses
 * an idempotency key with different parameters, that's an error (400) not a retry.</p>
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public IdempotencyService(IdempotencyKeyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Check if an idempotency key already exists and return cached result.
     *
     * @param key The idempotency key from request header
     * @param requestBody The request body for hash comparison
     * @param requestPath The request path
     * @param requestMethod The HTTP method
     * @return Optional cached response if key exists and is completed
     * @throws IdempotencyConflictException if key exists but request is different or still processing
     */
    @Transactional
    public Optional<CachedResponse> checkIdempotency(String key, Object requestBody,
                                                     String requestPath, String requestMethod) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String requestHash = hashRequest(requestBody);
        Optional<IdempotencyKey> existingKey = repository.findValidKey(key, Instant.now());

        if (existingKey.isEmpty()) {
            return Optional.empty();
        }

        IdempotencyKey idempotencyKey = existingKey.get();

        // Validate request hash matches
        if (!idempotencyKey.getRequestHash().equals(requestHash)) {
            log.warn("Idempotency key {} reused with different request body", key);
            throw new IdempotencyConflictException(
                    "Idempotency key was used with a different request body");
        }

        // Check if request is still being processed
        if (idempotencyKey.isLocked() && !idempotencyKey.isCompleted()) {
            log.info("Idempotency key {} is currently being processed", key);
            throw new IdempotencyConflictException(
                    "A request with this idempotency key is currently being processed");
        }

        // Return cached response if completed
        if (idempotencyKey.isCompleted()) {
            log.info("Returning cached response for idempotency key {}", key);
            return Optional.of(new CachedResponse(
                    idempotencyKey.getResponseBody(),
                    idempotencyKey.getResponseStatus()
            ));
        }

        return Optional.empty();
    }

    /**
     * Create and lock a new idempotency key for processing.
     *
     * @param key The idempotency key
     * @param requestBody The request body
     * @param requestPath The request path
     * @param requestMethod The HTTP method
     * @return The created IdempotencyKey entity
     */
    @Transactional
    public IdempotencyKey createAndLock(String key, Object requestBody,
                                        String requestPath, String requestMethod) {
        String requestHash = hashRequest(requestBody);

        IdempotencyKey idempotencyKey = new IdempotencyKey(key, requestHash, requestPath, requestMethod);
        idempotencyKey.setLockedAt(Instant.now());

        return repository.save(idempotencyKey);
    }

    /**
     * Complete the idempotency key with the response.
     *
     * @param idempotencyKey The idempotency key entity
     * @param responseBody The response body to cache
     * @param responseStatus The HTTP status code
     * @param orderId The order ID if applicable
     */
    @Transactional
    public void complete(IdempotencyKey idempotencyKey, Object responseBody,
                        int responseStatus, Long orderId) {
        try {
            idempotencyKey.setResponseBody(objectMapper.writeValueAsString(responseBody));
        } catch (JsonProcessingException e) {
            idempotencyKey.setResponseBody("{}");
        }
        idempotencyKey.setResponseStatus(responseStatus);
        idempotencyKey.setOrderId(orderId);
        idempotencyKey.setCompletedAt(Instant.now());

        repository.save(idempotencyKey);
        log.debug("Completed idempotency key {} with status {}", idempotencyKey.getKey(), responseStatus);
    }

    /**
     * Release a locked idempotency key without completing (on error).
     */
    @Transactional
    public void release(IdempotencyKey idempotencyKey) {
        // Delete the key so it can be retried
        repository.delete(idempotencyKey);
        log.debug("Released idempotency key {}", idempotencyKey.getKey());
    }

    /**
     * Scheduled job to clean up expired idempotency keys.
     * Runs every hour.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredKeys() {
        int deleted = repository.deleteExpiredKeys(Instant.now());
        if (deleted > 0) {
            log.info("Cleaned up {} expired idempotency keys", deleted);
        }
    }

    /**
     * Generate a hash of the request body for comparison.
     */
    private String hashRequest(Object requestBody) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            log.error("Failed to hash request body: {}", e.getMessage());
            return "unknown";
        }
    }

    /**
     * Record class for cached responses.
     */
    public record CachedResponse(String body, int status) {}
}

