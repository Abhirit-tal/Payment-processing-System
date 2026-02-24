package com.example.payment.service;

import com.example.payment.model.AuditLog;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.AuditLogRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for recording audit logs of payment operations.
 *
 * <p>This service provides comprehensive audit logging for:</p>
 * <ul>
 *   <li>Payment state transitions</li>
 *   <li>Transaction creation and updates</li>
 *   <li>Sensitive operations (refunds, voids, captures)</li>
 *   <li>Error events</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should audit logging be synchronous or asynchronous?</p>
 * <p><strong>A:</strong> Async for most cases to avoid impacting payment latency, but
 * critical compliance events (like state changes) use sync to ensure capture before
 * transaction commits. We use @Async for non-critical audit events.</p>
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // Entity type constants
    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_TRANSACTION = "TRANSACTION";

    // Action constants
    public static final String ACTION_STATE_CHANGE = "STATE_CHANGE";
    public static final String ACTION_CREATED = "CREATED";
    public static final String ACTION_UPDATED = "UPDATED";
    public static final String ACTION_PURCHASE = "PURCHASE";
    public static final String ACTION_AUTHORIZE = "AUTHORIZE";
    public static final String ACTION_CAPTURE = "CAPTURE";
    public static final String ACTION_VOID = "VOID";
    public static final String ACTION_REFUND = "REFUND";
    public static final String ACTION_GATEWAY_CALL = "GATEWAY_CALL";
    public static final String ACTION_GATEWAY_RESPONSE = "GATEWAY_RESPONSE";
    public static final String ACTION_ERROR = "ERROR";

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Log a state transition for an order (synchronous for transaction integrity).
     */
    public void logStateTransition(Order order, PaymentState fromState, PaymentState toState) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(ENTITY_ORDER)
                    .entityId(order.getId())
                    .action(ACTION_STATE_CHANGE)
                    .actor(getCurrentActor())
                    .actorIp(getClientIp())
                    .oldValue(fromState != null ? fromState.getCode() : null)
                    .newValue(toState.getCode())
                    .metadata(buildOrderMetadata(order))
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit: State transition {} -> {} for order {}", fromState, toState, order.getId());
        } catch (Exception e) {
            log.error("Failed to log state transition audit for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Log order creation.
     */
    public void logOrderCreated(Order order) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(ENTITY_ORDER)
                    .entityId(order.getId())
                    .action(ACTION_CREATED)
                    .actor(getCurrentActor())
                    .actorIp(getClientIp())
                    .newValue(order.getState().getCode())
                    .metadata(buildOrderMetadata(order))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log order creation audit: {}", e.getMessage());
        }
    }

    /**
     * Log transaction creation.
     */
    public void logTransactionCreated(Transaction transaction, String action) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(ENTITY_TRANSACTION)
                    .entityId(transaction.getId())
                    .action(action)
                    .actor(getCurrentActor())
                    .actorIp(getClientIp())
                    .newValue(transaction.getStatus())
                    .metadata(buildTransactionMetadata(transaction))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log transaction audit: {}", e.getMessage());
        }
    }

    /**
     * Log gateway call (async to not impact latency).
     */
    @Async
    public void logGatewayCallAsync(Long orderId, String operation, String requestSummary) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .entityType(ENTITY_ORDER)
                    .entityId(orderId)
                    .action(ACTION_GATEWAY_CALL)
                    .actor(getCurrentActor())
                    .metadata(String.format("{\"operation\":\"%s\",\"request\":\"%s\"}",
                            operation, sanitize(requestSummary)))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log gateway call audit: {}", e.getMessage());
        }
    }

    /**
     * Log gateway response (async).
     */
    @Async
    public void logGatewayResponseAsync(Long orderId, String operation, boolean success, String responseSummary) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("operation", operation);
            metadata.put("success", success);
            metadata.put("response", sanitize(responseSummary));

            AuditLog auditLog = AuditLog.builder()
                    .entityType(ENTITY_ORDER)
                    .entityId(orderId)
                    .action(ACTION_GATEWAY_RESPONSE)
                    .actor(getCurrentActor())
                    .metadata(toJson(metadata))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log gateway response audit: {}", e.getMessage());
        }
    }

    /**
     * Log an error event.
     */
    @Async
    public void logErrorAsync(String entityType, Long entityId, String errorCode, String errorMessage) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("error_code", errorCode);
            metadata.put("error_message", errorMessage);

            AuditLog auditLog = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .action(ACTION_ERROR)
                    .actor(getCurrentActor())
                    .actorIp(getClientIp())
                    .metadata(toJson(metadata))
                    .build();

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to log error audit: {}", e.getMessage());
        }
    }

    /**
     * Retrieve audit history for an order.
     */
    public List<AuditLog> getOrderAuditHistory(Long orderId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(ENTITY_ORDER, orderId);
    }

    /**
     * Retrieve audit history for a transaction.
     */
    public List<AuditLog> getTransactionAuditHistory(Long transactionId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(ENTITY_TRANSACTION, transactionId);
    }

    // Helper methods

    private String getCurrentActor() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return auth.getName();
            }
        } catch (Exception e) {
            // Ignore - may not be in request context
        }
        return "SYSTEM";
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String xForwardedFor = request.getHeader("X-Forwarded-For");
                if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                    return xForwardedFor.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore - may not be in request context
        }
        return null;
    }

    private String buildOrderMetadata(Order order) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("external_id", order.getExternalId());
        meta.put("amount", order.getAmount());
        meta.put("currency", order.getCurrency());
        return toJson(meta);
    }

    private String buildTransactionMetadata(Transaction transaction) {
        Map<String, Object> meta = new HashMap<>();
        meta.put("order_id", transaction.getOrder() != null ? transaction.getOrder().getId() : null);
        meta.put("type", transaction.getType());
        meta.put("amount", transaction.getAmount());
        meta.put("provider_tx_id", transaction.getProviderTxId());
        return toJson(meta);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Sanitize sensitive data from audit logs.
     * Removes/masks card numbers, CVVs, etc.
     */
    private String sanitize(String input) {
        if (input == null) return null;
        // Mask card numbers (16 digits)
        String sanitized = input.replaceAll("\\b\\d{13,16}\\b", "****");
        // Mask CVV (3-4 digits following "cvv" keyword)
        sanitized = sanitized.replaceAll("(?i)(cvv[\":\\s]+)\\d{3,4}", "$1***");
        return sanitized;
    }
}

