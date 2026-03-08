package com.example.payment.service;

import com.example.payment.exception.TransientPaymentException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import net.authorize.Environment;
import net.authorize.api.contract.v1.*;
import net.authorize.api.controller.CreateTransactionController;
import net.authorize.api.controller.ARBCreateSubscriptionController;
import net.authorize.api.controller.ARBGetSubscriptionController;
import net.authorize.api.controller.ARBUpdateSubscriptionController;
import net.authorize.api.controller.ARBCancelSubscriptionController;
import net.authorize.api.controller.GetTransactionDetailsController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Authorize.Net SDK client — thread-safe, with retry and circuit breaker resilience.
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we initialize merchant auth on every call or once at startup?</p>
 * <p><strong>A:</strong> Once at startup via @PostConstruct. The previous approach used static
 * globals (ApiOperationBase.setMerchantAuthentication) which is NOT thread-safe under concurrent
 * requests. We now build a MerchantAuthenticationType once and inject it into each request.</p>
 *
 * <p><strong>Q:</strong> How should we handle transient gateway failures?</p>
 * <p><strong>A:</strong> We apply @Retryable with exponential backoff (500ms initial, 2x multiplier,
 * max 3 attempts) for TransientPaymentException. A Resilience4j circuit breaker prevents cascading
 * failures when the gateway is persistently down.</p>
 */
