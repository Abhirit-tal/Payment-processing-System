package com.example.payment.controller;

import com.example.payment.dto.SubscriptionRequests;
import com.example.payment.model.Subscription;
import com.example.payment.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for SubscriptionController.
 */
public class SubscriptionControllerTest {

    private MockMvc mockMvc;
    private SubscriptionService subscriptionService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setup() {
        subscriptionService = mock(SubscriptionService.class);
        SubscriptionController controller = new SubscriptionController(subscriptionService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private Subscription createMockSubscription(Long id, String status) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setGatewaySubscriptionId("gw-sub-" + id);
        sub.setName("Test Plan");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setIntervalLength(1);
        sub.setIntervalUnit("months");
        sub.setStartDate(LocalDate.of(2026, 4, 1));
        sub.setStatus(status);
        sub.setCreatedAt(Instant.now());
        return sub;
    }

    @Nested
    class CreateSubscription {

        @Test
        void createSubscriptionSuccess() throws Exception {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionService.createSubscription(anyString(), any(), anyInt(), anyString(), anyString(), anyMap()))
                    .thenReturn(sub);

            String body = """
                {
                    "name": "Monthly Plan",
                    "amount": 29.99,
                    "intervalLength": 1,
                    "intervalUnit": "months",
                    "startDate": "2026-04-01",
                    "card": {
                        "number": "4111111111111111",
                        "expMonth": 12,
                        "expYear": 2030,
                        "cvv": "123"
                    }
                }
                """;

            mockMvc.perform(post("/payments/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("active"));
        }

        @Test
        void createSubscriptionGatewayFailed() throws Exception {
            Subscription sub = createMockSubscription(1L, "failed");
            when(subscriptionService.createSubscription(anyString(), any(), anyInt(), anyString(), anyString(), anyMap()))
                    .thenReturn(sub);

            String body = """
                {
                    "name": "Monthly Plan",
                    "amount": 29.99,
                    "intervalLength": 1,
                    "intervalUnit": "months",
                    "startDate": "2026-04-01",
                    "card": {
                        "number": "4111111111111111",
                        "expMonth": 12,
                        "expYear": 2030,
                        "cvv": "123"
                    }
                }
                """;

            mockMvc.perform(post("/payments/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                    .andExpect(status().is(502));
        }

        @Test
        void createSubscriptionValidationError() throws Exception {
            mockMvc.perform(post("/payments/subscriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class GetSubscription {

        @Test
        void getSubscriptionSuccess() throws Exception {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionService.getSubscription(1L)).thenReturn(Optional.of(sub));

            mockMvc.perform(get("/payments/subscriptions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.name").value("Test Plan"));
        }

        @Test
        void getSubscriptionNotFound() throws Exception {
            when(subscriptionService.getSubscription(99L)).thenReturn(Optional.empty());

            mockMvc.perform(get("/payments/subscriptions/99"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class UpdateSubscription {

        @Test
        void updateSubscriptionSuccess() throws Exception {
            Subscription sub = createMockSubscription(1L, "active");
            sub.setName("Updated Plan");
            when(subscriptionService.updateSubscription(eq(1L), anyString(), any())).thenReturn(Optional.of(sub));

            mockMvc.perform(put("/payments/subscriptions/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Updated Plan\", \"amount\": 49.99}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Plan"));
        }

        @Test
        void updateSubscriptionNotFound() throws Exception {
            when(subscriptionService.updateSubscription(eq(99L), any(), any())).thenReturn(Optional.empty());

            mockMvc.perform(put("/payments/subscriptions/99")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\": \"Updated Plan\"}"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class CancelSubscription {

        @Test
        void cancelSubscriptionSuccess() throws Exception {
            Subscription sub = createMockSubscription(1L, "cancelled");
            sub.setCancelledAt(Instant.now());
            when(subscriptionService.cancelSubscription(1L)).thenReturn(Optional.of(sub));

            mockMvc.perform(delete("/payments/subscriptions/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("cancelled"));
        }

        @Test
        void cancelSubscriptionNotFound() throws Exception {
            when(subscriptionService.cancelSubscription(99L)).thenReturn(Optional.empty());

            mockMvc.perform(delete("/payments/subscriptions/99"))
                    .andExpect(status().isNotFound());
        }
    }
}

