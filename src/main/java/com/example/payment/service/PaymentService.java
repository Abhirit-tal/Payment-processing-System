package com.example.payment.service;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventQueue;
import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.GatewayResponseType;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Payment Service - Core business logic for payment operations.
 *
 * <p>This service orchestrates payment flows including:</p>
 * <ul>
 *   <li>Purchase (auth + capture in one step)</li>
 *   <li>Authorize only (two-step flow)</li>
 *   <li>Capture (following authorization)</li>
 *   <li>Void/Cancel (before capture)</li>
 *   <li>Refund (full or partial)</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should state transitions be handled here or in a separate service?</p>
 * <p><strong>A:</strong> State validation is delegated to PaymentStateMachine for single
 * responsibility. This service coordinates the flow, while the state machine enforces
 * valid transitions. This allows reuse of state logic and easier testing.</p>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AuthorizeNetClient authorizeNetClient;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;
    private final PaymentStateMachine stateMachine;
    private final AuditService auditService;
    private final PaymentEventQueue eventQueue;

    public PaymentService(AuthorizeNetClient authorizeNetClient,
                         OrderRepository orderRepository,
                         TransactionRepository transactionRepository,
                         PaymentStateMachine stateMachine,
                         AuditService auditService,
                         PaymentEventQueue eventQueue) {
        this.authorizeNetClient = authorizeNetClient;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
        this.stateMachine = stateMachine;
        this.auditService = auditService;
        this.eventQueue = eventQueue;
    }

    @Transactional
    public Transaction purchase(BigDecimal amount, String currency, Map<String, String> card, String externalOrderId) {
        // Create order in CREATED state
        Order order = createOrder(amount, currency, externalOrderId, null);

        // Transition to PENDING
        transitionOrderState(order, PaymentState.PENDING);

        // Create pending transaction
        Transaction tx = createTransaction(order, "purchase", amount);

        // Call gateway
        auditService.logGatewayCallAsync(order.getId(), "AUTH_CAPTURE", "amount=" + amount);
        Map<String, Object> resp = authorizeNetClient.createTransaction(amount, currency, card, true);

        // Process response
        return processGatewayResponse(order, tx, resp, true);
    }

    @Transactional
    public Transaction authorizeOnly(BigDecimal amount, String currency, Map<String, String> card, String externalOrderId) {
        // Create order in CREATED state
        Order order = createOrder(amount, currency, externalOrderId, null);

        // Transition to PENDING
        transitionOrderState(order, PaymentState.PENDING);

        // Create pending transaction
        Transaction tx = createTransaction(order, "authorize", amount);

        // Call gateway
        auditService.logGatewayCallAsync(order.getId(), "AUTH_ONLY", "amount=" + amount);
        Map<String, Object> resp = authorizeNetClient.createTransaction(amount, currency, card, false);

        // Process response
        return processGatewayResponse(order, tx, resp, false);
    }

    @Transactional
    public Optional<Transaction> capture(String providerAuthTxId, BigDecimal amount) {
        Optional<Transaction> authTxOpt = transactionRepository.findByProviderTxId(providerAuthTxId);
        if (authTxOpt.isEmpty()) {
            log.warn("Capture failed: Authorization transaction not found: {}", providerAuthTxId);
            return Optional.empty();
        }

        Transaction authTx = authTxOpt.get();
        Order order = authTx.getOrder();

        // Validate state transition
        if (!order.getState().isCapturable()) {
            log.warn("Capture failed: Order {} is not in capturable state ({})",
                    order.getId(), order.getState());
            throw new InvalidStateTransitionException(order.getState(), PaymentState.CAPTURED,
                    order.getId(), "Order is not in a capturable state");
        }

        BigDecimal captureAmount = amount != null ? amount : authTx.getAmount();

        // Create capture transaction
        Transaction captureTx = createTransaction(order, "capture", captureAmount);

        // Call gateway
        auditService.logGatewayCallAsync(order.getId(), "CAPTURE", "amount=" + captureAmount);
        Map<String, Object> resp = authorizeNetClient.captureTransaction(providerAuthTxId, captureAmount);

        // Process response
        String status = (String) resp.getOrDefault("status", "failed");
        captureTx.setProviderTxId((String) resp.get("provider_tx_id"));
        captureTx.setRawResponse(sanitizeRawResponse(resp.get("raw")));
        captureTx.setStatus(status);

        if ("success".equals(status)) {
            transitionOrderState(order, PaymentState.CAPTURED);
            auditService.logTransactionCreated(captureTx, AuditService.ACTION_CAPTURE);
        }
        // Note: On capture failure, we keep the order in AUTHORIZED state
        // so the user can retry with correct amount. The authorization is still valid.

        transactionRepository.save(captureTx);
        auditService.logGatewayResponseAsync(order.getId(), "CAPTURE", "success".equals(status), status);

        if ("success".equals(status)) {
            eventQueue.publish(PaymentEvent.of(PaymentEvent.CAPTURE_SUCCESS, order.getId(), captureTx.getProviderTxId()));
        }

        return Optional.of(captureTx);
    }

    @Transactional
    public Optional<Transaction> voidTransaction(String providerTxId) {
        Optional<Transaction> txOpt = transactionRepository.findByProviderTxId(providerTxId);
        if (txOpt.isEmpty()) {
            log.warn("Void failed: Transaction not found: {}", providerTxId);
            return Optional.empty();
        }

        Transaction tx = txOpt.get();
        Order order = tx.getOrder();

        // Validate state transition
        if (!order.getState().isVoidable()) {
            log.warn("Void failed: Order {} is not in voidable state ({})",
                    order.getId(), order.getState());
            throw new InvalidStateTransitionException(order.getState(), PaymentState.VOIDED,
                    order.getId(), "Order is not in a voidable state");
        }

        // Call gateway
        auditService.logGatewayCallAsync(order.getId(), "VOID", "tx=" + providerTxId);
        Map<String, Object> resp = authorizeNetClient.voidTransaction(providerTxId);

        String status = (String) resp.getOrDefault("status", "failed");
        tx.setStatus(status);
        tx.setRawResponse(sanitizeRawResponse(resp.get("raw")));

        if ("success".equals(status)) {
            transitionOrderState(order, PaymentState.VOIDED);
            auditService.logTransactionCreated(tx, AuditService.ACTION_VOID);
        }

        transactionRepository.save(tx);
        auditService.logGatewayResponseAsync(order.getId(), "VOID", "success".equals(status), status);

        if ("success".equals(status)) {
            eventQueue.publish(PaymentEvent.of(PaymentEvent.VOID_SUCCESS, order.getId(), tx.getProviderTxId()));
        }

        return Optional.of(tx);
    }

    @Transactional
    public Optional<Transaction> refund(String providerCapturedTxId, BigDecimal amount, String last4) {
        Optional<Transaction> capturedOpt = transactionRepository.findByProviderTxId(providerCapturedTxId);
        if (capturedOpt.isEmpty()) {
            log.warn("Refund failed: Original transaction not found: {}", providerCapturedTxId);
            return Optional.empty();
        }

        Transaction orig = capturedOpt.get();
        Order order = orig.getOrder();

        // Validate state transition
        if (!order.getState().isRefundable()) {
            log.warn("Refund failed: Order {} is not in refundable state ({})",
                    order.getId(), order.getState());
            throw new InvalidStateTransitionException(order.getState(), PaymentState.REFUNDED,
                    order.getId(), "Order is not in a refundable state");
        }

        BigDecimal refundAmount = amount != null ? amount : orig.getAmount();
        boolean isPartialRefund = refundAmount.compareTo(orig.getAmount()) < 0;

        // Create refund transaction
        Transaction refundTx = createTransaction(order, "refund", refundAmount);

        // Call gateway
        auditService.logGatewayCallAsync(order.getId(), "REFUND", "amount=" + refundAmount);
        Map<String, Object> resp = authorizeNetClient.refundTransaction(providerCapturedTxId, refundAmount, last4);

        String status = (String) resp.getOrDefault("status", "failed");
        refundTx.setProviderTxId((String) resp.get("provider_tx_id"));
        refundTx.setRawResponse(sanitizeRawResponse(resp.get("raw")));
        refundTx.setStatus(status);

        if ("success".equals(status)) {
            PaymentState newState = isPartialRefund ? PaymentState.PARTIALLY_REFUNDED : PaymentState.REFUNDED;
            transitionOrderState(order, newState);
            auditService.logTransactionCreated(refundTx, AuditService.ACTION_REFUND);
        }

        transactionRepository.save(refundTx);
        auditService.logGatewayResponseAsync(order.getId(), "REFUND", "success".equals(status), status);

        if ("success".equals(status)) {
            eventQueue.publish(PaymentEvent.of(PaymentEvent.REFUND_SUCCESS, order.getId(), refundTx.getProviderTxId()));
        }

        return Optional.of(refundTx);
    }

    // ==================== Helper Methods ====================

    /**
     * Create a new order with initial state.
     */
    private Order createOrder(BigDecimal amount, String currency, String externalOrderId, String idempotencyKey) {
        Order order = new Order();
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setExternalId(externalOrderId);
        order.setState(PaymentState.CREATED);
        order.setStatus(PaymentState.CREATED.getCode()); // Legacy field
        order.setIdempotencyKey(idempotencyKey);
        order = orderRepository.save(order);

        auditService.logOrderCreated(order);
        log.info("Created order {} with amount {} {}", order.getId(), amount, currency);

        return order;
    }

    /**
     * Create a new transaction.
     */
    private Transaction createTransaction(Order order, String type, BigDecimal amount) {
        Transaction tx = new Transaction();
        tx.setOrder(order);
        tx.setType(type);
        tx.setAmount(amount);
        tx.setStatus("pending");
        tx = transactionRepository.save(tx);

        log.debug("Created transaction {} of type {} for order {}", tx.getId(), type, order.getId());
        return tx;
    }

    /**
     * Transition order to a new state with validation and audit logging.
     */
    private void transitionOrderState(Order order, PaymentState newState) {
        PaymentState oldState = order.getState();

        // Validate transition
        stateMachine.validateTransition(oldState, newState, order.getId());

        // Perform transition
        order.transitionTo(newState);
        orderRepository.save(order);

        // Audit log
        auditService.logStateTransition(order, oldState, newState);
        log.info("Order {} transitioned from {} to {}", order.getId(), oldState, newState);
    }

    /**
     * Process gateway response and update transaction/order state.
     */
    private Transaction processGatewayResponse(Order order, Transaction tx,
                                               Map<String, Object> resp, boolean isCapture) {
        String status = (String) resp.getOrDefault("status", "failed");
        String responseCode = (String) resp.get("response_code");

        tx.setProviderTxId((String) resp.get("provider_tx_id"));
        tx.setRawResponse(sanitizeRawResponse(resp.get("raw")));

        // Map gateway response to state
        GatewayResponseType responseType = "success".equals(status)
                ? GatewayResponseType.APPROVED
                : GatewayResponseType.fromCode(responseCode);

        PaymentState newState = responseType.toPaymentState(isCapture);

        if ("success".equals(status)) {
            tx.setStatus("success");
            transitionOrderState(order, newState);
            auditService.logTransactionCreated(tx, isCapture ? AuditService.ACTION_PURCHASE : AuditService.ACTION_AUTHORIZE);
        } else {
            tx.setStatus("failed");
            transitionOrderState(order, newState);
            auditService.logErrorAsync(AuditService.ENTITY_ORDER, order.getId(),
                    responseType.getName(), "Gateway returned: " + status);
        }

        transactionRepository.save(tx);
        auditService.logGatewayResponseAsync(order.getId(),
                isCapture ? "AUTH_CAPTURE" : "AUTH_ONLY",
                "success".equals(status), status);

        // Publish event for async processing
        String eventType = "success".equals(status)
                ? (isCapture ? PaymentEvent.PURCHASE_SUCCESS : PaymentEvent.AUTHORIZE_SUCCESS)
                : PaymentEvent.PURCHASE_FAILED;
        eventQueue.publish(PaymentEvent.of(eventType, order.getId(), tx.getProviderTxId()));

        return tx;
    }

    /**
     * Sanitize raw response to remove sensitive card data before storage.
     */
    private String sanitizeRawResponse(Object raw) {
        if (raw == null) return null;
        String str = raw.toString();
        // Mask card numbers
        str = str.replaceAll("\\b\\d{13,16}\\b", "****");
        // Mask CVV
        str = str.replaceAll("(?i)(cardCode|cvv)[\":\\s]+\\d{3,4}", "$1\":\"***\"");
        return str;
    }
}

