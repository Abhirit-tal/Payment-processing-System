package com.example.payment.service;

import com.example.payment.exception.TransientPaymentException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for AuthorizeNetClient.
 *
 * <p>These tests verify that the client handles missing/invalid credentials
 * gracefully. When the SDK returns an error response (not an exception),
 * the client returns a "failed" status map. When a true communication error
 * occurs, TransientPaymentException is thrown (caught by @Retryable in prod).</p>
 */
public class AuthorizeNetClientSmokeTest {

    private AuthorizeNetClient client;

    @BeforeEach
    void setup() {
        client = new AuthorizeNetClient();
        client.init(); // manually invoke @PostConstruct since we're not using Spring context
    }

    @Test
    public void createTransactionReturnsFailedOrThrowsWhenNoCredentials() {
        // Without credentials, the SDK may return error response or throw.
        // Either outcome is acceptable — both indicate graceful handling.
        try {
            Map<String, Object> resp = client.createTransaction(new BigDecimal("1.00"), "USD", null, true);
            assertNotNull(resp);
            assertTrue(resp.containsKey("status"));
            assertEquals("failed", resp.get("status"));
        } catch (TransientPaymentException e) {
            // Also acceptable — gateway communication error wrapped properly
            assertTrue(e.isRetryable());
        }
    }

    @Test
    public void captureTransactionReturnsFailedOrThrowsWhenNoCredentials() {
        try {
            Map<String, Object> resp = client.captureTransaction("nonexistent", null);
            assertNotNull(resp);
            assertTrue(resp.containsKey("status"));
            assertEquals("failed", resp.get("status"));
        } catch (TransientPaymentException e) {
            assertTrue(e.isRetryable());
        }
    }

    @Test
    public void voidTransactionReturnsFailedOrThrowsWhenNoCredentials() {
        try {
            Map<String, Object> resp = client.voidTransaction("nonexistent");
            assertNotNull(resp);
            assertTrue(resp.containsKey("status"));
            assertEquals("failed", resp.get("status"));
        } catch (TransientPaymentException e) {
            assertTrue(e.isRetryable());
        }
    }

    @Test
    public void refundTransactionReturnsFailedOrThrowsWhenNoCredentials() {
        try {
            Map<String, Object> resp = client.refundTransaction("nonexistent", new BigDecimal("1.00"), "1111");
            assertNotNull(resp);
            assertTrue(resp.containsKey("status"));
            assertEquals("failed", resp.get("status"));
        } catch (TransientPaymentException e) {
            assertTrue(e.isRetryable());
        }
    }

    @Test
    public void initSetsEnvironmentAndMerchantAuth() {
        // Verify that init() doesn't throw with empty/null credentials
        AuthorizeNetClient freshClient = new AuthorizeNetClient();
        assertDoesNotThrow(() -> freshClient.init());
    }
}
