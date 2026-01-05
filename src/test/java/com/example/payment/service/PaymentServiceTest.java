package com.example.payment.service;

import com.example.payment.model.Order;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class PaymentServiceTest {

    private AuthorizeNetClient authorizeNetClient;
    private OrderRepository orderRepository;
    private TransactionRepository transactionRepository;
    private PaymentService paymentService;

    @BeforeEach
    public void setup() {
        authorizeNetClient = Mockito.mock(AuthorizeNetClient.class);
        orderRepository = Mockito.mock(OrderRepository.class);
        transactionRepository = Mockito.mock(TransactionRepository.class);
        paymentService = new PaymentService(authorizeNetClient, orderRepository, transactionRepository);
    }

    @Test
    public void testPurchaseSuccessCreatesOrderAndTransaction() {
        when(authorizeNetClient.createTransaction(any(), anyString(), anyMap(), eq(true))).thenReturn(Map.of("status", "success", "provider_tx_id", "12345", "raw", Map.of()));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(1L);
            return t;
        });

        Transaction tx = paymentService.purchase(new BigDecimal("10.00"), "USD", Map.of("number", "4111111111111111"), "ext-1");
        assertNotNull(tx);
        assertEquals("success", tx.getStatus());
        assertEquals("12345", tx.getProviderTxId());
    }

    @Test
    public void testRefundFailsWhenOriginalMissing() {
        when(transactionRepository.findByProviderTxId("nonexistent")).thenReturn(Optional.empty());
        Optional<Transaction> opt = paymentService.refund("nonexistent", new BigDecimal("5.00"), "1111");
        assertTrue(opt.isEmpty());
    }

    @Test
    public void testCaptureSuccessUpdatesOrder() {
        Order order = new Order();
        order.setId(10L);
        order.setAmount(new BigDecimal("20.00"));
        order.setStatus("authorized");

        Transaction authTx = new Transaction();
        authTx.setId(2L);
        authTx.setOrder(order);
        authTx.setAmount(new BigDecimal("20.00"));
        authTx.setProviderTxId("auth-1");
        authTx.setStatus("success");

        when(transactionRepository.findByProviderTxId("auth-1")).thenReturn(Optional.of(authTx));
        when(authorizeNetClient.captureTransaction(eq("auth-1"), any())).thenReturn(Map.of("status", "success", "provider_tx_id", "cap-1", "raw", Map.of()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Transaction> capOpt = paymentService.capture("auth-1", null);
        assertTrue(capOpt.isPresent());
        Transaction capTx = capOpt.get();
        assertEquals("success", capTx.getStatus());
        assertEquals("cap-1", capTx.getProviderTxId());
        assertEquals("captured", capTx.getOrder().getStatus());
    }

    @Test
    public void testVoidSuccessCancelsOrder() {
        Order order = new Order();
        order.setId(11L);
        order.setAmount(new BigDecimal("15.00"));
        order.setStatus("authorized");

        Transaction tx = new Transaction();
        tx.setId(3L);
        tx.setOrder(order);
        tx.setProviderTxId("auth-2");
        tx.setAmount(new BigDecimal("15.00"));
        tx.setStatus("success");

        when(transactionRepository.findByProviderTxId("auth-2")).thenReturn(Optional.of(tx));
        when(authorizeNetClient.voidTransaction("auth-2")).thenReturn(Map.of("status", "success", "provider_tx_id", "auth-2", "raw", Map.of()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Transaction> out = paymentService.voidTransaction("auth-2");
        assertTrue(out.isPresent());
        assertEquals("success", out.get().getStatus());
        assertEquals("cancelled", out.get().getOrder().getStatus());
    }

    @Test
    public void testRefundSuccessCreatesRefundTransaction() {
        Order order = new Order();
        order.setId(12L);
        order.setAmount(new BigDecimal("30.00"));
        order.setStatus("captured");

        Transaction captured = new Transaction();
        captured.setId(4L);
        captured.setOrder(order);
        captured.setProviderTxId("cap-2");
        captured.setAmount(new BigDecimal("30.00"));
        captured.setStatus("success");

        when(transactionRepository.findByProviderTxId("cap-2")).thenReturn(Optional.of(captured));
        when(authorizeNetClient.refundTransaction(eq("cap-2"), any(), anyString())).thenReturn(Map.of("status", "success", "provider_tx_id", "ref-1", "raw", Map.of()));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        Optional<Transaction> refundOpt = paymentService.refund("cap-2", new BigDecimal("10.00"), "1111");
        assertTrue(refundOpt.isPresent());
        Transaction r = refundOpt.get();
        assertEquals("success", r.getStatus());
        assertEquals("ref-1", r.getProviderTxId());
        assertEquals("refunded", r.getOrder().getStatus());
    }
}
