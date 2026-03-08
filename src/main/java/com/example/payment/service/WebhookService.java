package com.example.payment.service;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventQueue;
import com.example.payment.model.WebhookEvent;
import com.example.payment.repository.WebhookEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Service for handling Authorize.Net webhook events.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> How should we validate webhook authenticity?</p>
 * <p><strong>A:</strong> Authorize.Net sends an X-ANET-Signature header containing a
 * SHA-512 HMAC of the webhook payload, signed with the webhook signature key configured
 * in the Authorize.Net merchant portal. We validate this signature before processing.</p>
 *
 * <p><strong>Q:</strong> How do we ensure idempotency for webhook events?</p>
 * <p><strong>A:</strong> Each webhook has a unique notificationId. We check the database
 * for duplicates before processing. If already processed, we return 200 OK (Authorize.Net
 * expects this) without re-processing.</p>
 *
 * <p><strong>Q:</strong> Should webhooks be processed synchronously or asynchronously?</p>
 * <p><strong>A:</strong> Asynchronously. We persist the webhook event immediately (sync),
 * then publish it to the in-memory PaymentEventQueue for async processing. This ensures
 * we respond to Authorize.Net quickly (they require < 20s response) and can handle
 * processing failures via retry.</p>
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    @Value("${authnet.webhook.signature-key:}")
    private String signatureKey;

    private final WebhookEventRepository webhookEventRepository;
    private final PaymentEventQueue eventQueue;
    private final ObjectMapper objectMapper;

    public WebhookService(WebhookEventRepository webhookEventRepository,
                          PaymentEventQueue eventQueue,
                          ObjectMapper objectMapper) {
        this.webhookEventRepository = webhookEventRepository;
        this.eventQueue = eventQueue;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate the webhook signature from Authorize.Net.
     *
     * @param payload   Raw request body
     * @param signature Value of X-ANET-Signature header (format: sha512=HEXDIGEST)
     * @return true if signature is valid
     */
    public boolean validateSignature(String payload, String signature) {
        if (signatureKey == null || signatureKey.isBlank()) {
            log.warn("Webhook signature key not configured — skipping validation");
            return true; // In dev mode, allow unsigned webhooks
        }
        if (signature == null || !signature.startsWith("sha512=")) {
            log.warn("Invalid webhook signature format");
            return false;
        }

        try {
            String expectedHash = signature.substring(7).toUpperCase();
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(signatureKey.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String calculatedHash = HexFormat.of().withUpperCase().formatHex(hmac);

            return expectedHash.equals(calculatedHash);
        } catch (Exception e) {
            log.error("Error validating webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Check if a webhook event has already been processed (idempotency).
     */
    public boolean isDuplicate(String notificationId) {
        return webhookEventRepository.existsByNotificationId(notificationId);
    }

    /**
     * Process and persist a webhook event, then dispatch to the event queue.
     */
    @Transactional
    public WebhookEvent processWebhook(String notificationId, String rawPayload) {
        // Check for duplicate
        if (isDuplicate(notificationId)) {
            log.info("Duplicate webhook event ignored: {}", notificationId);
            return webhookEventRepository.findByNotificationId(notificationId).orElse(null);
        }

        // Parse event type from payload
        String eventType = "unknown";
        try {
            JsonNode root = objectMapper.readTree(rawPayload);
            if (root.has("eventType")) {
                eventType = root.get("eventType").asText();
            }
        } catch (Exception e) {
            log.warn("Could not parse webhook payload: {}", e.getMessage());
        }

        // Persist the webhook event
        WebhookEvent event = new WebhookEvent();
        event.setNotificationId(notificationId);
        event.setEventType(eventType);
        event.setPayload(rawPayload);
        event.setStatus("received");
        event = webhookEventRepository.save(event);

        // Publish to async processing queue
        eventQueue.publish(PaymentEvent.of(
                PaymentEvent.WEBHOOK_RECEIVED,
                null,
                notificationId,
                Map.of("eventType", eventType, "webhookEventId", event.getId())
        ));

        log.info("Webhook event received and queued: notificationId={}, type={}",
                notificationId, eventType);

        return event;
    }

    /**
     * Mark a webhook event as processed.
     */
    @Transactional
    public void markProcessed(Long webhookEventId) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setStatus("processed");
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
        });
    }

    /**
     * Mark a webhook event as failed.
     */
    @Transactional
    public void markFailed(Long webhookEventId, String errorMessage) {
        webhookEventRepository.findById(webhookEventId).ifPresent(event -> {
            event.setStatus("failed");
            event.setErrorMessage(errorMessage);
            webhookEventRepository.save(event);
        });
    }

    /**
     * Reprocess a previously failed webhook event (called by WebhookRetryService).
     *
     * @param event The failed webhook event to reprocess
     */
    @Transactional
    public void reprocessWebhook(WebhookEvent event) {
        log.info("Reprocessing webhook event: id={}, notificationId={}, type={}",
                event.getId(), event.getNotificationId(), event.getEventType());

        // Re-publish to async processing queue
        eventQueue.publish(PaymentEvent.of(
                PaymentEvent.WEBHOOK_RECEIVED,
                null,
                event.getNotificationId(),
                Map.of("eventType", event.getEventType(),
                       "webhookEventId", event.getId(),
                       "isRetry", true)
        ));

        log.info("Webhook event {} reprocessed and re-queued", event.getId());
    }
}

