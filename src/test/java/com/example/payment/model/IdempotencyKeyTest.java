package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for IdempotencyKey entity.
 */
public class IdempotencyKeyTest {

    @Test
    void testDefaultConstructor() {
        IdempotencyKey key = new IdempotencyKey();
        assertNotNull(key.getCreatedAt());
    }

    @Test
    void testParameterizedConstructor() {
        IdempotencyKey key = new IdempotencyKey("idem-123", "hash456", "/payments/purchase", "POST");

        assertEquals("idem-123", key.getKey());
        assertEquals("hash456", key.getRequestHash());
        assertEquals("/payments/purchase", key.getRequestPath());
        assertEquals("POST", key.getRequestMethod());
        assertNotNull(key.getCreatedAt());
        assertNotNull(key.getExpiresAt());

        // Verify expires in approximately 24 hours
        long hoursDiff = ChronoUnit.HOURS.between(key.getCreatedAt(), key.getExpiresAt());
        assertTrue(hoursDiff >= 23 && hoursDiff <= 24);
    }

    @Test
    void testSettersAndGetters() {
        IdempotencyKey key = new IdempotencyKey();

        key.setId(1L);
        key.setKey("test-key");
        key.setRequestHash("abc123");
        key.setRequestPath("/payments/authorize");
        key.setRequestMethod("POST");
        key.setResponseBody("{\"status\": \"success\"}");
        key.setResponseStatus(201);
        key.setOrderId(100L);

        Instant created = Instant.now();
        Instant expires = created.plusSeconds(86400);
        Instant locked = created.plusSeconds(1);
        Instant completed = created.plusSeconds(2);

        key.setCreatedAt(created);
        key.setExpiresAt(expires);
        key.setLockedAt(locked);
        key.setCompletedAt(completed);

        assertEquals(1L, key.getId());
        assertEquals("test-key", key.getKey());
        assertEquals("abc123", key.getRequestHash());
        assertEquals("/payments/authorize", key.getRequestPath());
        assertEquals("POST", key.getRequestMethod());
        assertEquals("{\"status\": \"success\"}", key.getResponseBody());
        assertEquals(201, key.getResponseStatus());
        assertEquals(100L, key.getOrderId());
        assertEquals(created, key.getCreatedAt());
        assertEquals(expires, key.getExpiresAt());
        assertEquals(locked, key.getLockedAt());
        assertEquals(completed, key.getCompletedAt());
    }

    @Test
    void testIsLockedWhenLockedAndNotCompleted() {
        IdempotencyKey key = new IdempotencyKey();
        key.setLockedAt(Instant.now());
        key.setCompletedAt(null);

        assertTrue(key.isLocked());
    }

    @Test
    void testIsNotLockedWhenNotLocked() {
        IdempotencyKey key = new IdempotencyKey();
        key.setLockedAt(null);
        key.setCompletedAt(null);

        assertFalse(key.isLocked());
    }

    @Test
    void testIsNotLockedWhenCompleted() {
        IdempotencyKey key = new IdempotencyKey();
        key.setLockedAt(Instant.now());
        key.setCompletedAt(Instant.now());

        assertFalse(key.isLocked());
    }

    @Test
    void testIsCompletedWhenCompleted() {
        IdempotencyKey key = new IdempotencyKey();
        key.setCompletedAt(Instant.now());

        assertTrue(key.isCompleted());
    }

    @Test
    void testIsNotCompletedWhenNotCompleted() {
        IdempotencyKey key = new IdempotencyKey();
        key.setCompletedAt(null);

        assertFalse(key.isCompleted());
    }

    @Test
    void testIsExpiredWhenPastExpiration() {
        IdempotencyKey key = new IdempotencyKey();
        key.setExpiresAt(Instant.now().minusSeconds(3600)); // 1 hour ago

        assertTrue(key.isExpired());
    }

    @Test
    void testIsNotExpiredWhenBeforeExpiration() {
        IdempotencyKey key = new IdempotencyKey();
        key.setExpiresAt(Instant.now().plusSeconds(3600)); // 1 hour from now

        assertFalse(key.isExpired());
    }

    @Test
    void testNullValues() {
        IdempotencyKey key = new IdempotencyKey();
        key.setResponseBody(null);
        key.setResponseStatus(null);
        key.setOrderId(null);
        key.setLockedAt(null);
        key.setCompletedAt(null);

        assertNull(key.getResponseBody());
        assertNull(key.getResponseStatus());
        assertNull(key.getOrderId());
        assertNull(key.getLockedAt());
        assertNull(key.getCompletedAt());
    }
}

