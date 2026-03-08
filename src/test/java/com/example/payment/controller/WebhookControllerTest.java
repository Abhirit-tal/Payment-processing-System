package com.example.payment.controller;

import com.example.payment.model.WebhookEvent;
import com.example.payment.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for WebhookController.
 */
public class WebhookControllerTest {

    private MockMvc mockMvc;
    private WebhookService webhookService;

    @BeforeEach
    void setup() {
        webhookService = mock(WebhookService.class);
        WebhookController controller = new WebhookController(webhookService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    class SignatureValidation {

        @Test
        void rejectsInvalidSignature() throws Exception {
            when(webhookService.validateSignature(anyString(), anyString())).thenReturn(false);

            mockMvc.perform(post("/webhooks/authorize-net")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-ANET-Signature", "sha512=invalid")
                    .content("{\"eventType\":\"net.authorize.payment.authcapture.created\",\"notificationId\":\"abc-123\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void acceptsValidSignature() throws Exception {
            when(webhookService.validateSignature(anyString(), anyString())).thenReturn(true);
            when(webhookService.isDuplicate(anyString())).thenReturn(false);
            WebhookEvent event = new WebhookEvent();
            event.setId(1L);
            event.setNotificationId("abc-123");
            when(webhookService.processWebhook(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/webhooks/authorize-net")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-ANET-Signature", "sha512=valid")
                    .content("{\"eventType\":\"net.authorize.payment.authcapture.created\",\"notificationId\":\"abc-123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("received"));
        }
    }

    @Nested
    class IdempotencyTests {

        @Test
        void duplicateWebhookReturns200() throws Exception {
            when(webhookService.validateSignature(anyString(), any())).thenReturn(true);
            when(webhookService.isDuplicate(anyString())).thenReturn(true);

            mockMvc.perform(post("/webhooks/authorize-net")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"net.authorize.payment.authcapture.created\",\"notificationId\":\"abc-123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("already_processed"));

            // processWebhook should NOT be called for duplicates
            verify(webhookService, never()).processWebhook(anyString(), anyString());
        }
    }

    @Nested
    class PayloadProcessing {

        @Test
        void processesValidWebhookEvent() throws Exception {
            when(webhookService.validateSignature(anyString(), any())).thenReturn(true);
            when(webhookService.isDuplicate(anyString())).thenReturn(false);
            WebhookEvent event = new WebhookEvent();
            event.setId(42L);
            when(webhookService.processWebhook(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/webhooks/authorize-net")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"net.authorize.payment.refund.created\",\"notificationId\":\"def-456\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notification_id").value("def-456"))
                    .andExpect(jsonPath("$.event_id").value(42));
        }

        @Test
        void handlesPayloadWithoutNotificationId() throws Exception {
            when(webhookService.validateSignature(anyString(), any())).thenReturn(true);
            when(webhookService.isDuplicate(anyString())).thenReturn(false);
            WebhookEvent event = new WebhookEvent();
            event.setId(99L);
            when(webhookService.processWebhook(anyString(), anyString())).thenReturn(event);

            mockMvc.perform(post("/webhooks/authorize-net")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"eventType\":\"net.authorize.payment.void.created\"}"))
                    .andExpect(status().isOk());
        }
    }
}

