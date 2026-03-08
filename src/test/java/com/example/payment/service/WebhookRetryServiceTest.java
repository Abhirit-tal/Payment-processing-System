package com.example.payment.service;

import com.example.payment.model.WebhookEvent;
import com.example.payment.repository.WebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WebhookRetryService — scheduled retry of failed webhook events.
 */
public class WebhookRetryServiceTest {

    private WebhookEventRepository webhookEventRepository;
    private WebhookService webhookService;
    private WebhookRetryService webhookRetryService;

    @BeforeEach
    void setup() {
        webhookEventRepository = mock(WebhookEventRepository.class);
        webhookService = mock(WebhookService.class);
        webhookRetryService = new WebhookRetryService(webhookEventRepository, webhookService);
    }

    private WebhookEvent createFailedEvent(Long id, String notificationId, int retryCount) {
        WebhookEvent event = new WebhookEvent();
        event.setId(id);
        event.setNotificationId(notificationId);
        event.setEventType("net.authorize.payment.authcapture.created");
        event.setPayload("{\"notificationId\":\"" + notificationId + "\"}");
        event.setStatus("failed");
        event.setRetryCount(retryCount);
        event.setMaxRetries(3);
        event.setCreatedAt(Instant.now());
        return event;
    }

    @Nested
    @DisplayName("retryFailedWebhooks")
    class RetryFailedWebhooksTests {

        @Test
        @DisplayName("No failed events — no retries")
        void noFailedEvents() {
            when(webhookEventRepository.findRetriableFailedEvents(anyInt(), any()))
                    .thenReturn(List.of());

            webhookRetryService.retryFailedWebhooks();

            verify(webhookService, never()).reprocessWebhook(any());
        }

        @Test
        @DisplayName("Failed event — retried successfully")
        void retriedSuccessfully() {
            WebhookEvent event = createFailedEvent(1L, "notif-123", 0);

            when(webhookEventRepository.findRetriableFailedEvents(anyInt(), any()))
                    .thenReturn(List.of(event));
            doNothing().when(webhookService).reprocessWebhook(event);
            when(webhookEventRepository.save(any(WebhookEvent.class)))
                    .thenAnswer(i -> i.getArgument(0));

            webhookRetryService.retryFailedWebhooks();

            assertEquals("processed", event.getStatus());
            assertNotNull(event.getProcessedAt());
            assertEquals(1, event.getRetryCount());
            verify(webhookEventRepository).save(event);
        }

        @Test
        @DisplayName("Failed event — retry throws exception — increments count")
        void retryFails() {
            WebhookEvent event = createFailedEvent(1L, "notif-456", 0);

            when(webhookEventRepository.findRetriableFailedEvents(anyInt(), any()))
                    .thenReturn(List.of(event));
            doThrow(new RuntimeException("Processing failed")).when(webhookService).reprocessWebhook(event);
            when(webhookEventRepository.save(any(WebhookEvent.class)))
                    .thenAnswer(i -> i.getArgument(0));

            webhookRetryService.retryFailedWebhooks();

            assertEquals(1, event.getRetryCount());
            assertTrue(event.getErrorMessage().contains("failed"));
            verify(webhookEventRepository).save(event);
        }

        @Test
        @DisplayName("Failed event at retry count 2 — sets next retry with backoff")
        void exponentialBackoff() {
            WebhookEvent event = createFailedEvent(1L, "notif-789", 1);

            when(webhookEventRepository.findRetriableFailedEvents(anyInt(), any()))
                    .thenReturn(List.of(event));
            doThrow(new RuntimeException("Still failing")).when(webhookService).reprocessWebhook(event);
            when(webhookEventRepository.save(any(WebhookEvent.class)))
                    .thenAnswer(i -> i.getArgument(0));

            webhookRetryService.retryFailedWebhooks();

            assertEquals(2, event.getRetryCount());
            assertNotNull(event.getNextRetryAt());
        }

        @Test
        @DisplayName("Multiple failed events processed in order")
        void multipleEvents() {
            WebhookEvent event1 = createFailedEvent(1L, "notif-a", 0);
            WebhookEvent event2 = createFailedEvent(2L, "notif-b", 0);

            when(webhookEventRepository.findRetriableFailedEvents(anyInt(), any()))
                    .thenReturn(List.of(event1, event2));
            doNothing().when(webhookService).reprocessWebhook(any());
            when(webhookEventRepository.save(any(WebhookEvent.class)))
                    .thenAnswer(i -> i.getArgument(0));

            webhookRetryService.retryFailedWebhooks();

            verify(webhookService, times(2)).reprocessWebhook(any());
            verify(webhookEventRepository, times(2)).save(any());
        }
    }
}

