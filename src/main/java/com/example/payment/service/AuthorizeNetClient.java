package com.example.payment.service;

import net.authorize.Environment;
import net.authorize.api.contract.v1.*;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.base.ApiOperationBase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class AuthorizeNetClient {

    @Value("${authnet.api.login.id:}")
    private String apiLoginId;

    @Value("${authnet.transaction.key:}")
    private String transactionKey;

    @Value("${authnet.environment:sandbox}")
    private String environment;

    private void initMerchant() {
        // set environment
        if ("production".equalsIgnoreCase(environment)) {
            ApiOperationBase.setEnvironment(Environment.PRODUCTION);
        } else {
            ApiOperationBase.setEnvironment(Environment.SANDBOX);
        }
        // merchant auth
        MerchantAuthenticationType merchantAuthentication = new MerchantAuthenticationType();
        merchantAuthentication.setName(apiLoginId);
        merchantAuthentication.setTransactionKey(transactionKey);
        ApiOperationBase.setMerchantAuthentication(merchantAuthentication);
    }

    public Map<String, Object> createTransaction(BigDecimal amount, String currency, Map<String, String> card, boolean capture) {
        Map<String, Object> resp = new HashMap<>();
        try {
            initMerchant();

            // Build payment data if card provided
            PaymentType paymentType = null;
            if (card != null) {
                CreditCardType creditCard = new CreditCardType();
                creditCard.setCardNumber(card.getOrDefault("number", ""));
                // expiration in format YYYY-MM
                String expYear = card.getOrDefault("expYear", "");
                String expMonth = card.getOrDefault("expMonth", "");
                if (!expYear.isBlank() && !expMonth.isBlank()) {
                    try {
                        int y = Integer.parseInt(expYear);
                        int m = Integer.parseInt(expMonth);
                        creditCard.setExpirationDate(String.format("%04d-%02d", y, m));
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (card.get("cvv") != null) creditCard.setCardCode(card.get("cvv"));
                paymentType = new PaymentType();
                paymentType.setCreditCard(creditCard);
            }

            // transaction request
            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(capture ? TransactionTypeEnum.AUTH_CAPTURE_TRANSACTION.value() : TransactionTypeEnum.AUTH_ONLY_TRANSACTION.value());
            txnRequest.setAmount(amount);
            if (paymentType != null) txnRequest.setPayment(paymentType);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                TransactionResponse result = response.getTransactionResponse();
                resp.put("status", "success");
                if (result != null) {
                    resp.put("provider_tx_id", result.getTransId());
                    resp.put("raw", result);
                } else {
                    resp.put("raw", response);
                }
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }

        return resp;
    }

    public Map<String, Object> captureTransaction(String authTransactionId, BigDecimal amount) {
        Map<String, Object> resp = new HashMap<>();
        try {
            initMerchant();

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.PRIOR_AUTH_CAPTURE_TRANSACTION.value());
            txnRequest.setRefTransId(authTransactionId);
            if (amount != null) txnRequest.setAmount(amount);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                TransactionResponse result = response.getTransactionResponse();
                resp.put("status", "success");
                if (result != null) resp.put("provider_tx_id", result.getTransId());
                resp.put("raw", result != null ? result : response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }

    public Map<String, Object> voidTransaction(String providerTxId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            initMerchant();

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
            txnRequest.setRefTransId(providerTxId);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                resp.put("provider_tx_id", providerTxId);
                resp.put("raw", response.getTransactionResponse());
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }

    public Map<String, Object> refundTransaction(String providerTxId, BigDecimal amount, String last4) {
        Map<String, Object> resp = new HashMap<>();
        try {
            initMerchant();

            // For refund, Authorize.Net requires payment info (card's last four)
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(last4);
            // some flows require expiration date; set a dummy if not provided
            creditCard.setExpirationDate("2030-12");
            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
            txnRequest.setRefTransId(providerTxId);
            txnRequest.setAmount(amount);
            txnRequest.setPayment(paymentType);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                TransactionResponse result = response.getTransactionResponse();
                resp.put("status", "success");
                if (result != null) resp.put("provider_tx_id", result.getTransId());
                resp.put("raw", result != null ? result : response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }
}
