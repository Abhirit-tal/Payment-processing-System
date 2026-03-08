package com.example.payment.controller;

import com.example.payment.dto.PaymentRequests;
import com.example.payment.model.IdempotencyKey;
import com.example.payment.model.Transaction;
import com.example.payment.service.IdempotencyService;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    public PaymentController(PaymentService paymentService,
                             IdempotencyService idempotencyService,
                             ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/purchase")
    @RateLimiter(name = "paymentApi")
    @Operation(summary = "Purchase (authorize + capture)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = "{\"order_id\":100,\"transaction_id\":\"prov-123\",\"status\":\"success\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation error"),
            @ApiResponse(responseCode = "502", description = "Gateway error"),
            @ApiResponse(responseCode = "504", description = "Gateway timeout")
    })
    public ResponseEntity<?> purchase(
            @Valid @RequestBody PaymentRequests.PurchaseRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(description = "Idempotency key for safe retries") String idempotencyKey,
            HttpServletRequest httpRequest) {

        // Check idempotency
        ResponseEntity<?> cachedResponse = checkIdempotency(idempotencyKey, req, httpRequest);
        if (cachedResponse != null) return cachedResponse;

        IdempotencyKey idemKey = lockIdempotency(idempotencyKey, req, httpRequest);

        try {
            log.debug("Purchase endpoint called, authentication={}", SecurityContextHolder.getContext().getAuthentication());
            Map<String, String> card = req.getCard().toGatewayMap();
            Transaction tx = paymentService.purchase(req.getAmount(), req.getCurrency(), card, req.getOrderId());
            if (tx == null) {
                log.warn("Purchase failed: paymentService returned null transaction for orderId={}", req.getOrderId());
                Map<String, Object> err = new HashMap<>();
                err.put("detail", "payment provider error");
                completeIdempotency(idemKey, err, 502, null);
                return ResponseEntity.status(502).body(err);
            }
            if (tx.getOrder() == null) {
                log.warn("Purchase failed: transaction has no associated order (tx={})", tx);
                Map<String, Object> err = new HashMap<>();
                err.put("detail", "internal persistence error");
                completeIdempotency(idemKey, err, 502, null);
                return ResponseEntity.status(502).body(err);
            }
            Map<String, Object> resp = new HashMap<>();
            resp.put("order_id", tx.getOrder().getId());
            resp.put("transaction_id", tx.getProviderTxId());
            resp.put("status", tx.getStatus());
            completeIdempotency(idemKey, resp, 201, tx.getOrder().getId());
            return ResponseEntity.status(201).body(resp);
        } catch (Exception e) {
            releaseIdempotency(idemKey);
            throw e;
        }
    }

    @PostMapping("/authorize")
    @RateLimiter(name = "paymentApi")
    @Operation(summary = "Authorize only (two-step)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> authorize(
            @Valid @RequestBody PaymentRequests.AuthorizeRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        ResponseEntity<?> cachedResponse = checkIdempotency(idempotencyKey, req, httpRequest);
        if (cachedResponse != null) return cachedResponse;

        IdempotencyKey idemKey = lockIdempotency(idempotencyKey, req, httpRequest);

        try {
            Map<String, String> card = req.getCard().toGatewayMap();
            Transaction tx = paymentService.authorizeOnly(req.getAmount(), req.getCurrency(), card, req.getOrderId());
            Map<String, Object> resp = new HashMap<>();
            resp.put("order_id", tx.getOrder().getId());
            resp.put("transaction_id", tx.getProviderTxId());
            resp.put("status", tx.getStatus());
            completeIdempotency(idemKey, resp, 201, tx.getOrder().getId());
            return ResponseEntity.status(201).body(resp);
        } catch (Exception e) {
            releaseIdempotency(idemKey);
            throw e;
        }
    }

    @PostMapping("/capture")
    @RateLimiter(name = "paymentApi")
    @Operation(summary = "Capture an authorized transaction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> capture(
            @Valid @RequestBody PaymentRequests.CaptureRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        ResponseEntity<?> cachedResponse = checkIdempotency(idempotencyKey, req, httpRequest);
        if (cachedResponse != null) return cachedResponse;

        IdempotencyKey idemKey = lockIdempotency(idempotencyKey, req, httpRequest);

        try {
            var opt = paymentService.capture(req.getTransactionId(), req.getAmount());
            if (opt.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("detail", "transaction not found");
                completeIdempotency(idemKey, err, 404, null);
                return ResponseEntity.status(404).body(err);
            }
            Transaction tx = opt.get();
            Map<String, Object> resp = new HashMap<>();
            resp.put("transaction_id", tx.getProviderTxId());
            resp.put("status", tx.getStatus());
            completeIdempotency(idemKey, resp, 200, tx.getOrder() != null ? tx.getOrder().getId() : null);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            releaseIdempotency(idemKey);
            throw e;
        }
    }

    @PostMapping("/cancel")
    @RateLimiter(name = "paymentApi")
    @Operation(summary = "Cancel (void) an authorized transaction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> cancel(
            @Valid @RequestBody PaymentRequests.CancelRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        ResponseEntity<?> cachedResponse = checkIdempotency(idempotencyKey, req, httpRequest);
        if (cachedResponse != null) return cachedResponse;

        IdempotencyKey idemKey = lockIdempotency(idempotencyKey, req, httpRequest);

        try {
            var opt = paymentService.voidTransaction(req.getTransactionId());
            if (opt.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("detail", "transaction not found");
                completeIdempotency(idemKey, err, 404, null);
                return ResponseEntity.status(404).body(err);
            }
            Transaction tx = opt.get();
            Map<String, Object> resp = new HashMap<>();
            resp.put("transaction_id", tx.getProviderTxId());
            resp.put("status", tx.getStatus());
            completeIdempotency(idemKey, resp, 200, tx.getOrder() != null ? tx.getOrder().getId() : null);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            releaseIdempotency(idemKey);
            throw e;
        }
    }

    @PostMapping("/refund")
    @RateLimiter(name = "paymentApi")
    @Operation(summary = "Refund (full or partial)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> refund(
            @Valid @RequestBody PaymentRequests.RefundRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest httpRequest) {

        ResponseEntity<?> cachedResponse = checkIdempotency(idempotencyKey, req, httpRequest);
        if (cachedResponse != null) return cachedResponse;

        IdempotencyKey idemKey = lockIdempotency(idempotencyKey, req, httpRequest);

        try {
            var opt = paymentService.refund(req.getTransactionId(), req.getAmount(), req.getLast4());
            if (opt.isEmpty()) {
                Map<String, Object> err = new HashMap<>();
                err.put("detail", "original transaction not found");
                completeIdempotency(idemKey, err, 404, null);
                return ResponseEntity.status(404).body(err);
            }
            Transaction tx = opt.get();
            Map<String, Object> resp = new HashMap<>();
            resp.put("refund_transaction_id", tx.getProviderTxId());
            resp.put("status", tx.getStatus());
            completeIdempotency(idemKey, resp, 200, tx.getOrder() != null ? tx.getOrder().getId() : null);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            releaseIdempotency(idemKey);
            throw e;
        }
    }

    // ==================== Idempotency Helpers ====================

    private ResponseEntity<?> checkIdempotency(String key, Object requestBody, HttpServletRequest httpRequest) {
        if (key == null || key.isBlank()) return null;
        try {
            Optional<IdempotencyService.CachedResponse> cached = idempotencyService.checkIdempotency(
                    key, requestBody, httpRequest.getRequestURI(), httpRequest.getMethod());
            if (cached.isPresent()) {
                IdempotencyService.CachedResponse cr = cached.get();
                Object body = objectMapper.readValue(cr.body(), Object.class);
                return ResponseEntity.status(cr.status()).body(body);
            }
        } catch (Exception e) {
            log.warn("Idempotency check failed: {}", e.getMessage());
        }
        return null;
    }

    private IdempotencyKey lockIdempotency(String key, Object requestBody, HttpServletRequest httpRequest) {
        if (key == null || key.isBlank()) return null;
        try {
            return idempotencyService.createAndLock(key, requestBody,
                    httpRequest.getRequestURI(), httpRequest.getMethod());
        } catch (Exception e) {
            log.warn("Idempotency lock failed: {}", e.getMessage());
            return null;
        }
    }

    private void completeIdempotency(IdempotencyKey idemKey, Object responseBody, int status, Long orderId) {
        if (idemKey != null) {
            try {
                idempotencyService.complete(idemKey, responseBody, status, orderId);
            } catch (Exception e) {
                log.warn("Idempotency complete failed: {}", e.getMessage());
            }
        }
    }

    private void releaseIdempotency(IdempotencyKey idemKey) {
        if (idemKey != null) {
            try {
                idempotencyService.release(idemKey);
            } catch (Exception e) {
                log.warn("Idempotency release failed: {}", e.getMessage());
            }
        }
    }
}
