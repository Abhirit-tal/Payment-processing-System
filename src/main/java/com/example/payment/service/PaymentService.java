package com.example.payment.service;

import com.example.payment.model.Order;
import com.example.payment.model.Transaction;
import com.example.payment.repository.OrderRepository;
import com.example.payment.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class PaymentService {

    private final AuthorizeNetClient authorizeNetClient;
    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;

    public PaymentService(AuthorizeNetClient authorizeNetClient, OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.authorizeNetClient = authorizeNetClient;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public Transaction purchase(BigDecimal amount, String currency, Map<String, String> card, String externalOrderId) {
        Order order = new Order();
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setExternalId(externalOrderId);
        order.setStatus("processing");
        order = orderRepository.save(order);

        Transaction tx = new Transaction();
        tx.setOrder(order);
        tx.setType("purchase");
        tx.setAmount(amount);
        tx.setStatus("pending");
        tx = transactionRepository.save(tx);

        Map<String, Object> resp = authorizeNetClient.createTransaction(amount, currency, card, true);
        String status = (String) resp.getOrDefault("status", "failed");
        tx.setProviderTxId((String) resp.get("provider_tx_id"));
        tx.setRawResponse(resp.get("raw").toString());
        if ("success".equals(status)) {
            tx.setStatus("success");
            order.setStatus("captured");
        } else {
            tx.setStatus("failed");
            order.setStatus("failed");
        }

        transactionRepository.save(tx);
        orderRepository.save(order);
        return tx;
    }

    @Transactional
    public Transaction authorizeOnly(BigDecimal amount, String currency, Map<String, String> card, String externalOrderId) {
        Order order = new Order();
        order.setAmount(amount);
        order.setCurrency(currency);
        order.setExternalId(externalOrderId);
        order.setStatus("processing");
        order = orderRepository.save(order);

        Transaction tx = new Transaction();
        tx.setOrder(order);
        tx.setType("authorize");
        tx.setAmount(amount);
        tx.setStatus("pending");
        tx = transactionRepository.save(tx);

        Map<String, Object> resp = authorizeNetClient.createTransaction(amount, currency, null, false);
        String status = (String) resp.getOrDefault("status", "failed");
        tx.setProviderTxId((String) resp.get("provider_tx_id"));
        tx.setRawResponse(resp.get("raw").toString());
        if ("success".equals(status)) {
            tx.setStatus("success");
            order.setStatus("authorized");
        } else {
            tx.setStatus("failed");
            order.setStatus("failed");
        }

        transactionRepository.save(tx);
        orderRepository.save(order);
        return tx;
    }

    @Transactional
    public Optional<Transaction> capture(String providerAuthTxId, BigDecimal amount) {
        Optional<Transaction> authTxOpt = transactionRepository.findByProviderTxId(providerAuthTxId);
        if (authTxOpt.isEmpty()) return Optional.empty();
        Transaction authTx = authTxOpt.get();
        Map<String, Object> resp = authorizeNetClient.captureTransaction(providerAuthTxId, amount == null ? authTx.getAmount() : amount);
        Transaction captureTx = new Transaction();
        captureTx.setOrder(authTx.getOrder());
        captureTx.setType("capture");
        captureTx.setAmount(amount == null ? authTx.getAmount() : amount);
        captureTx.setStatus((String) resp.getOrDefault("status", "failed"));
        captureTx.setProviderTxId((String) resp.get("provider_tx_id"));
        captureTx.setRawResponse(resp.get("raw").toString());
        transactionRepository.save(captureTx);

        if ("success".equals(captureTx.getStatus())) {
            Order order = authTx.getOrder();
            order.setStatus("captured");
            orderRepository.save(order);
        }

        return Optional.of(captureTx);
    }

    @Transactional
    public Optional<Transaction> voidTransaction(String providerTxId) {
        Optional<Transaction> txOpt = transactionRepository.findByProviderTxId(providerTxId);
        if (txOpt.isEmpty()) return Optional.empty();
        Transaction tx = txOpt.get();
        Map<String, Object> resp = authorizeNetClient.voidTransaction(providerTxId);
        tx.setStatus((String) resp.getOrDefault("status", "failed"));
        tx.setRawResponse(resp.get("raw").toString());
        transactionRepository.save(tx);
        if ("success".equals(tx.getStatus())) {
            Order order = tx.getOrder();
            order.setStatus("cancelled");
            orderRepository.save(order);
        }
        return Optional.of(tx);
    }

    @Transactional
    public Optional<Transaction> refund(String providerCapturedTxId, BigDecimal amount, String last4) {
        Optional<Transaction> capturedOpt = transactionRepository.findByProviderTxId(providerCapturedTxId);
        if (capturedOpt.isEmpty()) return Optional.empty();
        Transaction orig = capturedOpt.get();
        Map<String, Object> resp = authorizeNetClient.refundTransaction(providerCapturedTxId, amount == null ? orig.getAmount() : amount, last4);
        Transaction refundTx = new Transaction();
        refundTx.setOrder(orig.getOrder());
        refundTx.setType("refund");
        refundTx.setAmount(amount == null ? orig.getAmount() : amount);
        refundTx.setStatus((String) resp.getOrDefault("status", "failed"));
        refundTx.setProviderTxId((String) resp.get("provider_tx_id"));
        refundTx.setRawResponse(resp.get("raw").toString());
        transactionRepository.save(refundTx);

        if ("success".equals(refundTx.getStatus())) {
            Order order = orig.getOrder();
            order.setStatus("refunded");
            orderRepository.save(order);
        }

        return Optional.of(refundTx);
    }
}

