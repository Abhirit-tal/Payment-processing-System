package com.example.payment.service;

import com.example.payment.event.PaymentEventQueue;
import com.example.payment.exception.TransientPaymentException;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for gateway failure scenarios including declines, timeouts,
 * transient errors, and circuit breaker fallbacks.
 */
public class PaymentServiceGatewayFailureTest {

    private AuthorizeNetClient authorizeNetClient;
    private OrderRepository orderRepository;
    private TransactionRepository transactionRepository;
    private PaymentStateMachine stateMachine;
    private AuditService auditService;
    private PaymentEventQueue eventQueue;
    private PaymentService paymentService;

    @BeforeEach
    void setup() {
        authorizeNetClient = mock(AuthorizeNetClient.class);
        orderRepository = mock(OrderRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        stateMachine = new PaymentStateMachine();
        auditService = mock(AuditService.class);
        eventQueue = mock(PaymentEventQueue.class);

        paymentService = new PaymentService(
                authorizeNetClient, orderRepository, transactionRepository,
                stateMachine, auditService, eventQueue);

        // Mock save to return argument
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
            Order o = i.getArgument(0);
            if (o.getId() == null) o.setId(1L);
            return o;
        });
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            if (t.getId() == null) t.setId(1L);
            return t;
        });
        when(eventQueue.publish(any())).thenReturn(true);
    }

    private Map<String, String> testCard() {
        Map<String, String> card = new HashMap<>();
        card.put("number", "4111111111111111");
        card.put("expMonth", "12");
        card.put("expYear", "2030");
        card.put("cvv", "123");
        return card;
    }

    @Nested
    @DisplayName("Gateway Decline Scenarios")
    class GatewayDeclineTests {

        @Test
        @DisplayName("Card declined (response_code=2) should create order in DECLINED state")
        void cardDeclined() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "failed");
            gatewayResp.put("response_code", "2");
            gatewayResp.put("raw", "declined");

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("50.00"), "USD", testCard(), "ext-decline-1");

            assertEquals("failed", result.getStatus());
            verify(auditService).logErrorAsync(any(), any(), contains("ecline"), any());
        }

        @Test
        @DisplayName("Insufficient funds (response_code=2) should return failed transaction")
        void insufficientFunds() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "failed");
            gatewayResp.put("response_code", "2");
            gatewayResp.put("raw", "insufficient funds");

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(false)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.authorizeOnly(
                    new BigDecimal("1000.00"), "USD", testCard(), "ext-insufficient-1");

            assertEquals("failed", result.getStatus());
        }
    }

    @Nested
    @DisplayName("Gateway Error Scenarios")
    class GatewayErrorTests {

        @Test
        @DisplayName("Gateway returns error (response_code=3) should create ERROR state order")
        void gatewayError() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "failed");
            gatewayResp.put("response_code", "3");
            gatewayResp.put("raw", "error");

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("25.00"), "USD", testCard(), "ext-error-1");

            assertEquals("failed", result.getStatus());
        }

        @Test
        @DisplayName("Gateway returns held for review (response_code=4)")
        void heldForReview() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "failed");
            gatewayResp.put("response_code", "4");
            gatewayResp.put("raw", "held for review");

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("25.00"), "USD", testCard(), "ext-review-1");

            assertEquals("failed", result.getStatus());
        }
    }

    @Nested
    @DisplayName("Gateway Timeout Scenarios")
    class GatewayTimeoutTests {

        @Test
        @DisplayName("Gateway throws TransientPaymentException")
        void transientError() {
            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenThrow(new TransientPaymentException("Gateway timeout"));

            assertThrows(TransientPaymentException.class, () ->
                    paymentService.purchase(new BigDecimal("15.00"), "USD", testCard(), "ext-timeout-1"));
        }

        @Test
        @DisplayName("Gateway communication failure throws TransientPaymentException")
        void communicationFailure() {
            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(false)))
                    .thenThrow(new TransientPaymentException("Connection refused"));

            assertThrows(TransientPaymentException.class, () ->
                    paymentService.authorizeOnly(new BigDecimal("10.00"), "USD", testCard(), "ext-comm-1"));
        }
    }

    @Nested
    @DisplayName("Circuit Breaker Fallback Scenarios")
    class CircuitBreakerTests {

        @Test
        @DisplayName("Circuit breaker open returns structured error from fallback")
        void circuitBreakerFallback() {
            Map<String, Object> fallbackResp = new HashMap<>();
            fallbackResp.put("status", "failed");
            fallbackResp.put("raw", Map.of("error", "Payment gateway temporarily unavailable"));

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(fallbackResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("10.00"), "USD", testCard(), "ext-cb-1");

            assertEquals("failed", result.getStatus());
        }
    }

    @Nested
    @DisplayName("Capture Failure Scenarios")
    class CaptureFailureTests {

        @Test
        @DisplayName("Capture of non-existent transaction returns empty")
        void captureNotFound() {
            when(transactionRepository.findByProviderTxId("non-existent"))
                    .thenReturn(Optional.empty());

            Optional<Transaction> result = paymentService.capture("non-existent", new BigDecimal("10.00"));

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Capture with gateway failure keeps order in AUTHORIZED state")
        void captureGatewayFailure() {
            Order order = new Order();
            order.setId(1L);
            order.setAmount(new BigDecimal("50.00"));
            order.setCurrency("USD");
            order.setState(PaymentState.AUTHORIZED);
            order.setStatus("authorized");

            Transaction authTx = new Transaction();
            authTx.setId(1L);
            authTx.setOrder(order);
            authTx.setProviderTxId("auth-123");
            authTx.setAmount(new BigDecimal("50.00"));
            authTx.setType("authorize");
            authTx.setStatus("success");

            when(transactionRepository.findByProviderTxId("auth-123"))
                    .thenReturn(Optional.of(authTx));

            Map<String, Object> failedResp = new HashMap<>();
            failedResp.put("status", "failed");
            failedResp.put("raw", "capture failed");

            when(authorizeNetClient.captureTransaction(eq("auth-123"), any()))
                    .thenReturn(failedResp);

            Optional<Transaction> result = paymentService.capture("auth-123", new BigDecimal("50.00"));

            assertTrue(result.isPresent());
            assertEquals("failed", result.get().getStatus());
            // Order should remain in AUTHORIZED state after failed capture
            assertEquals(PaymentState.AUTHORIZED, order.getState());
        }
    }

    @Nested
    @DisplayName("Refund Failure Scenarios")
    class RefundFailureTests {

        @Test
        @DisplayName("Refund of non-existent transaction returns empty")
        void refundNotFound() {
            when(transactionRepository.findByProviderTxId("non-existent"))
                    .thenReturn(Optional.empty());

            Optional<Transaction> result = paymentService.refund("non-existent", new BigDecimal("10.00"), "1111");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Refund gateway failure does not change order state")
        void refundGatewayFailure() {
            Order order = new Order();
            order.setId(1L);
            order.setAmount(new BigDecimal("50.00"));
            order.setCurrency("USD");
            order.setState(PaymentState.CAPTURED);
            order.setStatus("captured");

            Transaction captureTx = new Transaction();
            captureTx.setId(1L);
            captureTx.setOrder(order);
            captureTx.setProviderTxId("capture-123");
            captureTx.setAmount(new BigDecimal("50.00"));
            captureTx.setType("capture");
            captureTx.setStatus("success");

            when(transactionRepository.findByProviderTxId("capture-123"))
                    .thenReturn(Optional.of(captureTx));

            Map<String, Object> failedResp = new HashMap<>();
            failedResp.put("status", "failed");
            failedResp.put("raw", "refund failed");

            when(authorizeNetClient.refundTransaction(eq("capture-123"), any(), eq("1111")))
                    .thenReturn(failedResp);

            Optional<Transaction> result = paymentService.refund("capture-123", new BigDecimal("50.00"), "1111");

            assertTrue(result.isPresent());
            assertEquals("failed", result.get().getStatus());
        }
    }

    @Nested
    @DisplayName("Null/Missing Response Fields")
    class NullResponseTests {

        @Test
        @DisplayName("Gateway returns null provider_tx_id")
        void nullProviderTxId() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "success");
            gatewayResp.put("raw", "success response");
            // provider_tx_id is intentionally null

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("10.00"), "USD", testCard(), "ext-null-1");

            assertEquals("success", result.getStatus());
            assertNull(result.getProviderTxId());
        }

        @Test
        @DisplayName("Gateway returns null response_code with failed status")
        void nullResponseCode() {
            Map<String, Object> gatewayResp = new HashMap<>();
            gatewayResp.put("status", "failed");
            gatewayResp.put("raw", "unknown error");
            // response_code is intentionally null

            when(authorizeNetClient.createTransaction(any(), any(), any(), eq(true)))
                    .thenReturn(gatewayResp);

            Transaction result = paymentService.purchase(
                    new BigDecimal("10.00"), "USD", testCard(), "ext-null-rc-1");

            assertEquals("failed", result.getStatus());
        }
    }
}

