package com.example.payment.controller;

import com.example.payment.dto.SubscriptionRequests;
import com.example.payment.model.Subscription;
import com.example.payment.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for recurring billing / subscription management.
 *
 * <p>Endpoints for creating, retrieving, updating, and cancelling
 * recurring billing subscriptions via Authorize.Net ARB API.</p>
 */
@RestController
@RequestMapping("/payments/subscriptions")
@Tag(name = "Subscriptions", description = "Recurring Billing Management")
public class SubscriptionController {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionController.class);

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    @PostMapping
    @Operation(summary = "Create a recurring billing subscription",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> createSubscription(
            @Valid @RequestBody SubscriptionRequests.CreateSubscriptionRequest req) {
        log.info("Creating subscription: name={}, amount={}", req.getName(), req.getAmount());

        Map<String, String> card = req.getCard().toGatewayMap();

        Subscription sub = subscriptionService.createSubscription(
                req.getName(), req.getAmount(),
                req.getIntervalLength(), req.getIntervalUnit(),
                req.getStartDate(), card);

        if ("failed".equals(sub.getStatus())) {
            return ResponseEntity.status(502).body(Map.of(
                    "detail", "Failed to create subscription at payment gateway"));
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("id", sub.getId());
        resp.put("gateway_subscription_id", sub.getGatewaySubscriptionId());
        resp.put("name", sub.getName());
        resp.put("amount", sub.getAmount());
        resp.put("interval", sub.getIntervalLength() + " " + sub.getIntervalUnit());
        resp.put("start_date", sub.getStartDate().toString());
        resp.put("status", sub.getStatus());
        return ResponseEntity.status(201).body(resp);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get subscription by ID",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> getSubscription(@PathVariable Long id) {
        return subscriptionService.getSubscription(id)
                .map(sub -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", sub.getId());
                    resp.put("gateway_subscription_id", sub.getGatewaySubscriptionId());
                    resp.put("name", sub.getName());
                    resp.put("amount", sub.getAmount());
                    resp.put("interval", sub.getIntervalLength() + " " + sub.getIntervalUnit());
                    resp.put("start_date", sub.getStartDate().toString());
                    resp.put("status", sub.getStatus());
                    resp.put("created_at", sub.getCreatedAt().toString());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.status(404).body(Map.of("detail", "Subscription not found")));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a subscription (name and/or amount)",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequests.UpdateSubscriptionRequest req) {
        return subscriptionService.updateSubscription(id, req.getName(), req.getAmount())
                .map(sub -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", sub.getId());
                    resp.put("name", sub.getName());
                    resp.put("amount", sub.getAmount());
                    resp.put("status", sub.getStatus());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.status(404).body(Map.of("detail", "Subscription not found or update failed")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a subscription",
            security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> cancelSubscription(@PathVariable Long id) {
        return subscriptionService.cancelSubscription(id)
                .map(sub -> {
                    Map<String, Object> resp = new HashMap<>();
                    resp.put("id", sub.getId());
                    resp.put("status", sub.getStatus());
                    resp.put("cancelled_at", sub.getCancelledAt().toString());
                    return ResponseEntity.ok(resp);
                })
                .orElse(ResponseEntity.status(404).body(Map.of("detail", "Subscription not found or cancel failed")));
    }
}

