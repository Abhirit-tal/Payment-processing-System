package com.example.payment.service;

import com.example.payment.model.WebhookEvent;
import com.example.payment.repository.WebhookEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service to retry failed webhook events.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> What happens when webhook processing fails?</p>
 * <p><strong>A:</strong> WebhookService marks the event as "failed" but never retries.
 * This service periodically finds failed webhook events (with retry_count &lt; max)
 * and reprocesses them with exponential backoff.</p>
 *
 * <p><strong>Q:</strong> How many times should we retry?</p>
 * <p><strong>A:</strong> Maximum 3 retries with exponential backoff (2min, 8min, 32min).
 * After max retries, the event stays as "failed" for manual investigation.</p>
 */
@Component
public class WebhookRetryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final WebhookEventRepository webhookEventRepository;
    private final WebhookService webhookService;

    public WebhookRetryService(WebhookEventRepository webhookEventRepository,
                                WebhookService webhookService) {
        this.webhookEventRepository = webhookEventRepository;
        this.webhookService = webhookService;
    }

    /**
     * Retry failed webhook events. Runs every 2 minutes.
     */
    @Scheduled(fixedDelayString = "${webhook.retry.interval-ms:120000}",
               initialDelayString = "${webhook.retry.initial-delay-ms:60000}")
    @Transactional
    public void retryFailedWebhooks() {
        log.debug("Scanning for retriable failed webhook events...");

        List<WebhookEvent> failedEvents = webhookEventRepository.findRetriableFailedEvents(
                MAX_RETRY_ATTEMPTS, Instant.now());

        if (failedEvents.isEmpty()) {
            return;
        }

        log.info("Found {} failed webhook events to retry", failedEvents.size());

        for (WebhookEvent event : failedEvents) {
            retryWebhookEvent(event);
        }
    }

    private void retryWebhookEvent(WebhookEvent event) {
        log.info("Retrying webhook event {}, attempt {} of {}",
                event.getId(), event.getRetryCount() + 1, MAX_RETRY_ATTEMPTS);

        try {
            // Re-process the webhook payload
            webhookService.reprocessWebhook(event);

            // Mark as processed on success
            event.setStatus("processed");
            event.setProcessedAt(Instant.now());
            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage(null);
            webhookEventRepository.save(event);

            log.info("Webhook event {} retried successfully", event.getId());
        } catch (Exception e) {
            log.error("Webhook event {} retry failed: {}", event.getId(), e.getMessage());

            event.setRetryCount(event.getRetryCount() + 1);
            event.setErrorMessage("Retry " + event.getRetryCount() + " failed: " + e.getMessage());

            // Calculate next retry with exponential backoff: 2^(retry) * 2 minutes
            if (event.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                long delayMinutes = (long) Math.pow(4, event.getRetryCount()) * 2;
                event.setNextRetryAt(Instant.now().plus(delayMinutes, ChronoUnit.MINUTES));
            }

            webhookEventRepository.save(event);
        }
    }
}

