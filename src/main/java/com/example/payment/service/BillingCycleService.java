package com.example.payment.service;

import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Scheduled service for recurring billing cycle management.
 *
 * <h2>Responsibilities:</h2>
 * <ul>
 *   <li>Track next billing dates for active subscriptions</li>
 *   <li>Sync billing status with Authorize.Net ARB (ARB handles actual charges)</li>
 *   <li>Advance next_billing_date after each cycle</li>
 *   <li>Track billing failures and suspend after max failures</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should our scheduler directly charge cards for recurring billing?</p>
 * <p><strong>A:</strong> No. Authorize.Net ARB (Automated Recurring Billing) handles the actual
 * card charges on schedule. Our scheduler tracks billing dates locally, syncs status with
 * the gateway, and advances the next_billing_date. This avoids PCI scope expansion (no need
 * to store card tokens for re-charging) while maintaining local billing lifecycle awareness.</p>
 *
 * <p><strong>Q:</strong> What happens if billing fails at the gateway?</p>
 * <p><strong>A:</strong> ARB handles retry logic internally. Our scheduler detects status changes
 * (active → suspended) via the SubscriptionScheduler sync. We track billing_failures locally
 * and can suspend subscriptions that exceed max failures for manual review.</p>
 */
@Component
public class BillingCycleService {

    private static final Logger log = LoggerFactory.getLogger(BillingCycleService.class);
    private static final int MAX_BILLING_FAILURES = 3;

    private final SubscriptionRepository subscriptionRepository;
    private final AuthorizeNetClient authorizeNetClient;
    private final AuditService auditService;

    public BillingCycleService(SubscriptionRepository subscriptionRepository,
                                AuthorizeNetClient authorizeNetClient,
                                AuditService auditService) {
        this.subscriptionRepository = subscriptionRepository;
        this.authorizeNetClient = authorizeNetClient;
        this.auditService = auditService;
    }

    /**
     * Process due billing cycles. Runs every hour.
     *
     * <p>Queries active subscriptions whose next_billing_date has passed,
     * syncs with gateway to confirm billing occurred, and advances the cycle.</p>
     */
    @Scheduled(fixedDelayString = "${billing.cycle.interval-ms:3600000}",
               initialDelayString = "${billing.cycle.initial-delay-ms:120000}")
    public void processBillingCycles() {
        log.info("Starting billing cycle processing...");

        LocalDate today = LocalDate.now();
        List<Subscription> dueSubscriptions = subscriptionRepository.findSubscriptionsDueForBilling(today);

        if (dueSubscriptions.isEmpty()) {
            log.debug("No subscriptions due for billing today");
            return;
        }

        log.info("Found {} subscriptions due for billing", dueSubscriptions.size());

        int processed = 0;
        int failed = 0;

        for (Subscription sub : dueSubscriptions) {
            try {
                processBillingForSubscription(sub);
                processed++;
            } catch (Exception e) {
                log.error("Error processing billing for subscription {}: {}", sub.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Billing cycle processing complete: due={}, processed={}, failed={}",
                dueSubscriptions.size(), processed, failed);
    }

    /**
     * Initialize next billing dates for subscriptions that don't have one set.
     * Runs daily.
     */
    @Scheduled(fixedDelayString = "${billing.init.interval-ms:86400000}",
               initialDelayString = "${billing.init.initial-delay-ms:300000}")
    public void initializeBillingDates() {
        log.info("Checking for subscriptions without billing dates...");

        List<Subscription> activeSubscriptions = subscriptionRepository.findByStatus("active");
        int initialized = 0;

        for (Subscription sub : activeSubscriptions) {
            if (sub.getNextBillingDate() == null && sub.getStartDate() != null) {
                LocalDate nextDate = calculateNextBillingDate(sub.getStartDate(),
                        sub.getIntervalLength(), sub.getIntervalUnit());
                sub.setNextBillingDate(nextDate);
                sub.setUpdatedAt(Instant.now());
                subscriptionRepository.save(sub);
                initialized++;
                log.debug("Initialized billing date for subscription {}: {}", sub.getId(), nextDate);
            }
        }

        if (initialized > 0) {
            log.info("Initialized billing dates for {} subscriptions", initialized);
        }
    }

    @Transactional
    protected void processBillingForSubscription(Subscription subscription) {
        log.info("Processing billing for subscription {}, next_billing_date={}",
                subscription.getId(), subscription.getNextBillingDate());

        // Check if subscription has exceeded max billing failures
        if (subscription.getBillingFailures() >= MAX_BILLING_FAILURES) {
            log.warn("Subscription {} has exceeded max billing failures ({}). Suspending.",
                    subscription.getId(), MAX_BILLING_FAILURES);
            subscription.setStatus("suspended");
            subscription.setUpdatedAt(Instant.now());
            subscriptionRepository.save(subscription);
            auditService.logErrorAsync("SUBSCRIPTION", subscription.getId(),
                    "MAX_BILLING_FAILURES", "Suspended after " + MAX_BILLING_FAILURES + " failures");
            return;
        }

        // Sync with gateway to check if ARB has processed the billing
        if (subscription.getGatewaySubscriptionId() != null) {
            try {
                Map<String, Object> resp = authorizeNetClient.getSubscription(
                        subscription.getGatewaySubscriptionId());

                if ("success".equals(resp.get("status"))) {
                    // ARB is handling the billing — advance our local tracking
                    advanceBillingCycle(subscription);
                    subscription.setBillingFailures(0); // Reset failure count on success
                    log.info("Billing cycle advanced for subscription {}", subscription.getId());
                } else {
                    // Gateway returned failure — increment failure count
                    subscription.setBillingFailures(subscription.getBillingFailures() + 1);
                    subscription.setUpdatedAt(Instant.now());
                    subscriptionRepository.save(subscription);
                    log.warn("Billing check failed for subscription {}, failures: {}",
                            subscription.getId(), subscription.getBillingFailures());
                }
            } catch (Exception e) {
                log.error("Gateway error checking subscription {}: {}",
                        subscription.getId(), e.getMessage());
                subscription.setBillingFailures(subscription.getBillingFailures() + 1);
                subscription.setUpdatedAt(Instant.now());
                subscriptionRepository.save(subscription);
            }
        } else {
            // No gateway subscription ID — just advance the date (local-only subscription)
            advanceBillingCycle(subscription);
        }
    }

    private void advanceBillingCycle(Subscription subscription) {
        LocalDate currentBillingDate = subscription.getNextBillingDate();
        LocalDate nextDate = calculateNextBillingDate(currentBillingDate,
                subscription.getIntervalLength(), subscription.getIntervalUnit());

        subscription.setNextBillingDate(nextDate);
        subscription.setTotalBilled(subscription.getTotalBilled() + 1);
        subscription.setLastBilledAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        auditService.logGatewayCallAsync(subscription.getId(), "BILLING_CYCLE",
                "cycle=" + subscription.getTotalBilled() + ", next=" + nextDate);
    }

    /**
     * Calculate the next billing date based on interval.
     */
    LocalDate calculateNextBillingDate(LocalDate fromDate, int intervalLength, String intervalUnit) {
        if (fromDate == null) return LocalDate.now();

        return switch (intervalUnit.toLowerCase()) {
            case "days" -> fromDate.plusDays(intervalLength);
            case "months" -> fromDate.plusMonths(intervalLength);
            default -> fromDate.plusMonths(intervalLength); // Default to months
        };
    }
}

