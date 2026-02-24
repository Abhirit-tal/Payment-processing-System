package com.example.payment.controller;

import com.example.payment.dto.PaymentErrorCode;
import com.example.payment.dto.PaymentErrorResponse;
import com.example.payment.exception.*;
import com.example.payment.model.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.WebRequest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GlobalExceptionHandler.
 */
public class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private WebRequest mockRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        mockRequest = mock(WebRequest.class);
        when(mockRequest.getHeader("X-Request-ID")).thenReturn("test-request-id");
    }

    @Test
    void testHandleValidationExceptions() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("object", "amount", "must not be null");
        FieldError fieldError2 = new FieldError("object", "card.number", "invalid card number");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Arrays.asList(fieldError1, fieldError2));

        ResponseEntity<PaymentErrorResponse> response = handler.handleValidationExceptions(ex, mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_REQUEST", response.getBody().getError().getCode());
        assertTrue(response.getBody().getError().getMessage().contains("amount"));
        assertEquals("test-request-id", response.getBody().getError().getRequestId());
    }

    @Test
    void testHandleInvalidStateTransition() {
        InvalidStateTransitionException ex = new InvalidStateTransitionException(
                PaymentState.CREATED, PaymentState.CAPTURED, 123L);

        ResponseEntity<PaymentErrorResponse> response = handler.handleInvalidStateTransition(ex, mockRequest);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INVALID_STATE_TRANSITION", response.getBody().getError().getCode());
        assertEquals("test-request-id", response.getBody().getError().getRequestId());
    }

    @Test
    void testHandleGatewayDeclined() {
        GatewayDeclinedException ex = new GatewayDeclinedException(
                "Card declined", "D001", "A", "M");

        ResponseEntity<PaymentErrorResponse> response = handler.handleGatewayDeclined(ex, mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CARD_DECLINED", response.getBody().getError().getCode());
        assertNotNull(response.getBody().getError().getProviderError());
        assertEquals("D001", response.getBody().getError().getProviderError().getCode());
        assertEquals("A", response.getBody().getError().getProviderError().getAvsResult());
        assertEquals("M", response.getBody().getError().getProviderError().getCvvResult());
        assertNotNull(response.getBody().getError().getSuggestions());
        assertTrue(response.getBody().getError().getSuggestions().size() >= 1);
    }

    @Test
    void testHandleGatewayTimeout() {
        GatewayTimeoutException ex = new GatewayTimeoutException("Gateway timed out");

        ResponseEntity<PaymentErrorResponse> response = handler.handleGatewayTimeout(ex, mockRequest);

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GATEWAY_TIMEOUT", response.getBody().getError().getCode());
        // Check retry after is set (2000ms / 1000 = 2 seconds)
        assertEquals(2, response.getBody().getError().getRetryAfterSeconds());
    }

    @Test
    void testHandleTransientException() {
        TransientPaymentException ex = new TransientPaymentException("Temporary error", 3000);
        ex.setProviderErrorCode("T001");
        ex.setProviderErrorMessage("Temporary failure");

        ResponseEntity<PaymentErrorResponse> response = handler.handleTransientException(ex, mockRequest);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("GATEWAY_ERROR", response.getBody().getError().getCode());
        // 3000ms / 1000 = 3 seconds
        assertEquals(3, response.getBody().getError().getRetryAfterSeconds());
        assertNotNull(response.getBody().getError().getProviderError());
        assertEquals("T001", response.getBody().getError().getProviderError().getCode());
    }

    @Test
    void testHandleTransientExceptionNoProviderError() {
        TransientPaymentException ex = new TransientPaymentException("Temporary error");

        ResponseEntity<PaymentErrorResponse> response = handler.handleTransientException(ex, mockRequest);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertNull(response.getBody().getError().getProviderError());
    }

    @Test
    void testHandlePermanentException() {
        PermanentPaymentException ex = new PermanentPaymentException("Permanent error", "VALIDATION_ERROR");
        ex.setProviderErrorCode("P001");
        ex.setProviderErrorMessage("Validation failed");

        ResponseEntity<PaymentErrorResponse> response = handler.handlePermanentException(ex, mockRequest);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getError().getProviderError());
        assertEquals("P001", response.getBody().getError().getProviderError().getCode());
    }

    @Test
    void testHandlePermanentExceptionKnownErrorCode() {
        PermanentPaymentException ex = new PermanentPaymentException("Card declined", "CARD_DECLINED");

        ResponseEntity<PaymentErrorResponse> response = handler.handlePermanentException(ex, mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("CARD_DECLINED", response.getBody().getError().getCode());
    }

    @Test
    void testHandlePermanentExceptionUnknownErrorCode() {
        PermanentPaymentException ex = new PermanentPaymentException("Unknown error", "UNKNOWN_CODE");

        ResponseEntity<PaymentErrorResponse> response = handler.handlePermanentException(ex, mockRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getBody().getError().getCode());
    }

    @Test
    void testHandlePaymentException() {
        // Using a concrete subclass for testing since PaymentException is abstract
        IdempotencyConflictException ex = new IdempotencyConflictException("Conflict detected");
        ex.setProviderErrorCode("I001");
        ex.setProviderErrorMessage("Idempotency conflict");

        ResponseEntity<PaymentErrorResponse> response = handler.handlePaymentException(ex, mockRequest);

        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getError().getProviderError());
    }

    @Test
    void testHandlePaymentExceptionNoProviderError() {
        IdempotencyConflictException ex = new IdempotencyConflictException("Conflict");

        ResponseEntity<PaymentErrorResponse> response = handler.handlePaymentException(ex, mockRequest);

        assertNull(response.getBody().getError().getProviderError());
    }

    @Test
    void testHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");

        ResponseEntity<PaymentErrorResponse> response = handler.handleGenericException(ex, mockRequest);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().getError().getCode());
        assertEquals("An unexpected error occurred", response.getBody().getError().getMessage());
        assertEquals("test-request-id", response.getBody().getError().getRequestId());
    }

    @Test
    void testRequestIdGeneratedWhenNotProvided() {
        when(mockRequest.getHeader("X-Request-ID")).thenReturn(null);

        Exception ex = new RuntimeException("Error");
        ResponseEntity<PaymentErrorResponse> response = handler.handleGenericException(ex, mockRequest);

        assertNotNull(response.getBody().getError().getRequestId());
        assertTrue(response.getBody().getError().getRequestId().startsWith("req_"));
    }

    @Test
    void testRequestIdGeneratedWhenBlank() {
        when(mockRequest.getHeader("X-Request-ID")).thenReturn("   ");

        Exception ex = new RuntimeException("Error");
        ResponseEntity<PaymentErrorResponse> response = handler.handleGenericException(ex, mockRequest);

        assertNotNull(response.getBody().getError().getRequestId());
        assertTrue(response.getBody().getError().getRequestId().startsWith("req_"));
    }

    @Test
    void testValidationWithEmptyErrors() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(Collections.emptyList());

        ResponseEntity<PaymentErrorResponse> response = handler.handleValidationExceptions(ex, mockRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody().getError().getMessage().contains("Validation failed"));
    }
}

