package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Subscription entity.
 */
public class SubscriptionTest {

    @Test
    void testSubscriptionGettersAndSetters() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setGatewaySubscriptionId("gw-123");
        sub.setName("Monthly Plan");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setIntervalLength(1);
        sub.setIntervalUnit("months");
        sub.setStartDate(LocalDate.of(2026, 4, 1));
        sub.setStatus("active");
        sub.setCardLast4("1111");
        Instant now = Instant.now();
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        sub.setCancelledAt(now);

        assertEquals(1L, sub.getId());
        assertEquals("gw-123", sub.getGatewaySubscriptionId());
        assertEquals("Monthly Plan", sub.getName());
        assertEquals(new BigDecimal("29.99"), sub.getAmount());
        assertEquals(1, sub.getIntervalLength());
        assertEquals("months", sub.getIntervalUnit());
        assertEquals(LocalDate.of(2026, 4, 1), sub.getStartDate());
        assertEquals("active", sub.getStatus());
        assertEquals("1111", sub.getCardLast4());
        assertEquals(now, sub.getCreatedAt());
        assertEquals(now, sub.getUpdatedAt());
        assertEquals(now, sub.getCancelledAt());
    }

    @Test
    void testDefaultStatus() {
        Subscription sub = new Subscription();
        assertEquals("active", sub.getStatus());
    }

    @Test
    void testDefaultTimestamps() {
        Subscription sub = new Subscription();
        assertNotNull(sub.getCreatedAt());
        assertNotNull(sub.getUpdatedAt());
        assertNull(sub.getCancelledAt());
    }
}

