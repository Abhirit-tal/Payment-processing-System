package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Order entity.
 */
public class OrderTest {

    @Test
    void testOrderDefaultValues() {
        Order order = new Order();
        assertEquals("USD", order.getCurrency());
        assertEquals(PaymentState.CREATED, order.getState());
        assertNotNull(order.getCreatedAt());
        assertNotNull(order.getUpdatedAt());
    }

    @Test
    void testOrderSettersAndGetters() {
        Order order = new Order();

        order.setId(1L);
        order.setExternalId("ext-123");
        order.setCurrency("EUR");
        order.setAmount(new BigDecimal("99.99"));
        order.setState(PaymentState.AUTHORIZED);
        order.setStatus("authorized");
        order.setIdempotencyKey("idem-key-123");

        assertEquals(1L, order.getId());
        assertEquals("ext-123", order.getExternalId());
        assertEquals("EUR", order.getCurrency());
        assertEquals(new BigDecimal("99.99"), order.getAmount());
        assertEquals(PaymentState.AUTHORIZED, order.getState());
        assertEquals("authorized", order.getStatus());
        assertEquals("idem-key-123", order.getIdempotencyKey());
    }

    @Test
    void testTransitionTo() {
        Order order = new Order();
        order.setState(PaymentState.CREATED);
        order.setStatus("created");

        Instant beforeTransition = Instant.now();
        order.transitionTo(PaymentState.PENDING);

        assertEquals(PaymentState.PENDING, order.getState());
        assertEquals(PaymentState.CREATED, order.getPreviousState());
        assertEquals("pending", order.getStatus());
        assertNotNull(order.getStateChangedAt());
        assertTrue(order.getStateChangedAt().isAfter(beforeTransition) ||
                   order.getStateChangedAt().equals(beforeTransition));
    }

    @Test
    void testMultipleTransitions() {
        Order order = new Order();
        order.setState(PaymentState.CREATED);

        order.transitionTo(PaymentState.PENDING);
        assertEquals(PaymentState.CREATED, order.getPreviousState());
        assertEquals(PaymentState.PENDING, order.getState());

        order.transitionTo(PaymentState.AUTHORIZED);
        assertEquals(PaymentState.PENDING, order.getPreviousState());
        assertEquals(PaymentState.AUTHORIZED, order.getState());

        order.transitionTo(PaymentState.CAPTURED);
        assertEquals(PaymentState.AUTHORIZED, order.getPreviousState());
        assertEquals(PaymentState.CAPTURED, order.getState());
    }

    @Test
    void testPreviousStateSettersAndGetters() {
        Order order = new Order();
        order.setPreviousState(PaymentState.PENDING);
        assertEquals(PaymentState.PENDING, order.getPreviousState());
    }

    @Test
    void testStateChangedAtSettersAndGetters() {
        Order order = new Order();
        Instant now = Instant.now();
        order.setStateChangedAt(now);
        assertEquals(now, order.getStateChangedAt());
    }

    @Test
    void testCreatedAtSettersAndGetters() {
        Order order = new Order();
        Instant created = Instant.parse("2025-01-01T00:00:00Z");
        order.setCreatedAt(created);
        assertEquals(created, order.getCreatedAt());
    }

    @Test
    void testUpdatedAtSettersAndGetters() {
        Order order = new Order();
        Instant updated = Instant.parse("2025-06-15T12:00:00Z");
        order.setUpdatedAt(updated);
        assertEquals(updated, order.getUpdatedAt());
    }

    @Test
    void testTransitionUpdatesUpdatedAt() {
        Order order = new Order();
        order.setState(PaymentState.CREATED);
        Instant originalUpdatedAt = order.getUpdatedAt();

        // Small delay to ensure time difference
        try { Thread.sleep(10); } catch (InterruptedException ignored) {}

        order.transitionTo(PaymentState.PENDING);

        assertTrue(order.getUpdatedAt().isAfter(originalUpdatedAt) ||
                   order.getUpdatedAt().equals(originalUpdatedAt));
    }
}

