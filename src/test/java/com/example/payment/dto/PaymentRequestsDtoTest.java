package com.example.payment.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PaymentRequests DTOs.
 */
public class PaymentRequestsDtoTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    class CardTests {

        @Test
        void testValidCard() {
            PaymentRequests.Card card = new PaymentRequests.Card();
            card.setNumber("4111111111111111");
            card.setExpMonth(12);
            card.setExpYear(2030);
            card.setCvv("123");

            assertEquals("4111111111111111", card.getNumber());
            assertEquals(12, card.getExpMonth());
            assertEquals(2030, card.getExpYear());
            assertEquals("123", card.getCvv());
        }

        @Test
        void testCardSettersAndGetters() {
            PaymentRequests.Card card = new PaymentRequests.Card();

            card.setNumber("378282246310005"); // AMEX
            card.setExpMonth(6);
            card.setExpYear(2028);
            card.setCvv("1234"); // 4-digit AMEX CVV

            assertEquals("378282246310005", card.getNumber());
            assertEquals(6, card.getExpMonth());
            assertEquals(2028, card.getExpYear());
            assertEquals("1234", card.getCvv());
        }
    }

    @Nested
    class PurchaseRequestTests {

        @Test
        void testPurchaseRequestSettersAndGetters() {
            PaymentRequests.PurchaseRequest request = new PaymentRequests.PurchaseRequest();

            PaymentRequests.Card card = new PaymentRequests.Card();
            card.setNumber("4111111111111111");
            card.setExpMonth(12);
            card.setExpYear(2030);
            card.setCvv("123");

            request.setAmount(new BigDecimal("99.99"));
            request.setCurrency("EUR");
            request.setCard(card);
            request.setOrderId("order-123");

            assertEquals(new BigDecimal("99.99"), request.getAmount());
            assertEquals("EUR", request.getCurrency());
            assertNotNull(request.getCard());
            assertEquals("order-123", request.getOrderId());
        }

        @Test
        void testPurchaseRequestDefaultCurrency() {
            PaymentRequests.PurchaseRequest request = new PaymentRequests.PurchaseRequest();
            assertEquals("USD", request.getCurrency());
        }
    }

    @Nested
    class AuthorizeRequestTests {

        @Test
        void testAuthorizeRequestInheritsPurchaseRequest() {
            PaymentRequests.AuthorizeRequest request = new PaymentRequests.AuthorizeRequest();

            PaymentRequests.Card card = new PaymentRequests.Card();
            card.setNumber("4111111111111111");
            card.setExpMonth(12);
            card.setExpYear(2030);
            card.setCvv("123");

            request.setAmount(new BigDecimal("50.00"));
            request.setCurrency("USD");
            request.setCard(card);
            request.setOrderId("auth-order-1");

            assertEquals(new BigDecimal("50.00"), request.getAmount());
            assertEquals("USD", request.getCurrency());
            assertEquals("auth-order-1", request.getOrderId());
        }
    }

    @Nested
    class CaptureRequestTests {

        @Test
        void testCaptureRequestSettersAndGetters() {
            PaymentRequests.CaptureRequest request = new PaymentRequests.CaptureRequest();

            request.setTransactionId("tx-123");
            request.setAmount(new BigDecimal("75.00"));

            assertEquals("tx-123", request.getTransactionId());
            assertEquals(new BigDecimal("75.00"), request.getAmount());
        }

        @Test
        void testCaptureRequestWithNullAmount() {
            PaymentRequests.CaptureRequest request = new PaymentRequests.CaptureRequest();

            request.setTransactionId("tx-456");
            request.setAmount(null);

            assertEquals("tx-456", request.getTransactionId());
            assertNull(request.getAmount());
        }
    }

    @Nested
    class RefundRequestTests {

        @Test
        void testRefundRequestSettersAndGetters() {
            PaymentRequests.RefundRequest request = new PaymentRequests.RefundRequest();

            request.setTransactionId("cap-tx-1");
            request.setAmount(new BigDecimal("25.00"));
            request.setLast4("1111");

            assertEquals("cap-tx-1", request.getTransactionId());
            assertEquals(new BigDecimal("25.00"), request.getAmount());
            assertEquals("1111", request.getLast4());
        }

        @Test
        void testRefundRequestFullRefund() {
            PaymentRequests.RefundRequest request = new PaymentRequests.RefundRequest();

            request.setTransactionId("cap-tx-2");
            request.setAmount(null); // Full refund
            request.setLast4("4242");

            assertNull(request.getAmount());
        }
    }

    @Nested
    class CancelRequestTests {

        @Test
        void testCancelRequestSettersAndGetters() {
            PaymentRequests.CancelRequest request = new PaymentRequests.CancelRequest();

            request.setTransactionId("auth-tx-void");

            assertEquals("auth-tx-void", request.getTransactionId());
        }
    }

    @Nested
    class ValidationTests {

        @Test
        void testCaptureRequestTransactionIdRequired() {
            PaymentRequests.CaptureRequest request = new PaymentRequests.CaptureRequest();
            request.setTransactionId(""); // Empty - should violate @NotBlank

            Set<ConstraintViolation<PaymentRequests.CaptureRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        void testRefundRequestTransactionIdRequired() {
            PaymentRequests.RefundRequest request = new PaymentRequests.RefundRequest();
            request.setTransactionId("");

            Set<ConstraintViolation<PaymentRequests.RefundRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        void testCancelRequestTransactionIdRequired() {
            PaymentRequests.CancelRequest request = new PaymentRequests.CancelRequest();
            request.setTransactionId("");

            Set<ConstraintViolation<PaymentRequests.CancelRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
        }

        @Test
        void testRefundRequestLast4Size() {
            PaymentRequests.RefundRequest request = new PaymentRequests.RefundRequest();
            request.setTransactionId("valid-tx");
            request.setLast4("123"); // Only 3 digits - should violate @Size(min=4, max=4)

            Set<ConstraintViolation<PaymentRequests.RefundRequest>> violations = validator.validate(request);
            assertFalse(violations.isEmpty());
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("last4")));
        }

        @Test
        void testValidRefundRequest() {
            PaymentRequests.RefundRequest request = new PaymentRequests.RefundRequest();
            request.setTransactionId("valid-tx");
            request.setLast4("1234");
            request.setAmount(new BigDecimal("10.00"));

            Set<ConstraintViolation<PaymentRequests.RefundRequest>> violations = validator.validate(request);
            assertTrue(violations.isEmpty());
        }
    }
}

