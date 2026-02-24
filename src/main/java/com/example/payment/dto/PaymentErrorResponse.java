package com.example.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Structured error response for payment API errors.
 *
 * <p>This response format provides:</p>
 * <ul>
 *   <li>Machine-readable error codes</li>
 *   <li>Human-readable messages</li>
 *   <li>Retry guidance</li>
 *   <li>Provider-specific error details</li>
 *   <li>Request correlation ID</li>
 * </ul>
 *
 * <h2>Example Response:</h2>
 * <pre>
 * {
 *   "error": {
 *     "code": "CARD_DECLINED",
 *     "message": "The card was declined by the issuing bank",
 *     "category": "DECLINE_ERROR",
 *     "retryable": false,
 *     "provider_error": {
 *       "code": "2",
 *       "message": "This transaction has been declined"
 *     },
 *     "suggestions": ["Try a different payment method"],
 *     "request_id": "req_abc123",
 *     "timestamp": "2026-02-20T10:30:00Z"
 *   }
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentErrorResponse {

    @JsonProperty("error")
    private ErrorDetail error;

    public PaymentErrorResponse() {
        this.error = new ErrorDetail();
    }

    public PaymentErrorResponse(PaymentErrorCode errorCode, String message) {
        this.error = new ErrorDetail();
        this.error.code = errorCode.getCode();
        this.error.message = message != null ? message : errorCode.getDefaultMessage();
        this.error.category = errorCode.getCategory();
        this.error.retryable = errorCode.isRetryable();
        this.error.timestamp = Instant.now();
    }

    public ErrorDetail getError() {
        return error;
    }

    public void setError(ErrorDetail error) {
        this.error = error;
    }

    // Builder methods
    public PaymentErrorResponse withRequestId(String requestId) {
        if (this.error != null) {
            this.error.requestId = requestId;
        }
        return this;
    }

    public PaymentErrorResponse withProviderError(String code, String message) {
        if (this.error != null) {
            this.error.providerError = new ProviderError(code, message);
        }
        return this;
    }

    public PaymentErrorResponse withProviderError(ProviderError providerError) {
        if (this.error != null) {
            this.error.providerError = providerError;
        }
        return this;
    }

    public PaymentErrorResponse withSuggestion(String suggestion) {
        if (this.error != null) {
            if (this.error.suggestions == null) {
                this.error.suggestions = new ArrayList<>();
            }
            this.error.suggestions.add(suggestion);
        }
        return this;
    }

    public PaymentErrorResponse withRetryAfter(Integer seconds) {
        if (this.error != null) {
            this.error.retryAfterSeconds = seconds;
        }
        return this;
    }

    /**
     * Inner class representing the error detail structure.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {

        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;

        @JsonProperty("category")
        private String category;

        @JsonProperty("retryable")
        private Boolean retryable;

        @JsonProperty("provider_error")
        private ProviderError providerError;

        @JsonProperty("suggestions")
        private List<String> suggestions;

        @JsonProperty("request_id")
        private String requestId;

        @JsonProperty("timestamp")
        private Instant timestamp;

        @JsonProperty("retry_after_seconds")
        private Integer retryAfterSeconds;

        // Getters and setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public Boolean getRetryable() { return retryable; }
        public void setRetryable(Boolean retryable) { this.retryable = retryable; }

        public ProviderError getProviderError() { return providerError; }
        public void setProviderError(ProviderError providerError) { this.providerError = providerError; }

        public List<String> getSuggestions() { return suggestions; }
        public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }

        public String getRequestId() { return requestId; }
        public void setRequestId(String requestId) { this.requestId = requestId; }

        public Instant getTimestamp() { return timestamp; }
        public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

        public Integer getRetryAfterSeconds() { return retryAfterSeconds; }
        public void setRetryAfterSeconds(Integer retryAfterSeconds) { this.retryAfterSeconds = retryAfterSeconds; }
    }

    /**
     * Inner class representing provider-specific error details.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ProviderError {

        @JsonProperty("code")
        private String code;

        @JsonProperty("message")
        private String message;

        @JsonProperty("avs_result")
        private String avsResult;

        @JsonProperty("cvv_result")
        private String cvvResult;

        public ProviderError() {}

        public ProviderError(String code, String message) {
            this.code = code;
            this.message = message;
        }

        public ProviderError(String code, String message, String avsResult, String cvvResult) {
            this.code = code;
            this.message = message;
            this.avsResult = avsResult;
            this.cvvResult = cvvResult;
        }

        // Getters and setters
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getAvsResult() { return avsResult; }
        public void setAvsResult(String avsResult) { this.avsResult = avsResult; }

        public String getCvvResult() { return cvvResult; }
        public void setCvvResult(String cvvResult) { this.cvvResult = cvvResult; }
    }
}

