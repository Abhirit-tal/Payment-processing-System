package com.example.payment.controller;

import com.example.payment.dto.PaymentErrorCode;
import com.example.payment.dto.PaymentErrorResponse;
import com.example.payment.exception.*;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Global exception handler for structured error responses.
 *
 * <p>This handler provides consistent error response formatting across all
 * payment API endpoints, with proper error codes, retry guidance, and
 * request correlation.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle validation exceptions from @Valid annotations.
     * Card-related field values are masked to prevent sensitive data leakage.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<PaymentErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            // Mask any rejected value that may contain card data
            Object rejectedValue = ((FieldError) error).getRejectedValue();
            if (rejectedValue != null && isSensitiveField(fieldName)) {
                errorMessage = maskSensitiveErrorMessage(errorMessage, rejectedValue);
            }
            fieldErrors.put(fieldName, errorMessage);
        });

        String message = "Validation failed: " + fieldErrors;
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.INVALID_REQUEST, message)
                .withRequestId(getRequestId(request));

        log.warn("Validation error: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle invalid state transition exceptions.
     */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ResponseEntity<PaymentErrorResponse> handleInvalidStateTransition(
            InvalidStateTransitionException ex, WebRequest request) {

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.INVALID_STATE_TRANSITION, ex.getMessage())
                .withRequestId(getRequestId(request))
                .withSuggestion("Check the current transaction state before performing this operation");

        log.warn("Invalid state transition for order {}: {} -> {}",
                ex.getOrderId(), ex.getFromState(), ex.getToState());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    /**
     * Handle gateway declined exceptions.
     */
    @ExceptionHandler(GatewayDeclinedException.class)
    public ResponseEntity<PaymentErrorResponse> handleGatewayDeclined(
            GatewayDeclinedException ex, WebRequest request) {

        PaymentErrorResponse.ProviderError providerError = new PaymentErrorResponse.ProviderError(
                ex.getDeclineCode(), ex.getMessage(), ex.getAvsResult(), ex.getCvvResult());

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.CARD_DECLINED, ex.getMessage())
                .withRequestId(getRequestId(request))
                .withProviderError(providerError)
                .withSuggestion("Try a different payment method")
                .withSuggestion("Contact your card issuer for more information");

        log.info("Payment declined: {}", ex.getDeclineCode());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    /**
     * Handle gateway timeout exceptions.
     */
    @ExceptionHandler(GatewayTimeoutException.class)
    public ResponseEntity<PaymentErrorResponse> handleGatewayTimeout(
            GatewayTimeoutException ex, WebRequest request) {

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.GATEWAY_TIMEOUT, ex.getMessage())
                .withRequestId(getRequestId(request))
                .withRetryAfter(ex.getSuggestedRetryDelayMs() / 1000)
                .withSuggestion("Retry the request after the suggested delay");

        log.error("Gateway timeout: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response);
    }

    /**
     * Handle transient payment exceptions.
     */
    @ExceptionHandler(TransientPaymentException.class)
    public ResponseEntity<PaymentErrorResponse> handleTransientException(
            TransientPaymentException ex, WebRequest request) {

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.GATEWAY_ERROR, ex.getMessage())
                .withRequestId(getRequestId(request))
                .withRetryAfter(ex.getSuggestedRetryDelayMs() / 1000)
                .withSuggestion("This is a temporary error. Please retry.");

        if (ex.getProviderErrorCode() != null) {
            response.withProviderError(ex.getProviderErrorCode(), ex.getProviderErrorMessage());
        }

        log.error("Transient payment error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    /**
     * Handle permanent payment exceptions.
     */
    @ExceptionHandler(PermanentPaymentException.class)
    public ResponseEntity<PaymentErrorResponse> handlePermanentException(
            PermanentPaymentException ex, WebRequest request) {

        PaymentErrorCode errorCode = mapErrorCode(ex.getErrorCode());
        PaymentErrorResponse response = new PaymentErrorResponse(errorCode, ex.getMessage())
                .withRequestId(getRequestId(request));

        if (ex.getProviderErrorCode() != null) {
            response.withProviderError(ex.getProviderErrorCode(), ex.getProviderErrorMessage());
        }

        log.warn("Permanent payment error: {} - {}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    /**
     * Handle base payment exceptions.
     */
    @ExceptionHandler(PaymentException.class)
    public ResponseEntity<PaymentErrorResponse> handlePaymentException(
            PaymentException ex, WebRequest request) {

        PaymentErrorCode errorCode = mapErrorCode(ex.getErrorCode());
        PaymentErrorResponse response = new PaymentErrorResponse(errorCode, ex.getMessage())
                .withRequestId(getRequestId(request));

        if (ex.getProviderErrorCode() != null) {
            response.withProviderError(ex.getProviderErrorCode(), ex.getProviderErrorMessage());
        }

        log.error("Payment error: {} - {}", ex.getErrorCode(), ex.getMessage(), ex);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(response);
    }

    /**
     * Handle all other exceptions.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<PaymentErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.INTERNAL_ERROR, "An unexpected error occurred")
                .withRequestId(getRequestId(request));

        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    /**
     * Generate or retrieve request ID for correlation.
     */
    private String getRequestId(WebRequest request) {
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = "req_" + UUID.randomUUID().toString().substring(0, 12);
        }
        return requestId;
    }

    /**
     * Handle rate limiter exceeded exceptions (HTTP 429 Too Many Requests).
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<PaymentErrorResponse> handleRateLimitExceeded(
            RequestNotPermitted ex, WebRequest request) {

        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.RATE_LIMIT_EXCEEDED, "Too many requests. Please retry after a short delay.")
                .withRequestId(getRequestId(request))
                .withRetryAfter(1)
                .withSuggestion("Reduce request frequency or implement backoff");

        log.warn("Rate limit exceeded: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response);
    }

    /**
     * Map error code string to PaymentErrorCode enum.
     */
    private PaymentErrorCode mapErrorCode(String errorCode) {
        if (errorCode == null) {
            return PaymentErrorCode.INTERNAL_ERROR;
        }
        try {
            return PaymentErrorCode.valueOf(errorCode);
        } catch (IllegalArgumentException e) {
            return PaymentErrorCode.INTERNAL_ERROR;
        }
    }

    /**
     * Check if a field name relates to sensitive card data.
     */
    private boolean isSensitiveField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.contains("card") || lower.contains("number")
                || lower.contains("cvv") || lower.contains("pan")
                || lower.contains("cvc") || lower.contains("cardcode");
    }

    /**
     * Mask any sensitive card values that might appear in validation error messages.
     */
    private String maskSensitiveErrorMessage(String message, Object rejectedValue) {
        if (rejectedValue == null || message == null) return message;
        String valueStr = rejectedValue.toString();
        // If the rejected value looks like a card number (long digits), mask it
        if (valueStr.matches("\\d{8,19}")) {
            return message;  // don't include the value at all
        }
        // Replace any embedded card-like numbers in the message itself
        return message.replaceAll("\\b\\d{13,19}\\b", "****");
    }
}

