package com.example.payment.dto;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentErrorResponse and related DTOs.
 */
public class PaymentErrorResponseTest {

    @Test
    void testDefaultConstructor() {
        PaymentErrorResponse response = new PaymentErrorResponse();
        assertNotNull(response.getError());
    }

    @Test
    void testConstructorWithErrorCodeAndMessage() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.CARD_DECLINED, "Card was declined by issuer");

        PaymentErrorResponse.ErrorDetail error = response.getError();
        assertNotNull(error);
        assertEquals("CARD_DECLINED", error.getCode());
        assertEquals("Card was declined by issuer", error.getMessage());
        assertEquals("DECLINE_ERROR", error.getCategory());
        assertFalse(error.getRetryable());
        assertNotNull(error.getTimestamp());
    }

    @Test
    void testConstructorWithErrorCodeAndNullMessage() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.GATEWAY_TIMEOUT, null);

        PaymentErrorResponse.ErrorDetail error = response.getError();
        assertEquals("The payment gateway timed out", error.getMessage()); // Default message
    }

    @Test
    void testWithRequestId() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.INTERNAL_ERROR, "Something went wrong");

        PaymentErrorResponse result = response.withRequestId("req-12345");

        assertSame(response, result);
        assertEquals("req-12345", response.getError().getRequestId());
    }

    @Test
    void testWithProviderErrorCodeAndMessage() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.CARD_DECLINED, "Declined");

        response.withProviderError("2", "This transaction has been declined");

        PaymentErrorResponse.ProviderError providerError = response.getError().getProviderError();
        assertNotNull(providerError);
        assertEquals("2", providerError.getCode());
        assertEquals("This transaction has been declined", providerError.getMessage());
    }

    @Test
    void testWithProviderErrorObject() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.CARD_DECLINED, "Declined");

        PaymentErrorResponse.ProviderError providerError =
                new PaymentErrorResponse.ProviderError("3", "Error message", "A", "M");
        response.withProviderError(providerError);

        assertEquals(providerError, response.getError().getProviderError());
        assertEquals("A", response.getError().getProviderError().getAvsResult());
        assertEquals("M", response.getError().getProviderError().getCvvResult());
    }

    @Test
    void testWithSuggestion() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.CARD_DECLINED, "Declined");

        response.withSuggestion("Try a different card");
        response.withSuggestion("Contact your bank");

        List<String> suggestions = response.getError().getSuggestions();
        assertNotNull(suggestions);
        assertEquals(2, suggestions.size());
        assertTrue(suggestions.contains("Try a different card"));
        assertTrue(suggestions.contains("Contact your bank"));
    }

    @Test
    void testWithRetryAfter() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.GATEWAY_TIMEOUT, "Timeout");

        response.withRetryAfter(30);

        assertEquals(30, response.getError().getRetryAfterSeconds());
    }

    @Test
    void testChainedBuilderMethods() {
        PaymentErrorResponse response = new PaymentErrorResponse(
                PaymentErrorCode.GATEWAY_ERROR, "Gateway error")
                .withRequestId("req-abc")
                .withProviderError("E001", "Provider message")
                .withSuggestion("Wait and retry")
                .withRetryAfter(60);

        PaymentErrorResponse.ErrorDetail error = response.getError();
        assertEquals("req-abc", error.getRequestId());
        assertEquals("E001", error.getProviderError().getCode());
        assertEquals(1, error.getSuggestions().size());
        assertEquals(60, error.getRetryAfterSeconds());
    }

    @Test
    void testWithNullError() {
        PaymentErrorResponse response = new PaymentErrorResponse();
        response.setError(null);

        // Should not throw NPE
        response.withRequestId("test");
        response.withProviderError("code", "msg");
        response.withSuggestion("suggestion");
        response.withRetryAfter(10);
    }

    // ========== ErrorDetail Tests ==========

    @Test
    void testErrorDetailSettersAndGetters() {
        PaymentErrorResponse.ErrorDetail detail = new PaymentErrorResponse.ErrorDetail();

        detail.setCode("TEST_CODE");
        detail.setMessage("Test message");
        detail.setCategory("TEST_CATEGORY");
        detail.setRetryable(true);
        detail.setRequestId("req-test");
        detail.setRetryAfterSeconds(120);

        Instant now = Instant.now();
        detail.setTimestamp(now);

        assertEquals("TEST_CODE", detail.getCode());
        assertEquals("Test message", detail.getMessage());
        assertEquals("TEST_CATEGORY", detail.getCategory());
        assertTrue(detail.getRetryable());
        assertEquals("req-test", detail.getRequestId());
        assertEquals(120, detail.getRetryAfterSeconds());
        assertEquals(now, detail.getTimestamp());
    }

    @Test
    void testErrorDetailSuggestions() {
        PaymentErrorResponse.ErrorDetail detail = new PaymentErrorResponse.ErrorDetail();

        detail.setSuggestions(List.of("Suggestion 1", "Suggestion 2"));

        assertEquals(2, detail.getSuggestions().size());
    }

    @Test
    void testErrorDetailProviderError() {
        PaymentErrorResponse.ErrorDetail detail = new PaymentErrorResponse.ErrorDetail();
        PaymentErrorResponse.ProviderError providerError =
                new PaymentErrorResponse.ProviderError("code", "message");

        detail.setProviderError(providerError);

        assertEquals(providerError, detail.getProviderError());
    }

    // ========== ProviderError Tests ==========

    @Test
    void testProviderErrorDefaultConstructor() {
        PaymentErrorResponse.ProviderError error = new PaymentErrorResponse.ProviderError();
        assertNull(error.getCode());
        assertNull(error.getMessage());
        assertNull(error.getAvsResult());
        assertNull(error.getCvvResult());
    }

    @Test
    void testProviderErrorTwoArgConstructor() {
        PaymentErrorResponse.ProviderError error =
                new PaymentErrorResponse.ProviderError("E100", "Error message");

        assertEquals("E100", error.getCode());
        assertEquals("Error message", error.getMessage());
        assertNull(error.getAvsResult());
        assertNull(error.getCvvResult());
    }

    @Test
    void testProviderErrorFourArgConstructor() {
        PaymentErrorResponse.ProviderError error =
                new PaymentErrorResponse.ProviderError("E200", "Message", "Y", "N");

        assertEquals("E200", error.getCode());
        assertEquals("Message", error.getMessage());
        assertEquals("Y", error.getAvsResult());
        assertEquals("N", error.getCvvResult());
    }

    @Test
    void testProviderErrorSettersAndGetters() {
        PaymentErrorResponse.ProviderError error = new PaymentErrorResponse.ProviderError();

        error.setCode("CODE1");
        error.setMessage("Provider message");
        error.setAvsResult("A");
        error.setCvvResult("M");

        assertEquals("CODE1", error.getCode());
        assertEquals("Provider message", error.getMessage());
        assertEquals("A", error.getAvsResult());
        assertEquals("M", error.getCvvResult());
    }
}

