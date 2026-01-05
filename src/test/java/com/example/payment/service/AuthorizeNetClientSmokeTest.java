package com.example.payment.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AuthorizeNetClientSmokeTest {

    @Test
    public void createTransactionFailsGracefullyWhenNoCredentials() {
        AuthorizeNetClient client = new AuthorizeNetClient();
        Map<String, Object> resp = client.createTransaction(new BigDecimal("1.00"), "USD", null, true);
        assertNotNull(resp);
        assertTrue(resp.containsKey("status"));
        assertEquals("failed", resp.get("status"));
    }

    @Test
    public void captureTransactionFailsGracefullyWhenNoCredentials() {
        AuthorizeNetClient client = new AuthorizeNetClient();
        Map<String, Object> resp = client.captureTransaction("nonexistent", null);
        assertNotNull(resp);
        assertTrue(resp.containsKey("status"));
        assertEquals("failed", resp.get("status"));
    }

    @Test
    public void voidTransactionFailsGracefullyWhenNoCredentials() {
        AuthorizeNetClient client = new AuthorizeNetClient();
        Map<String, Object> resp = client.voidTransaction("nonexistent");
        assertNotNull(resp);
        assertTrue(resp.containsKey("status"));
        assertEquals("failed", resp.get("status"));
    }

    @Test
    public void refundTransactionFailsGracefullyWhenNoCredentials() {
        AuthorizeNetClient client = new AuthorizeNetClient();
        Map<String, Object> resp = client.refundTransaction("nonexistent", new BigDecimal("1.00"), "1111");
        assertNotNull(resp);
        assertTrue(resp.containsKey("status"));
        assertEquals("failed", resp.get("status"));
    }
}

