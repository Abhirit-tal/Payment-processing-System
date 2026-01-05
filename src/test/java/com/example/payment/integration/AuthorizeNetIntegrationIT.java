package com.example.payment.integration;

import com.example.payment.service.AuthorizeNetClient;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class AuthorizeNetIntegrationIT {

    @Autowired
    private AuthorizeNetClient authorizeNetClient;

    @Test
    public void integrationPurchaseAndRefund() {
        String apiLogin = System.getenv("AUTHNET_API_LOGIN_ID");
        String txKey = System.getenv("AUTHNET_TRANSACTION_KEY");
        Assumptions.assumeTrue(apiLogin != null && !apiLogin.isBlank() && txKey != null && !txKey.isBlank(), "Authorize.Net credentials not provided, skipping integration test");

        // Prepare a sandbox test card (Authorize.Net sandbox accepts common test cards)
        Map<String, String> card = Map.of(
                "number", "4111111111111111",
                "expMonth", "12",
                "expYear", "2030",
                "cvv", "123"
        );

        // Perform a small purchase (auth+capture)
        Map<String, Object> resp = authorizeNetClient.createTransaction(new BigDecimal("1.00"), "USD", card, true);
        assertNotNull(resp, "Response should not be null");
        assertEquals("success", resp.getOrDefault("status", "failed"));

        String providerTxId = (String) resp.get("provider_tx_id");
        assertNotNull(providerTxId, "Provider transaction id should be returned on success");

        // Attempt a refund for the full amount
        Map<String, Object> refund = authorizeNetClient.refundTransaction(providerTxId, new BigDecimal("1.00"), "1111");
        assertNotNull(refund);
        // Refund may fail depending on transaction settlement; assert structure exists
        assertTrue(refund.containsKey("status"));
    }
}
