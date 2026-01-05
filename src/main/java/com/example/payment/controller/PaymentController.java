package com.example.payment.controller;

import com.example.payment.dto.PaymentRequests;
import com.example.payment.model.Transaction;
import com.example.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping("/purchase")
    @Operation(summary = "Purchase (authorize + capture)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Purchase created",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class),
                            examples = @ExampleObject(value = "{\"order_id\":100,\"transaction_id\":\"prov-123\",\"status\":\"success\"}"))),
            @ApiResponse(responseCode = "400", description = "Validation error")
    })
    public ResponseEntity<?> purchase(@Valid @RequestBody PaymentRequests.PurchaseRequest req) {
        Map<String, String> card = new HashMap<>();
        card.put("number", req.getCard().getNumber());
        card.put("expMonth", String.valueOf(req.getCard().getExpMonth()));
        card.put("expYear", String.valueOf(req.getCard().getExpYear()));
        card.put("cvv", req.getCard().getCvv());
        Transaction tx = paymentService.purchase(req.getAmount(), req.getCurrency(), card, req.getOrderId());
        return ResponseEntity.status(201).body(Map.of(
                "order_id", tx.getOrder().getId(),
                "transaction_id", tx.getProviderTxId(),
                "status", tx.getStatus()
        ));
    }

    @PostMapping("/authorize")
    @Operation(summary = "Authorize only (two-step)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> authorize(@Valid @RequestBody PaymentRequests.AuthorizeRequest req) {
        Map<String, String> card = new HashMap<>();
        card.put("number", req.getCard().getNumber());
        card.put("expMonth", String.valueOf(req.getCard().getExpMonth()));
        card.put("expYear", String.valueOf(req.getCard().getExpYear()));
        card.put("cvv", req.getCard().getCvv());
        Transaction tx = paymentService.authorizeOnly(req.getAmount(), req.getCurrency(), card, req.getOrderId());
        return ResponseEntity.status(201).body(Map.of(
                "order_id", tx.getOrder().getId(),
                "transaction_id", tx.getProviderTxId(),
                "status", tx.getStatus()
        ));
    }

    @PostMapping("/capture")
    @Operation(summary = "Capture an authorized transaction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> capture(@Valid @RequestBody PaymentRequests.CaptureRequest req) {
        var opt = paymentService.capture(req.getTransactionId(), req.getAmount());
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("detail", "transaction not found"));
        Transaction tx = opt.get();
        return ResponseEntity.ok(Map.of("transaction_id", tx.getProviderTxId(), "status", tx.getStatus()));
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel (void) an authorized transaction", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> cancel(@Valid @RequestBody PaymentRequests.CancelRequest req) {
        var opt = paymentService.voidTransaction(req.getTransactionId());
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("detail", "transaction not found"));
        Transaction tx = opt.get();
        return ResponseEntity.ok(Map.of("transaction_id", tx.getProviderTxId(), "status", tx.getStatus()));
    }

    @PostMapping("/refund")
    @Operation(summary = "Refund (full or partial)", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> refund(@Valid @RequestBody PaymentRequests.RefundRequest req) {
        var opt = paymentService.refund(req.getTransactionId(), req.getAmount(), req.getLast4());
        if (opt.isEmpty()) return ResponseEntity.status(404).body(Map.of("detail", "original transaction not found"));
        Transaction tx = opt.get();
        return ResponseEntity.ok(Map.of("refund_transaction_id", tx.getProviderTxId(), "status", tx.getStatus()));
    }
}