@Component
public class AuthorizeNetClient {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeNetClient.class);

    @Value("${authnet.api.login.id:}")
    private String apiLoginId;

    @Value("${authnet.transaction.key:}")
    private String transactionKey;

    @Value("${authnet.environment:sandbox}")
    private String environment;

    private MerchantAuthenticationType merchantAuthentication;
    private Environment apiEnvironment;

    /**
     * Initialize merchant credentials once at startup (thread-safe).
     */
    @PostConstruct
    public void init() {
        apiEnvironment = "production".equalsIgnoreCase(environment)
                ? Environment.PRODUCTION : Environment.SANDBOX;

        merchantAuthentication = new MerchantAuthenticationType();
        merchantAuthentication.setName(apiLoginId);
        merchantAuthentication.setTransactionKey(transactionKey);

        log.info("AuthorizeNetClient initialized: env={}, loginId={}***",
                apiEnvironment, apiLoginId != null && apiLoginId.length() > 4
                        ? apiLoginId.substring(0, 4) : "N/A");
    }

    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @CircuitBreaker(name = "authorizeNet", fallbackMethod = "createTransactionFallback")
    public Map<String, Object> createTransaction(BigDecimal amount, String currency, Map<String, String> card, boolean capture) {
        Map<String, Object> resp = new HashMap<>();
        try {
            log.debug("Gateway createTransaction request: amount={}, currency={}, capture={}", amount, currency, capture);

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
            request.setMerchantAuthentication(merchantAuthentication);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            CreateTransactionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                TransactionResponse result = response.getTransactionResponse();
                resp.put("status", "success");
                if (result != null) {
                    resp.put("provider_tx_id", result.getTransId());
                    resp.put("response_code", result.getResponseCode());
                    resp.put("raw", result);
                } else {
                    resp.put("raw", response);
                }
            } else {
                resp.put("status", "failed");
                // Extract response code for decline/error classification
                if (response != null && response.getTransactionResponse() != null) {
                    resp.put("response_code", response.getTransactionResponse().getResponseCode());
                }
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            log.error("Gateway error during createTransaction: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }

    /** Circuit breaker fallback for createTransaction. */
    @SuppressWarnings("unused")
    private Map<String, Object> createTransactionFallback(BigDecimal amount, String currency,
            Map<String, String> card, boolean capture, Throwable t) {
        log.warn("Circuit breaker open for createTransaction: {}", t.getMessage());
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "failed");
        resp.put("raw", Map.of("error", "Payment gateway temporarily unavailable"));
        return resp;
    }

    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @CircuitBreaker(name = "authorizeNet", fallbackMethod = "captureTransactionFallback")
    public Map<String, Object> captureTransaction(String authTransactionId, BigDecimal amount) {
        Map<String, Object> resp = new HashMap<>();
        try {
            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.PRIOR_AUTH_CAPTURE_TRANSACTION.value());
            txnRequest.setRefTransId(authTransactionId);
            if (amount != null) txnRequest.setAmount(amount);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);
            request.setMerchantAuthentication(merchantAuthentication);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.setEnvironment(apiEnvironment);
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
            log.error("Gateway error during captureTransaction: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> captureTransactionFallback(String authTransactionId, BigDecimal amount, Throwable t) {
        log.warn("Circuit breaker open for captureTransaction: {}", t.getMessage());
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "failed");
        resp.put("raw", Map.of("error", "Payment gateway temporarily unavailable"));
        return resp;
    }

    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @CircuitBreaker(name = "authorizeNet", fallbackMethod = "voidTransactionFallback")
    public Map<String, Object> voidTransaction(String providerTxId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.VOID_TRANSACTION.value());
            txnRequest.setRefTransId(providerTxId);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);
            request.setMerchantAuthentication(merchantAuthentication);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.setEnvironment(apiEnvironment);
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
            log.error("Gateway error during voidTransaction: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> voidTransactionFallback(String providerTxId, Throwable t) {
        log.warn("Circuit breaker open for voidTransaction: {}", t.getMessage());
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "failed");
        resp.put("raw", Map.of("error", "Payment gateway temporarily unavailable"));
        return resp;
    }

    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    @CircuitBreaker(name = "authorizeNet", fallbackMethod = "refundTransactionFallback")
    public Map<String, Object> refundTransaction(String providerTxId, BigDecimal amount, String last4) {
        Map<String, Object> resp = new HashMap<>();
        try {
            // For refund, Authorize.Net requires payment info (card's last four)
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(last4);
            // Authorize.Net requires an expiration date for refunds; use a far-future date
            // dynamically calculated to prevent expiry (10 years ahead)
            int futureYear = java.time.Year.now().getValue() + 10;
            creditCard.setExpirationDate(futureYear + "-12");
            PaymentType paymentType = new PaymentType();
            paymentType.setCreditCard(creditCard);

            TransactionRequestType txnRequest = new TransactionRequestType();
            txnRequest.setTransactionType(TransactionTypeEnum.REFUND_TRANSACTION.value());
            txnRequest.setRefTransId(providerTxId);
            txnRequest.setAmount(amount);
            txnRequest.setPayment(paymentType);

            CreateTransactionRequest request = new CreateTransactionRequest();
            request.setTransactionRequest(txnRequest);
            request.setMerchantAuthentication(merchantAuthentication);

            CreateTransactionController controller = new CreateTransactionController(request);
            controller.setEnvironment(apiEnvironment);
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
            log.error("Gateway error during refundTransaction: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }

    @SuppressWarnings("unused")
    private Map<String, Object> refundTransactionFallback(String providerTxId, BigDecimal amount, String last4, Throwable t) {
        log.warn("Circuit breaker open for refundTransaction: {}", t.getMessage());
        Map<String, Object> resp = new HashMap<>();
        resp.put("status", "failed");
        resp.put("raw", Map.of("error", "Payment gateway temporarily unavailable"));
        return resp;
    }

    // ==================== Recurring Billing (ARB) Methods ====================

    /**
     * Create a recurring billing subscription via Authorize.Net ARB API.
     */
    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public Map<String, Object> createSubscription(String name, BigDecimal amount,
            int intervalLength, String intervalUnit,
            String startDate, Map<String, String> card) {
        Map<String, Object> resp = new HashMap<>();
        try {
            // Payment
            CreditCardType creditCard = new CreditCardType();
            creditCard.setCardNumber(card.getOrDefault("number", ""));
            String expYear = card.getOrDefault("expYear", "");
            String expMonth = card.getOrDefault("expMonth", "");
            if (!expYear.isBlank() && !expMonth.isBlank()) {
                creditCard.setExpirationDate(String.format("%04d-%02d",
                        Integer.parseInt(expYear), Integer.parseInt(expMonth)));
            }
            PaymentType payment = new PaymentType();
            payment.setCreditCard(creditCard);

            // Interval
            PaymentScheduleType.Interval interval = new PaymentScheduleType.Interval();
            interval.setLength((short) intervalLength);
            interval.setUnit(ARBSubscriptionUnitEnum.fromValue(intervalUnit.toLowerCase()));

            // Schedule
            PaymentScheduleType schedule = new PaymentScheduleType();
            schedule.setInterval(interval);
            schedule.setStartDate(javax.xml.datatype.DatatypeFactory.newInstance()
                    .newXMLGregorianCalendar(startDate));
            schedule.setTotalOccurrences((short) 9999); // ongoing

            // Subscription
            ARBSubscriptionType subscription = new ARBSubscriptionType();
            subscription.setName(name);
            subscription.setPaymentSchedule(schedule);
            subscription.setAmount(amount);
            subscription.setPayment(payment);

            ARBCreateSubscriptionRequest request = new ARBCreateSubscriptionRequest();
            request.setMerchantAuthentication(merchantAuthentication);
            request.setSubscription(subscription);

            ARBCreateSubscriptionController controller = new ARBCreateSubscriptionController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            ARBCreateSubscriptionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                resp.put("subscription_id", response.getSubscriptionId());
                resp.put("raw", response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            log.error("Gateway error during createSubscription: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }

    /**
     * Get subscription status from Authorize.Net ARB.
     */
    public Map<String, Object> getSubscription(String subscriptionId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            ARBGetSubscriptionRequest request = new ARBGetSubscriptionRequest();
            request.setMerchantAuthentication(merchantAuthentication);
            request.setSubscriptionId(subscriptionId);

            ARBGetSubscriptionController controller = new ARBGetSubscriptionController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            ARBGetSubscriptionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                resp.put("subscription", response.getSubscription());
                resp.put("raw", response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            log.error("Gateway error during getSubscription: {}", ex.getMessage());
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }

    /**
     * Update an existing subscription.
     */
    public Map<String, Object> updateSubscription(String subscriptionId, String name, BigDecimal amount) {
        Map<String, Object> resp = new HashMap<>();
        try {
            ARBSubscriptionType subscription = new ARBSubscriptionType();
            if (name != null) subscription.setName(name);
            if (amount != null) subscription.setAmount(amount);

            ARBUpdateSubscriptionRequest request = new ARBUpdateSubscriptionRequest();
            request.setMerchantAuthentication(merchantAuthentication);
            request.setSubscriptionId(subscriptionId);
            request.setSubscription(subscription);

            ARBUpdateSubscriptionController controller = new ARBUpdateSubscriptionController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            ARBUpdateSubscriptionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                resp.put("raw", response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            log.error("Gateway error during updateSubscription: {}", ex.getMessage());
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }

    /**
     * Cancel a subscription.
     */
    public Map<String, Object> cancelSubscription(String subscriptionId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            ARBCancelSubscriptionRequest request = new ARBCancelSubscriptionRequest();
            request.setMerchantAuthentication(merchantAuthentication);
            request.setSubscriptionId(subscriptionId);

            ARBCancelSubscriptionController controller = new ARBCancelSubscriptionController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            ARBCancelSubscriptionResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                resp.put("raw", response);
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
            }
        } catch (Exception ex) {
            log.error("Gateway error during cancelSubscription: {}", ex.getMessage());
            resp.put("status", "failed");
            resp.put("raw", Map.of("error", ex.getMessage()));
        }
        return resp;
    }

    // ==================== Transaction Query Methods ====================

    /**
     * Get transaction details from Authorize.Net for reconciliation.
     * Used by PendingTransactionRetryService to check actual gateway status
     * of pending/error transactions.
     *
     * @param transactionId The provider transaction ID to query
     * @return Map containing status, transaction_status, and raw response
     */
    @Retryable(retryFor = TransientPaymentException.class, maxAttempts = 3,
            backoff = @Backoff(delay = 500, multiplier = 2))
    public Map<String, Object> getTransactionDetails(String transactionId) {
        Map<String, Object> resp = new HashMap<>();
        try {
            GetTransactionDetailsRequest request = new GetTransactionDetailsRequest();
            request.setMerchantAuthentication(merchantAuthentication);
            request.setTransId(transactionId);

            GetTransactionDetailsController controller = new GetTransactionDetailsController(request);
            controller.setEnvironment(apiEnvironment);
            controller.execute();
            GetTransactionDetailsResponse response = controller.getApiResponse();

            if (response != null && response.getMessages().getResultCode() == MessageTypeEnum.OK) {
                resp.put("status", "success");
                TransactionDetailsType txDetails = response.getTransaction();
                if (txDetails != null) {
                    resp.put("transaction_status", txDetails.getTransactionStatus());
                    resp.put("response_code", String.valueOf(txDetails.getResponseCode()));
                    resp.put("settle_amount", txDetails.getSettleAmount());
                    resp.put("transaction_type", txDetails.getTransactionType());
                    resp.put("raw", txDetails);
                    log.info("Transaction {} status at gateway: {}", transactionId, txDetails.getTransactionStatus());
                } else {
                    resp.put("raw", response);
                }
            } else {
                resp.put("status", "failed");
                resp.put("raw", response);
                log.warn("Failed to get transaction details for {}", transactionId);
            }
        } catch (Exception ex) {
            log.error("Gateway error during getTransactionDetails: {}", ex.getMessage());
            throw new TransientPaymentException("Gateway communication error: " + ex.getMessage(), ex);
        }
        return resp;
    }
}
