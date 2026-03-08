package com.example.payment.service;

import com.example.payment.event.PaymentEventQueue;
import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended unit tests for PaymentService covering additional scenarios.
 */
public class PaymentServiceExtendedTest {

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
        stateMachine = mock(PaymentStateMachine.class);
        auditService = mock(AuditService.class);
        eventQueue = mock(PaymentEventQueue.class);
        paymentService = new PaymentService(authorizeNetClient, orderRepository, transactionRepository, stateMachine, auditService, eventQueue);

        // Default mocks
        when(stateMachine.canTransition(any(), any())).thenReturn(true);
        doNothing().when(stateMachine).validateTransition(any(), any(), any());
        doNothing().when(auditService).logOrderCreated(any());
        doNothing().when(auditService).logStateTransition(any(), any(), any());
        doNothing().when(auditService).logTransactionCreated(any(), any());
        doNothing().when(auditService).logGatewayCallAsync(any(), any(), any());
        doNothing().when(auditService).logGatewayResponseAsync(any(), any(), anyBoolean(), any());
        doNothing().when(auditService).logErrorAsync(any(), any(), any(), any());
    }

    @Nested
    class AuthorizeOnlyTests {

        @Test
        void testAuthorizeOnlySuccess() {
            when(authorizeNetClient.createTransaction(any(), anyString(), anyMap(), eq(false)))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "auth-123", "raw", Map.of()));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(1L);
                return o;
            });
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(1L);
                return t;
            });

            Transaction tx = paymentService.authorizeOnly(new BigDecimal("25.00"), "USD",
                Map.of("number", "4111111111111111"), "ext-auth");

            assertNotNull(tx);
            assertEquals("success", tx.getStatus());
            assertEquals("auth-123", tx.getProviderTxId());
        }

        @Test
        void testAuthorizeOnlyFailed() {
            when(authorizeNetClient.createTransaction(any(), anyString(), anyMap(), eq(false)))
                .thenReturn(Map.of("status", "failed", "response_code", "2", "raw", Map.of()));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(2L);
                return o;
            });
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(2L);
                return t;
            });

            Transaction tx = paymentService.authorizeOnly(new BigDecimal("50.00"), "USD",
                Map.of("number", "4111111111111111"), "ext-auth-fail");

            assertNotNull(tx);
            assertEquals("failed", tx.getStatus());
        }
    }

    @Nested
    class CaptureTests {

        @Test
        void testCaptureMissingTransaction() {
            when(transactionRepository.findByProviderTxId("missing")).thenReturn(Optional.empty());

            Optional<Transaction> result = paymentService.capture("missing", new BigDecimal("10.00"));

            assertTrue(result.isEmpty());
        }

        @Test
        void testCaptureInvalidState() {
            Order order = new Order();
            order.setId(5L);
            order.setState(PaymentState.CAPTURED); // Already captured

            Transaction authTx = new Transaction();
            authTx.setId(5L);
            authTx.setOrder(order);
            authTx.setAmount(new BigDecimal("100.00"));

            when(transactionRepository.findByProviderTxId("auth-invalid")).thenReturn(Optional.of(authTx));

            assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.capture("auth-invalid", null));
        }

        @Test
        void testCaptureWithNullAmount() {
            Order order = new Order();
            order.setId(10L);
            order.setState(PaymentState.AUTHORIZED);

            Transaction authTx = new Transaction();
            authTx.setId(10L);
            authTx.setOrder(order);
            authTx.setAmount(new BigDecimal("75.00"));
            authTx.setProviderTxId("auth-null-amt");

            when(transactionRepository.findByProviderTxId("auth-null-amt")).thenReturn(Optional.of(authTx));
            when(authorizeNetClient.captureTransaction(eq("auth-null-amt"), any()))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "cap-null", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Optional<Transaction> result = paymentService.capture("auth-null-amt", null);

            assertTrue(result.isPresent());
            assertEquals(new BigDecimal("75.00"), result.get().getAmount()); // Uses original amount
        }

        @Test
        void testCaptureFailure() {
            Order order = new Order();
            order.setId(11L);
            order.setState(PaymentState.AUTHORIZED);

            Transaction authTx = new Transaction();
            authTx.setId(11L);
            authTx.setOrder(order);
            authTx.setAmount(new BigDecimal("50.00"));
            authTx.setProviderTxId("auth-fail-cap");

            when(transactionRepository.findByProviderTxId("auth-fail-cap")).thenReturn(Optional.of(authTx));
            when(authorizeNetClient.captureTransaction(eq("auth-fail-cap"), any()))
                .thenReturn(Map.of("status", "failed", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Optional<Transaction> result = paymentService.capture("auth-fail-cap", new BigDecimal("50.00"));

            assertTrue(result.isPresent());
            assertEquals("failed", result.get().getStatus());
        }
    }

    @Nested
    class VoidTests {

        @Test
        void testVoidMissingTransaction() {
            when(transactionRepository.findByProviderTxId("void-missing")).thenReturn(Optional.empty());

            Optional<Transaction> result = paymentService.voidTransaction("void-missing");

            assertTrue(result.isEmpty());
        }

        @Test
        void testVoidInvalidState() {
            Order order = new Order();
            order.setId(20L);
            order.setState(PaymentState.CAPTURED); // Cannot void after capture

            Transaction tx = new Transaction();
            tx.setId(20L);
            tx.setOrder(order);

            when(transactionRepository.findByProviderTxId("void-invalid")).thenReturn(Optional.of(tx));

            assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.voidTransaction("void-invalid"));
        }

        @Test
        void testVoidFailure() {
            Order order = new Order();
            order.setId(21L);
            order.setState(PaymentState.AUTHORIZED);

            Transaction tx = new Transaction();
            tx.setId(21L);
            tx.setOrder(order);
            tx.setProviderTxId("void-fail");

            when(transactionRepository.findByProviderTxId("void-fail")).thenReturn(Optional.of(tx));
            when(authorizeNetClient.voidTransaction("void-fail"))
                .thenReturn(Map.of("status", "failed", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Optional<Transaction> result = paymentService.voidTransaction("void-fail");

            assertTrue(result.isPresent());
            assertEquals("failed", result.get().getStatus());
        }
    }

    @Nested
    class RefundTests {

        @Test
        void testRefundInvalidState() {
            Order order = new Order();
            order.setId(30L);
            order.setState(PaymentState.AUTHORIZED); // Cannot refund before capture

            Transaction tx = new Transaction();
            tx.setId(30L);
            tx.setOrder(order);

            when(transactionRepository.findByProviderTxId("refund-invalid")).thenReturn(Optional.of(tx));

            assertThrows(InvalidStateTransitionException.class,
                () -> paymentService.refund("refund-invalid", new BigDecimal("10.00"), "1111"));
        }

        @Test
        void testFullRefund() {
            Order order = new Order();
            order.setId(31L);
            order.setState(PaymentState.CAPTURED);

            Transaction captured = new Transaction();
            captured.setId(31L);
            captured.setOrder(order);
            captured.setAmount(new BigDecimal("100.00"));
            captured.setProviderTxId("cap-full-refund");

            when(transactionRepository.findByProviderTxId("cap-full-refund")).thenReturn(Optional.of(captured));
            when(authorizeNetClient.refundTransaction(eq("cap-full-refund"), any(), anyString()))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "ref-full", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Full refund - same amount as captured
            Optional<Transaction> result = paymentService.refund("cap-full-refund", new BigDecimal("100.00"), "1111");

            assertTrue(result.isPresent());
            assertEquals("success", result.get().getStatus());
        }

        @Test
        void testRefundWithNullAmount() {
            Order order = new Order();
            order.setId(32L);
            order.setState(PaymentState.CAPTURED);

            Transaction captured = new Transaction();
            captured.setId(32L);
            captured.setOrder(order);
            captured.setAmount(new BigDecimal("50.00"));
            captured.setProviderTxId("cap-null-ref");

            when(transactionRepository.findByProviderTxId("cap-null-ref")).thenReturn(Optional.of(captured));
            when(authorizeNetClient.refundTransaction(eq("cap-null-ref"), any(), anyString()))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "ref-null", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            // Null amount should use original transaction amount
            Optional<Transaction> result = paymentService.refund("cap-null-ref", null, "1111");

            assertTrue(result.isPresent());
            assertEquals(new BigDecimal("50.00"), result.get().getAmount());
        }

        @Test
        void testRefundFromPartiallyRefundedState() {
            Order order = new Order();
            order.setId(33L);
            order.setState(PaymentState.PARTIALLY_REFUNDED);

            Transaction captured = new Transaction();
            captured.setId(33L);
            captured.setOrder(order);
            captured.setAmount(new BigDecimal("100.00"));
            captured.setProviderTxId("cap-partial");

            when(transactionRepository.findByProviderTxId("cap-partial")).thenReturn(Optional.of(captured));
            when(authorizeNetClient.refundTransaction(eq("cap-partial"), any(), anyString()))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "ref-2", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Optional<Transaction> result = paymentService.refund("cap-partial", new BigDecimal("25.00"), "1111");

            assertTrue(result.isPresent());
        }

        @Test
        void testRefundFailure() {
            Order order = new Order();
            order.setId(34L);
            order.setState(PaymentState.CAPTURED);

            Transaction captured = new Transaction();
            captured.setId(34L);
            captured.setOrder(order);
            captured.setAmount(new BigDecimal("100.00"));
            captured.setProviderTxId("cap-ref-fail");

            when(transactionRepository.findByProviderTxId("cap-ref-fail")).thenReturn(Optional.of(captured));
            when(authorizeNetClient.refundTransaction(eq("cap-ref-fail"), any(), anyString()))
                .thenReturn(Map.of("status", "failed", "raw", Map.of()));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            Optional<Transaction> result = paymentService.refund("cap-ref-fail", new BigDecimal("50.00"), "1111");

            assertTrue(result.isPresent());
            assertEquals("failed", result.get().getStatus());
        }
    }

    @Nested
    class PurchaseTests {

        @Test
        void testPurchaseFailed() {
            when(authorizeNetClient.createTransaction(any(), anyString(), anyMap(), eq(true)))
                .thenReturn(Map.of("status", "failed", "response_code", "2", "raw", Map.of()));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(40L);
                return o;
            });
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(40L);
                return t;
            });

            Transaction tx = paymentService.purchase(new BigDecimal("100.00"), "USD",
                Map.of("number", "4111111111111111"), "ext-fail");

            assertNotNull(tx);
            assertEquals("failed", tx.getStatus());
        }

        @Test
        void testPurchaseWithDifferentCurrency() {
            when(authorizeNetClient.createTransaction(any(), eq("EUR"), anyMap(), eq(true)))
                .thenReturn(Map.of("status", "success", "provider_tx_id", "eur-123", "raw", Map.of()));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> {
                Order o = i.getArgument(0);
                o.setId(41L);
                return o;
            });
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
                Transaction t = i.getArgument(0);
                t.setId(41L);
                return t;
            });

            Transaction tx = paymentService.purchase(new BigDecimal("50.00"), "EUR",
                Map.of("number", "4111111111111111"), "ext-eur");

            assertNotNull(tx);
            assertEquals("success", tx.getStatus());
        }
    }
}

