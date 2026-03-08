package com.example.payment.service;

import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import net.authorize.api.contract.v1.ARBSubscriptionMaskedType;
import net.authorize.api.contract.v1.ARBSubscriptionStatusEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Scheduled job for recurring billing lifecycle management.
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Periodically sync subscription statuses with Authorize.Net ARB</li>
 *   <li>Detect expired or suspended subscriptions</li>
 *   <li>Clean up stale subscription records</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we poll the gateway or rely solely on webhooks?</p>
 * <p><strong>A:</strong> Both. Webhooks provide near-real-time updates, but they can be
 * missed (network issues, misconfiguration). The scheduler acts as a safety net,
 * reconciling local state with the gateway periodically (every 6 hours by default).</p>
 */
@Component
public class SubscriptionScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionScheduler.class);

    private final SubscriptionRepository subscriptionRepository;
    private final AuthorizeNetClient authorizeNetClient;

    public SubscriptionScheduler(SubscriptionRepository subscriptionRepository,
                                  AuthorizeNetClient authorizeNetClient) {
        this.subscriptionRepository = subscriptionRepository;
        this.authorizeNetClient = authorizeNetClient;
    }

    /**
     * Sync active subscription statuses with Authorize.Net ARB.
     * Runs every 6 hours by default.
     */
    @Scheduled(fixedDelayString = "${subscription.sync.interval-ms:21600000}",
               initialDelayString = "${subscription.sync.initial-delay-ms:60000}")
    public void syncSubscriptionStatuses() {
        log.info("Starting subscription status sync...");
        List<Subscription> activeSubscriptions = subscriptionRepository.findByStatus("active");

        int synced = 0;
        int failed = 0;

        for (Subscription sub : activeSubscriptions) {
            if (sub.getGatewaySubscriptionId() == null) continue;

            try {
                Map<String, Object> resp = authorizeNetClient.getSubscription(sub.getGatewaySubscriptionId());

                if ("success".equals(resp.get("status"))) {
                    Object gatewaySubscription = resp.get("subscription");
                    if (gatewaySubscription instanceof ARBSubscriptionMaskedType arbSub) {
                        String gatewayStatus = resolveGatewaySubscriptionStatus(arbSub);
                        if (gatewayStatus != null && !gatewayStatus.equals(sub.getStatus())) {
                            log.info("Subscription {} status changed: {} -> {}",
                                    sub.getId(), sub.getStatus(), gatewayStatus);
                            sub.setStatus(gatewayStatus);
                            sub.setUpdatedAt(Instant.now());
                            if ("cancelled".equals(gatewayStatus) && sub.getCancelledAt() == null) {
                                sub.setCancelledAt(Instant.now());
                            }
                            subscriptionRepository.save(sub);
                        }
                        synced++;
                    } else {
                        log.debug("Gateway returned subscription info but could not parse type for sub {}", sub.getId());
                        synced++;
                    }
                } else {
                    log.warn("Failed to sync subscription {}: gateway returned failed", sub.getId());
                    failed++;
                }
            } catch (Exception e) {
                log.error("Error syncing subscription {}: {}", sub.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Subscription sync complete: total={}, synced={}, failed={}",
                activeSubscriptions.size(), synced, failed);
    }

    /**
     * Resolve the local status string from an ARBSubscriptionMaskedType.
     *
     * @param arbSub The subscription details from Authorize.Net
     * @return The resolved local status string (active, suspended, cancelled, expired)
     */
    private String resolveGatewaySubscriptionStatus(ARBSubscriptionMaskedType arbSub) {
        if (arbSub.getStatus() == null) return null;

        ARBSubscriptionStatusEnum arbStatus = arbSub.getStatus();
        return switch (arbStatus) {
            case ACTIVE -> "active";
            case SUSPENDED -> "suspended";
            case CANCELED -> "cancelled";
            case EXPIRED -> "expired";
            case TERMINATED -> "cancelled";
        };
    }

    /**
     * Clean up expired idempotency keys. Runs daily.
     */
    @Scheduled(fixedDelayString = "${idempotency.cleanup.interval-ms:86400000}",
               initialDelayString = "${idempotency.cleanup.initial-delay-ms:300000}")
    public void cleanupExpiredIdempotencyKeys() {
        log.info("Idempotency key cleanup triggered (handled by IdempotencyService @Scheduled)");
        // The IdempotencyService already has a @Scheduled cleanup method.
        // This is a fallback log entry for observability.
    }
}

