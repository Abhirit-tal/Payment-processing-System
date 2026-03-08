package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WebhookEvent entity.
 */
public class WebhookEventTest {

    @Test
    void testWebhookEventGettersAndSetters() {
        WebhookEvent event = new WebhookEvent();
        event.setId(1L);
        event.setNotificationId("notif-abc-123");
        event.setEventType("net.authorize.payment.authcapture.created");
        event.setPayload("{\"key\":\"value\"}");
        event.setStatus("processed");
        Instant now = Instant.now();
        event.setProcessedAt(now);
        event.setErrorMessage("test error");
        event.setCreatedAt(now);

        assertEquals(1L, event.getId());
        assertEquals("notif-abc-123", event.getNotificationId());
        assertEquals("net.authorize.payment.authcapture.created", event.getEventType());
        assertEquals("{\"key\":\"value\"}", event.getPayload());
        assertEquals("processed", event.getStatus());
        assertEquals(now, event.getProcessedAt());
        assertEquals("test error", event.getErrorMessage());
        assertEquals(now, event.getCreatedAt());
    }

    @Test
    void testDefaultStatus() {
        WebhookEvent event = new WebhookEvent();
        assertEquals("received", event.getStatus());
    }

    @Test
    void testDefaultTimestamp() {
        WebhookEvent event = new WebhookEvent();
        assertNotNull(event.getCreatedAt());
        assertNull(event.getProcessedAt());
        assertNull(event.getErrorMessage());
    }
}

