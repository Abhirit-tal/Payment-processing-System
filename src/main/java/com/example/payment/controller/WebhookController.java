package com.example.payment.controller;

import com.example.payment.model.WebhookEvent;
import com.example.payment.service.WebhookService;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Webhook controller for receiving async payment events from Authorize.Net.
 *
 * <p>This endpoint is NOT protected by JWT — it uses HMAC signature validation
 * instead (Authorize.Net signs the payload with the webhook signature key).</p>
 *
 * <h2>Flow:</h2>
 * <ol>
 *   <li>Authorize.Net sends POST to /webhooks/authorize-net</li>
 *   <li>We validate the X-ANET-Signature header (SHA-512 HMAC)</li>
 *   <li>We check for duplicate notification ID (idempotency)</li>
 *   <li>We persist the event and queue it for async processing</li>
 *   <li>Return 200 OK immediately (Authorize.Net requires fast response)</li>
 * </ol>
 */
@RestController
@RequestMapping("/webhooks")
@Tag(name = "Webhooks", description = "Async payment event handlers")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookService webhookService;

    public WebhookController(WebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping("/authorize-net")
    @RateLimiter(name = "webhookApi")
    @Operation(summary = "Receive Authorize.Net webhook events",
            description = "Validates signature, deduplicates, and queues for async processing")
    public ResponseEntity<?> handleWebhook(
            @RequestHeader(value = "X-ANET-Signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.info("Webhook received from Authorize.Net");

        // 1. Validate signature
        if (!webhookService.validateSignature(rawPayload, signature)) {
            log.warn("Webhook signature validation failed");
            return ResponseEntity.status(401).body(Map.of("detail", "Invalid webhook signature"));
        }

        // 2. Extract notification ID for idempotency
        String notificationId = extractNotificationId(rawPayload);

        // 3. Check for duplicate (idempotent handling)
        if (webhookService.isDuplicate(notificationId)) {
            log.info("Duplicate webhook event, returning 200: {}", notificationId);
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }

        // 4. Process (persist + queue for async processing)
        WebhookEvent event = webhookService.processWebhook(notificationId, rawPayload);

        return ResponseEntity.ok(Map.of(
                "status", "received",
                "notification_id", notificationId,
                "event_id", event != null ? event.getId() : "unknown"
        ));
    }

    /**
     * Extract notification ID from webhook payload.
     * Authorize.Net includes a notificationId field in the JSON payload.
     */
    private String extractNotificationId(String rawPayload) {
        try {
            // Simple extraction — avoid full Jackson parse for speed
            int idx = rawPayload.indexOf("\"notificationId\"");
            if (idx >= 0) {
                int start = rawPayload.indexOf("\"", idx + 16) + 1;
                int end = rawPayload.indexOf("\"", start);
                if (start > 0 && end > start) {
                    return rawPayload.substring(start, end);
                }
            }
        } catch (Exception e) {
            log.warn("Could not extract notificationId from webhook payload");
        }
        // Fallback: generate a unique ID
        return "webhook-" + UUID.randomUUID();
    }
}

