package com.example.payment.service;

import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PendingTransactionRetryService.
 */
public class PendingTransactionRetryServiceTest {

    private OrderRepository orderRepository;
    private TransactionRepository transactionRepository;
    private AuthorizeNetClient authorizeNetClient;
    private AuditService auditService;
    private PendingTransactionRetryService retryService;

    @BeforeEach
    void setup() {
        orderRepository = mock(OrderRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        authorizeNetClient = mock(AuthorizeNetClient.class);
        auditService = mock(AuditService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        retryService = new PendingTransactionRetryService(
                orderRepository, transactionRepository, authorizeNetClient, auditService, meterRegistry);
        retryService.initMetrics();

        doNothing().when(auditService).logGatewayCallAsync(any(), any(), any());
        doNothing().when(auditService).logErrorAsync(any(), any(), any(), any());
    }

    private Order createOrder(Long id, PaymentState state, int retryCount) {
        Order order = new Order();
        order.setId(id);
        order.setAmount(new BigDecimal("50.00"));
        order.setCurrency("USD");
        order.setState(state);
        order.setStatus(state.getCode());
        order.setRetryCount(retryCount);
        order.setCreatedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        return order;
    }

    private Transaction createTransaction(Order order, String type, String providerTxId) {
        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setOrder(order);
        tx.setType(type);
        tx.setAmount(order.getAmount());
        tx.setProviderTxId(providerTxId);
        tx.setStatus("pending");
        return tx;
    }

    @Nested
    class RetryPendingTransactions {

        @Test
        void noStalePendingOrders() {
            when(orderRepository.findByStateAndCreatedAtBefore(eq(PaymentState.PENDING), any()))
                    .thenReturn(List.of());

            retryService.retryPendingTransactions();

            verify(transactionRepository, never()).findByOrderId(anyLong());
        }

        @Test
        void retriesStalePendingOrder() {
            Order order = createOrder(1L, PaymentState.PENDING, 0);
            Transaction tx = createTransaction(order, "purchase", "provider-123");

            when(orderRepository.findByStateAndCreatedAtBefore(eq(PaymentState.PENDING), any()))
                    .thenReturn(List.of(order));
            when(transactionRepository.findByOrderId(1L)).thenReturn(List.of(tx));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(authorizeNetClient.getTransactionDetails("provider-123"))
                    .thenReturn(java.util.Map.of("status", "success", "transaction_status", "settledSuccessfully"));
            doNothing().when(auditService).logGatewayResponseAsync(any(), any(), anyBoolean(), any());

            retryService.retryPendingTransactions();

            verify(orderRepository, atLeastOnce()).save(any(Order.class));
            verify(auditService).logGatewayCallAsync(eq(1L), eq("RETRY"), any());
            verify(authorizeNetClient).getTransactionDetails("provider-123");
        }

        @Test
        void exceedsMaxRetriesMarksError() {
            Order order = createOrder(1L, PaymentState.PENDING, 3);

            when(orderRepository.findByStateAndCreatedAtBefore(eq(PaymentState.PENDING), any()))
                    .thenReturn(List.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            retryService.retryPendingTransactions();

            verify(orderRepository).save(argThat(o -> o.getState() == PaymentState.ERROR));
            verify(auditService).logErrorAsync(any(), eq(1L), eq("MAX_RETRIES_EXCEEDED"), any());
        }

        @Test
        void noTransactionsFound() {
            Order order = createOrder(1L, PaymentState.PENDING, 0);

            when(orderRepository.findByStateAndCreatedAtBefore(eq(PaymentState.PENDING), any()))
                    .thenReturn(List.of(order));
            when(transactionRepository.findByOrderId(1L)).thenReturn(List.of());
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            retryService.retryPendingTransactions();

            // Should increment retry count but not crash
            verify(orderRepository, atLeastOnce()).save(any(Order.class));
        }

        @Test
        void noProviderTxIdMarksError() {
            Order order = createOrder(1L, PaymentState.PENDING, 0);
            Transaction tx = createTransaction(order, "purchase", null);

            when(orderRepository.findByStateAndCreatedAtBefore(eq(PaymentState.PENDING), any()))
                    .thenReturn(List.of(order));
            when(transactionRepository.findByOrderId(1L)).thenReturn(List.of(tx));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

            retryService.retryPendingTransactions();

            verify(orderRepository, atLeast(2)).save(any(Order.class));
        }
    }

    @Nested
    class RetryErrorTransactions {

        @Test
        void retriesErrorOrdersUnderMaxRetries() {
            Order order = createOrder(1L, PaymentState.ERROR, 1);
            Transaction tx = createTransaction(order, "purchase", "provider-456");

            when(orderRepository.findByState(PaymentState.ERROR)).thenReturn(List.of(order));
            when(transactionRepository.findByOrderId(1L)).thenReturn(List.of(tx));
            when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
            when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
            when(authorizeNetClient.getTransactionDetails("provider-456"))
                    .thenReturn(java.util.Map.of("status", "success", "transaction_status", "settledSuccessfully"));
            doNothing().when(auditService).logGatewayResponseAsync(any(), any(), anyBoolean(), any());

            retryService.retryErrorTransactions();

            verify(orderRepository, atLeastOnce()).save(any(Order.class));
        }

        @Test
        void skipsErrorOrdersAtMaxRetries() {
            Order order = createOrder(1L, PaymentState.ERROR, 3);

            when(orderRepository.findByState(PaymentState.ERROR)).thenReturn(List.of(order));

            retryService.retryErrorTransactions();

            // Should not retry since retryCount >= MAX_RETRY_ATTEMPTS
            verify(transactionRepository, never()).findByOrderId(anyLong());
        }

        @Test
        void noErrorOrders() {
            when(orderRepository.findByState(PaymentState.ERROR)).thenReturn(List.of());

            retryService.retryErrorTransactions();

            verify(orderRepository, never()).save(any());
        }
    }
}

