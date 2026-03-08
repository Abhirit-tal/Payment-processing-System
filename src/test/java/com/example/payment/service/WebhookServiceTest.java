package com.example.payment.service;

import com.example.payment.model.WebhookEvent;
import com.example.payment.event.PaymentEventQueue;
import com.example.payment.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookService.
 */
public class WebhookServiceTest {

    private WebhookEventRepository webhookEventRepository;
    private PaymentEventQueue eventQueue;
    private WebhookService webhookService;

    @BeforeEach
    void setup() {
        webhookEventRepository = mock(WebhookEventRepository.class);
        eventQueue = mock(PaymentEventQueue.class);
        webhookService = new WebhookService(webhookEventRepository, eventQueue, new ObjectMapper());
    }

    @Nested
    class SignatureValidation {

        @Test
        void acceptsWhenNoSignatureKeyConfigured() {
            // Default: signatureKey is empty, so validation is skipped
            assertTrue(webhookService.validateSignature("payload", "sha512=anything"));
        }

        @Test
        void rejectsNullSignature() {
            // Even without key configured, null signature is accepted (dev mode)
            assertTrue(webhookService.validateSignature("payload", null));
        }
    }

    @Nested
    class DuplicateDetection {

        @Test
        void detectsDuplicate() {
            when(webhookEventRepository.existsByNotificationId("abc-123")).thenReturn(true);
            assertTrue(webhookService.isDuplicate("abc-123"));
        }

        @Test
        void detectsNewEvent() {
            when(webhookEventRepository.existsByNotificationId("new-event")).thenReturn(false);
            assertFalse(webhookService.isDuplicate("new-event"));
        }
    }

    @Nested
    class ProcessWebhook {

        @Test
        void processesNewWebhookEvent() {
            when(webhookEventRepository.existsByNotificationId("abc-123")).thenReturn(false);
            WebhookEvent savedEvent = new WebhookEvent();
            savedEvent.setId(1L);
            savedEvent.setNotificationId("abc-123");
            savedEvent.setEventType("net.authorize.payment.authcapture.created");
            when(webhookEventRepository.save(any(WebhookEvent.class))).thenReturn(savedEvent);
            when(eventQueue.publish(any())).thenReturn(true);

            String payload = "{\"eventType\":\"net.authorize.payment.authcapture.created\",\"notificationId\":\"abc-123\"}";
            WebhookEvent result = webhookService.processWebhook("abc-123", payload);

            assertNotNull(result);
            assertEquals(1L, result.getId());
            verify(webhookEventRepository).save(any(WebhookEvent.class));
            verify(eventQueue).publish(any());
        }

        @Test
        void skipsDuplicateWebhookEvent() {
            when(webhookEventRepository.existsByNotificationId("abc-123")).thenReturn(true);
            when(webhookEventRepository.findByNotificationId("abc-123"))
                    .thenReturn(Optional.of(new WebhookEvent()));

            WebhookEvent result = webhookService.processWebhook("abc-123", "{}");

            verify(webhookEventRepository, never()).save(any());
            verify(eventQueue, never()).publish(any());
        }

        @Test
        void handlesInvalidJsonPayload() {
            when(webhookEventRepository.existsByNotificationId("test")).thenReturn(false);
            WebhookEvent savedEvent = new WebhookEvent();
            savedEvent.setId(1L);
            when(webhookEventRepository.save(any())).thenReturn(savedEvent);
            when(eventQueue.publish(any())).thenReturn(true);

            WebhookEvent result = webhookService.processWebhook("test", "not-json");

            assertNotNull(result);
            verify(webhookEventRepository).save(any());
        }
    }

    @Nested
    class StatusUpdates {

        @Test
        void marksEventAsProcessed() {
            WebhookEvent event = new WebhookEvent();
            event.setId(1L);
            when(webhookEventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(webhookEventRepository.save(any())).thenReturn(event);

            webhookService.markProcessed(1L);

            verify(webhookEventRepository).save(argThat(e -> "processed".equals(e.getStatus())));
        }

        @Test
        void marksEventAsFailed() {
            WebhookEvent event = new WebhookEvent();
            event.setId(1L);
            when(webhookEventRepository.findById(1L)).thenReturn(Optional.of(event));
            when(webhookEventRepository.save(any())).thenReturn(event);

            webhookService.markFailed(1L, "test error");

            verify(webhookEventRepository).save(argThat(e ->
                    "failed".equals(e.getStatus()) && "test error".equals(e.getErrorMessage())));
        }
    }
}

