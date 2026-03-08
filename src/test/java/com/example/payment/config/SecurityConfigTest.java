package com.example.payment.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for SecurityConfig — verifies endpoint access rules.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@Import(TestRabbitConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/payments/health"))
                .andExpect(status().isOk());
    }

    @Test
    void authEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/auth/token")
                        .contentType("application/json")
                        .content("{\"developer_key\":\"dev-local-key\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void webhookEndpointIsPublic() throws Exception {
        // Webhook is public but requires valid signature — 401 from WebhookService (not Spring Security)
        mockMvc.perform(post("/webhooks/authorize-net")
                        .contentType("application/json")
                        .content("{\"test\":\"data\"}")
                        .header("X-ANET-Signature", "sha512=invalid"))
                .andExpect(status().isUnauthorized()); // 401 from signature validation, not security filter
    }


    @Test
    void paymentEndpointsRequireAuth() throws Exception {
        mockMvc.perform(post("/payments/purchase")
                        .contentType("application/json")
                        .content("{\"amount\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void paymentEndpointsRejectInvalidToken() throws Exception {
        mockMvc.perform(post("/payments/purchase")
                        .contentType("application/json")
                        .content("{\"amount\":10}")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }
}

