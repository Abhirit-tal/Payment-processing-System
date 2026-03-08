package com.example.payment.service;

import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Scheduled service to retry pending/error transactions.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> What happens when a payment is left in PENDING state?</p>
 * <p><strong>A:</strong> This can happen if the gateway times out or returns a transient error
 * after the SDK call. We need a background job to detect stale PENDING orders and either
 * retry the gateway call or mark them as failed. This is separate from the @Retryable
 * mechanism which retries immediately within the same HTTP request.</p>
 *
 * <p><strong>Q:</strong> How often should we retry? When do we give up?</p>
 * <p><strong>A:</strong> Run every 5 minutes. Only retry orders that have been pending for
 * at least 2 minutes (to avoid conflicting with in-flight @Retryable attempts). After 3
 * background retries (tracked via a retry_count field), mark the order as ERROR and stop.</p>
 *
 * <p><strong>Q:</strong> What about ERROR state orders?</p>
 * <p><strong>A:</strong> ERROR state with retryable=true can also be retried. After max retries,
 * they stay in ERROR state and require manual investigation.</p>
 */
@Component
public class PendingTransactionRetryService {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionRetryService.class);
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int STALE_THRESHOLD_MINUTES = 2;

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final AuthorizeNetClient authorizeNetClient;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    private Counter retryAttemptCounter;
    private Counter retrySuccessCounter;
    private Counter retryExhaustedCounter;

    public PendingTransactionRetryService(OrderRepository orderRepository,
                                           TransactionRepository transactionRepository,
                                           AuthorizeNetClient authorizeNetClient,
                                           AuditService auditService,
                                           MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.authorizeNetClient = authorizeNetClient;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initMetrics() {
        retryAttemptCounter = Counter.builder("payment_retry_attempts_total")
                .description("Total pending transaction retry attempts")
                .register(meterRegistry);
        retrySuccessCounter = Counter.builder("payment_retry_success_total")
                .description("Total successful retries that reconciled with gateway")
                .register(meterRegistry);
        retryExhaustedCounter = Counter.builder("payment_retry_exhausted_total")
                .description("Total retries that exceeded max attempts")
                .register(meterRegistry);
    }

    /**
     * Retry stale PENDING orders. Runs every 5 minutes.
     */
    @Scheduled(fixedDelayString = "${pending.retry.interval-ms:300000}",
               initialDelayString = "${pending.retry.initial-delay-ms:120000}")
    public void retryPendingTransactions() {
        log.info("Scanning for stale pending transactions...");

        Instant cutoff = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        List<Order> pendingOrders = orderRepository.findByStateAndCreatedAtBefore(PaymentState.PENDING, cutoff);

        if (pendingOrders.isEmpty()) {
            log.debug("No stale pending transactions found");
            return;
        }

        log.info("Found {} stale pending orders to retry", pendingOrders.size());

        for (Order order : pendingOrders) {
            retryOrder(order);
        }
    }

    /**
     * Retry ERROR state orders that are still retriable. Runs every 10 minutes.
     */
    @Scheduled(fixedDelayString = "${error.retry.interval-ms:600000}",
               initialDelayString = "${error.retry.initial-delay-ms:180000}")
    public void retryErrorTransactions() {
        log.info("Scanning for retriable error transactions...");

        List<Order> errorOrders = orderRepository.findByState(PaymentState.ERROR);

        int retried = 0;
        for (Order order : errorOrders) {
            if (order.getRetryCount() < MAX_RETRY_ATTEMPTS) {
                retryOrder(order);
                retried++;
            }
        }

        if (retried > 0) {
            log.info("Retried {} error orders", retried);
        }
    }

    @Transactional
    protected void retryOrder(Order order) {
        if (order.getRetryCount() >= MAX_RETRY_ATTEMPTS) {
            log.warn("Order {} has exceeded max retries ({}). Marking as permanent ERROR.",
                    order.getId(), MAX_RETRY_ATTEMPTS);
            if (order.getState() != PaymentState.ERROR) {
                order.setState(PaymentState.ERROR);
                order.setStatus(PaymentState.ERROR.getCode());
            }
            orderRepository.save(order);
            if (retryExhaustedCounter != null) retryExhaustedCounter.increment();
            auditService.logErrorAsync(AuditService.ENTITY_ORDER, order.getId(),
                    "MAX_RETRIES_EXCEEDED",
                    "Order exceeded maximum retry attempts: " + MAX_RETRY_ATTEMPTS);
            return;
        }

        log.info("Retrying order {}, attempt {} of {}",
                order.getId(), order.getRetryCount() + 1, MAX_RETRY_ATTEMPTS);

        order.setRetryCount(order.getRetryCount() + 1);
        orderRepository.save(order);
        if (retryAttemptCounter != null) retryAttemptCounter.increment();

        // Find the latest pending transaction for this order
        List<Transaction> transactions = transactionRepository.findByOrderId(order.getId());
        if (transactions.isEmpty()) {
            log.warn("No transactions found for order {}. Cannot retry.", order.getId());
            return;
        }

        Transaction lastTx = transactions.get(transactions.size() - 1);

        // Log the retry attempt
        auditService.logGatewayCallAsync(order.getId(), "RETRY",
                "attempt=" + order.getRetryCount() + ", type=" + lastTx.getType());

        // Query the gateway for the actual transaction status using provider_tx_id
        if (lastTx.getProviderTxId() != null) {
            log.info("Order {} has provider tx {}, checking status at gateway",
                    order.getId(), lastTx.getProviderTxId());
            try {
                Map<String, Object> details = authorizeNetClient.getTransactionDetails(lastTx.getProviderTxId());

                if ("success".equals(details.get("status"))) {
                    String gatewayStatus = (String) details.get("transaction_status");
                    reconcileOrderWithGateway(order, lastTx, gatewayStatus);
                } else {
                    log.warn("Could not retrieve transaction details for order {}. Will retry later.", order.getId());
                }
            } catch (Exception e) {
                log.error("Error checking gateway status for order {}: {}", order.getId(), e.getMessage());
                // Don't mark as error — will retry on next scheduled run
            }
        } else {
            log.warn("Order {} has no provider tx id. Marking as ERROR after {} retries.",
                    order.getId(), order.getRetryCount());
            order.setState(PaymentState.ERROR);
            order.setStatus(PaymentState.ERROR.getCode());
            orderRepository.save(order);
        }
    }

    /**
     * Reconcile local order state with the actual gateway transaction status.
     *
     * Authorize.Net transaction statuses:
     * - "settledSuccessfully" → CAPTURED
     * - "authorizedPendingCapture" → AUTHORIZED
     * - "capturedPendingSettlement" → CAPTURED
     * - "declined" → DECLINED
     * - "expired" → ERROR (terminal)
     * - "voided" → VOIDED
     * - "refundSettledSuccessfully" → REFUNDED
     * - "FDSPendingReview" / "FDSAuthorizedPendingReview" → HELD_FOR_REVIEW
     */
    private void reconcileOrderWithGateway(Order order, Transaction lastTx, String gatewayStatus) {
        if (gatewayStatus == null) {
            log.warn("Gateway returned null status for order {}. Skipping reconciliation.", order.getId());
            return;
        }

        PaymentState resolvedState;
        switch (gatewayStatus.toLowerCase()) {
            case "settledsuccessfully":
            case "capturedpendingsettlement":
                resolvedState = PaymentState.CAPTURED;
                break;
            case "authorizedpendingcapture":
                resolvedState = PaymentState.AUTHORIZED;
                break;
            case "declined":
                resolvedState = PaymentState.DECLINED;
                break;
            case "voided":
                resolvedState = PaymentState.VOIDED;
                break;
            case "refundsettledsuccessfully":
            case "refundpendingsettlement":
                resolvedState = PaymentState.REFUNDED;
                break;
            case "fdspendreview":
            case "fdsauthorizedpendingreview":
                resolvedState = PaymentState.HELD_FOR_REVIEW;
                break;
            case "expired":
            case "generalerror":
            case "communicationerror":
                resolvedState = PaymentState.ERROR;
                break;
            default:
                log.warn("Unknown gateway status '{}' for order {}. Keeping current state.", gatewayStatus, order.getId());
                return;
        }

        PaymentState oldState = order.getState();
        log.info("Reconciling order {}: local={} -> gateway={} (resolved={})",
                order.getId(), oldState, gatewayStatus, resolvedState);

        // Update local state to match gateway
        order.setState(resolvedState);
        order.setStatus(resolvedState.getCode());
        order.setPreviousState(oldState);
        order.setStateChangedAt(Instant.now());
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        // Update transaction status
        lastTx.setStatus(resolvedState.isFailure() ? "failed" : "success");
        transactionRepository.save(lastTx);

        auditService.logGatewayResponseAsync(order.getId(), "RETRY_RECONCILE",
                !resolvedState.isFailure(), gatewayStatus);

        if (!resolvedState.isFailure() && retrySuccessCounter != null) {
            retrySuccessCounter.increment();
        }

        log.info("Order {} reconciled: {} -> {}", order.getId(), oldState, resolvedState);
    }
}

