package com.example.payment.service;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventQueue;
import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing recurring billing subscriptions via Authorize.Net ARB API.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> How should we handle subscription lifecycle locally vs at the gateway?</p>
 * <p><strong>A:</strong> We persist subscription state locally AND send commands to the gateway.
 * Local state is the source of truth for our application logic; gateway state is authoritative
 * for billing. Webhook events from Authorize.Net reconcile the two.</p>
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

    private final AuthorizeNetClient authorizeNetClient;
    private final SubscriptionRepository subscriptionRepository;
    private final AuditService auditService;
    private final PaymentEventQueue eventQueue;

    public SubscriptionService(AuthorizeNetClient authorizeNetClient,
                               SubscriptionRepository subscriptionRepository,
                               AuditService auditService,
                               PaymentEventQueue eventQueue) {
        this.authorizeNetClient = authorizeNetClient;
        this.subscriptionRepository = subscriptionRepository;
        this.auditService = auditService;
        this.eventQueue = eventQueue;
    }

    /**
     * Create a new recurring billing subscription.
     */
    @Transactional
    public Subscription createSubscription(String name, BigDecimal amount,
                                            int intervalLength, String intervalUnit,
                                            String startDate, Map<String, String> card) {
        log.info("Creating subscription: name={}, amount={}, interval={} {}",
                name, amount, intervalLength, intervalUnit);

        // Call Authorize.Net ARB API
        Map<String, Object> resp = authorizeNetClient.createSubscription(
                name, amount, intervalLength, intervalUnit, startDate, card);

        String status = (String) resp.getOrDefault("status", "failed");

        Subscription subscription = new Subscription();
        subscription.setName(name);
        subscription.setAmount(amount);
        subscription.setIntervalLength(intervalLength);
        subscription.setIntervalUnit(intervalUnit);
        subscription.setStartDate(LocalDate.parse(startDate));

        if ("success".equals(status)) {
            subscription.setGatewaySubscriptionId(String.valueOf(resp.get("subscription_id")));
            subscription.setStatus("active");
            // Calculate initial next billing date
            subscription.setNextBillingDate(subscription.getStartDate()
                    .plusDays("days".equalsIgnoreCase(intervalUnit) ? intervalLength : 0)
                    .plusMonths("months".equalsIgnoreCase(intervalUnit) ? intervalLength : 0));
            // Store last 4 digits of card
            String cardNumber = card.getOrDefault("number", "");
            if (cardNumber.length() >= 4) {
                subscription.setCardLast4(cardNumber.substring(cardNumber.length() - 4));
            }
        } else {
            subscription.setStatus("failed");
        }

        subscription = subscriptionRepository.save(subscription);

        // Publish event
        eventQueue.publish(PaymentEvent.of(PaymentEvent.SUBSCRIPTION_CREATED,
                null, subscription.getGatewaySubscriptionId(),
                Map.of("subscriptionId", subscription.getId(), "status", subscription.getStatus())));

        log.info("Subscription created: id={}, gatewayId={}, status={}",
                subscription.getId(), subscription.getGatewaySubscriptionId(), subscription.getStatus());

        return subscription;
    }

    /**
     * Get subscription by local ID.
     */
    @Transactional(readOnly = true)
    public Optional<Subscription> getSubscription(Long id) {
        return subscriptionRepository.findById(id);
    }

    /**
     * Get subscription by gateway subscription ID.
     */
    @Transactional(readOnly = true)
    public Optional<Subscription> getSubscriptionByGatewayId(String gatewayId) {
        return subscriptionRepository.findByGatewaySubscriptionId(gatewayId);
    }

    /**
     * Update a subscription (name and/or amount).
     */
    @Transactional
    public Optional<Subscription> updateSubscription(Long id, String name, BigDecimal amount) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) return Optional.empty();

        Subscription subscription = opt.get();

        if (subscription.getGatewaySubscriptionId() != null) {
            Map<String, Object> resp = authorizeNetClient.updateSubscription(
                    subscription.getGatewaySubscriptionId(), name, amount);

            if (!"success".equals(resp.getOrDefault("status", "failed"))) {
                log.warn("Failed to update subscription at gateway: id={}", id);
                return Optional.empty();
            }
        }

        if (name != null) subscription.setName(name);
        if (amount != null) subscription.setAmount(amount);
        subscription.setUpdatedAt(Instant.now());

        subscriptionRepository.save(subscription);
        log.info("Subscription updated: id={}", id);

        return Optional.of(subscription);
    }

    /**
     * Cancel a subscription.
     */
    @Transactional
    public Optional<Subscription> cancelSubscription(Long id) {
        Optional<Subscription> opt = subscriptionRepository.findById(id);
        if (opt.isEmpty()) return Optional.empty();

        Subscription subscription = opt.get();

        if (subscription.getGatewaySubscriptionId() != null) {
            Map<String, Object> resp = authorizeNetClient.cancelSubscription(
                    subscription.getGatewaySubscriptionId());

            if (!"success".equals(resp.getOrDefault("status", "failed"))) {
                log.warn("Failed to cancel subscription at gateway: id={}", id);
                return Optional.empty();
            }
        }

        subscription.setStatus("cancelled");
        subscription.setCancelledAt(Instant.now());
        subscription.setUpdatedAt(Instant.now());
        subscriptionRepository.save(subscription);

        // Publish event
        eventQueue.publish(PaymentEvent.of(PaymentEvent.SUBSCRIPTION_CANCELLED,
                null, subscription.getGatewaySubscriptionId()));

        log.info("Subscription cancelled: id={}", id);
        return Optional.of(subscription);
    }
}

