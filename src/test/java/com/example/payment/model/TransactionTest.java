package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Transaction entity.
 */
public class TransactionTest {

    @Test
    void testTransactionDefaultValues() {
        Transaction tx = new Transaction();
        assertNotNull(tx.getCreatedAt());
    }

    @Test
    void testTransactionSettersAndGetters() {
        Transaction tx = new Transaction();
        Order order = new Order();
        order.setId(100L);

        tx.setId(1L);
        tx.setOrder(order);
        tx.setType("purchase");
        tx.setProviderTxId("prov-123");
        tx.setAmount(new BigDecimal("50.00"));
        tx.setStatus("success");
        tx.setRawResponse("{\"result\": \"ok\"}");

        assertEquals(1L, tx.getId());
        assertEquals(order, tx.getOrder());
        assertEquals(100L, tx.getOrder().getId());
        assertEquals("purchase", tx.getType());
        assertEquals("prov-123", tx.getProviderTxId());
        assertEquals(new BigDecimal("50.00"), tx.getAmount());
        assertEquals("success", tx.getStatus());
        assertEquals("{\"result\": \"ok\"}", tx.getRawResponse());
    }

    @Test
    void testTransactionTypes() {
        Transaction tx = new Transaction();

        tx.setType("authorize");
        assertEquals("authorize", tx.getType());

        tx.setType("capture");
        assertEquals("capture", tx.getType());

        tx.setType("purchase");
        assertEquals("purchase", tx.getType());

        tx.setType("refund");
        assertEquals("refund", tx.getType());

        tx.setType("void");
        assertEquals("void", tx.getType());
    }

    @Test
    void testTransactionStatuses() {
        Transaction tx = new Transaction();

        tx.setStatus("pending");
        assertEquals("pending", tx.getStatus());

        tx.setStatus("success");
        assertEquals("success", tx.getStatus());

        tx.setStatus("failed");
        assertEquals("failed", tx.getStatus());

        tx.setStatus("voided");
        assertEquals("voided", tx.getStatus());

        tx.setStatus("refunded");
        assertEquals("refunded", tx.getStatus());
    }

    @Test
    void testCreatedAtSettersAndGetters() {
        Transaction tx = new Transaction();
        Instant created = Instant.parse("2025-03-15T10:30:00Z");
        tx.setCreatedAt(created);
        assertEquals(created, tx.getCreatedAt());
    }

    @Test
    void testTransactionWithOrder() {
        Order order = new Order();
        order.setId(999L);
        order.setAmount(new BigDecimal("100.00"));
        order.setCurrency("USD");

        Transaction tx = new Transaction();
        tx.setOrder(order);
        tx.setAmount(new BigDecimal("100.00"));
        tx.setType("purchase");

        assertNotNull(tx.getOrder());
        assertEquals(order.getAmount(), tx.getAmount());
    }

    @Test
    void testRawResponseCanBeNull() {
        Transaction tx = new Transaction();
        tx.setRawResponse(null);
        assertNull(tx.getRawResponse());
    }

    @Test
    void testProviderTxIdCanBeNull() {
        Transaction tx = new Transaction();
        tx.setProviderTxId(null);
        assertNull(tx.getProviderTxId());
    }
}

