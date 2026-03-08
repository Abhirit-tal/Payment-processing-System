package com.example.payment.controller;

import com.example.payment.exception.InvalidStateTransitionException;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.service.IdempotencyService;
import com.example.payment.service.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Extended controller tests for PaymentController.
 */
public class PaymentControllerExtendedTest {

    private MockMvc mockMvc;
    private PaymentService paymentService;
    private IdempotencyService idempotencyService;

    @BeforeEach
    void setup() {
        paymentService = mock(PaymentService.class);
        idempotencyService = mock(IdempotencyService.class);
        PaymentController controller = new PaymentController(paymentService, idempotencyService, new ObjectMapper());
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Nested
    class HealthEndpointTests {

        @Test
        void testHealthEndpoint() throws Exception {
            mockMvc.perform(get("/payments/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ok"));
        }
    }

    @Nested
    class PurchaseEndpointTests {

        @Test
        void testPurchaseReturnsNullTransaction() throws Exception {
            when(paymentService.purchase(any(BigDecimal.class), anyString(), any(Map.class), anyString()))
                    .thenReturn(null);

            String payload = "{\"amount\":12.34,\"currency\":\"USD\",\"card\":{\"number\":\"4111111111111111\",\"expMonth\":12,\"expYear\":2030,\"cvv\":\"123\"},\"orderId\":\"ext-100\"}";

            mockMvc.perform(post("/payments/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value("payment provider error"));
        }

        @Test
        void testPurchaseTransactionWithNoOrder() throws Exception {
            Transaction tx = new Transaction();
            tx.setId(1L);
            tx.setOrder(null); // No order attached

            when(paymentService.purchase(any(BigDecimal.class), anyString(), any(Map.class), anyString()))
                    .thenReturn(tx);

            String payload = "{\"amount\":12.34,\"currency\":\"USD\",\"card\":{\"number\":\"4111111111111111\",\"expMonth\":12,\"expYear\":2030,\"cvv\":\"123\"},\"orderId\":\"ext-100\"}";

            mockMvc.perform(post("/payments/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.detail").value("internal persistence error"));
        }

        @Test
        void testPurchaseWithoutOrderId() throws Exception {
            Order order = createOrder(100L, PaymentState.CAPTURED);
            Transaction tx = createTransaction(order, "prov-999", "success");

            when(paymentService.purchase(any(BigDecimal.class), anyString(), any(Map.class), isNull()))
                    .thenReturn(tx);

            String payload = "{\"amount\":50.00,\"currency\":\"USD\",\"card\":{\"number\":\"4111111111111111\",\"expMonth\":12,\"expYear\":2030,\"cvv\":\"123\"}}";

            mockMvc.perform(post("/payments/purchase")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    class AuthorizeEndpointTests {

        @Test
        void testAuthorizeSuccess() throws Exception {
            Order order = createOrder(200L, PaymentState.AUTHORIZED);
            Transaction tx = createTransaction(order, "auth-001", "success");

            when(paymentService.authorizeOnly(any(BigDecimal.class), anyString(), any(Map.class), anyString()))
                    .thenReturn(tx);

            String payload = "{\"amount\":75.00,\"currency\":\"USD\",\"card\":{\"number\":\"4111111111111111\",\"expMonth\":12,\"expYear\":2030,\"cvv\":\"123\"},\"orderId\":\"ext-auth\"}";

            mockMvc.perform(post("/payments/authorize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.order_id").value(200))
                    .andExpect(jsonPath("$.transaction_id").value("auth-001"))
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void testAuthorizeValidationError() throws Exception {
            String payload = "{\"amount\":-1,\"currency\":\"USD\",\"card\":{\"number\":\"invalid\",\"expMonth\":13,\"expYear\":2020,\"cvv\":\"12\"}}";

            mockMvc.perform(post("/payments/authorize")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class CaptureEndpointTests {

        @Test
        void testCaptureSuccess() throws Exception {
            Order order = createOrder(300L, PaymentState.CAPTURED);
            Transaction tx = createTransaction(order, "cap-001", "success");

            when(paymentService.capture(eq("auth-to-capture"), any()))
                    .thenReturn(Optional.of(tx));

            String payload = "{\"transactionId\":\"auth-to-capture\",\"amount\":50.00}";

            mockMvc.perform(post("/payments/capture")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value("cap-001"))
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void testCaptureNotFound() throws Exception {
            when(paymentService.capture(eq("nonexistent"), any()))
                    .thenReturn(Optional.empty());

            String payload = "{\"transactionId\":\"nonexistent\"}";

            mockMvc.perform(post("/payments/capture")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("transaction not found"));
        }

        @Test
        void testCaptureWithoutAmount() throws Exception {
            Order order = createOrder(301L, PaymentState.CAPTURED);
            Transaction tx = createTransaction(order, "cap-002", "success");

            when(paymentService.capture(eq("auth-no-amount"), isNull()))
                    .thenReturn(Optional.of(tx));

            String payload = "{\"transactionId\":\"auth-no-amount\"}";

            mockMvc.perform(post("/payments/capture")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    class CancelEndpointTests {

        @Test
        void testCancelSuccess() throws Exception {
            Order order = createOrder(400L, PaymentState.VOIDED);
            Transaction tx = createTransaction(order, "void-001", "success");

            when(paymentService.voidTransaction(eq("auth-to-void")))
                    .thenReturn(Optional.of(tx));

            String payload = "{\"transactionId\":\"auth-to-void\"}";

            mockMvc.perform(post("/payments/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transaction_id").value("void-001"))
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void testCancelNotFound() throws Exception {
            when(paymentService.voidTransaction(eq("nonexistent-void")))
                    .thenReturn(Optional.empty());

            String payload = "{\"transactionId\":\"nonexistent-void\"}";

            mockMvc.perform(post("/payments/cancel")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("transaction not found"));
        }
    }

    @Nested
    class RefundEndpointTests {

        @Test
        void testRefundSuccess() throws Exception {
            Order order = createOrder(500L, PaymentState.REFUNDED);
            Transaction tx = createTransaction(order, "ref-001", "success");

            when(paymentService.refund(eq("cap-to-refund"), any(), eq("1111")))
                    .thenReturn(Optional.of(tx));

            String payload = "{\"transactionId\":\"cap-to-refund\",\"amount\":25.00,\"last4\":\"1111\"}";

            mockMvc.perform(post("/payments/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.refund_transaction_id").value("ref-001"))
                    .andExpect(jsonPath("$.status").value("success"));
        }

        @Test
        void testRefundNotFound() throws Exception {
            when(paymentService.refund(eq("nonexistent-cap"), any(), any()))
                    .thenReturn(Optional.empty());

            String payload = "{\"transactionId\":\"nonexistent-cap\",\"last4\":\"1234\"}";

            mockMvc.perform(post("/payments/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.detail").value("original transaction not found"));
        }

        @Test
        void testRefundPartial() throws Exception {
            Order order = createOrder(501L, PaymentState.PARTIALLY_REFUNDED);
            Transaction tx = createTransaction(order, "ref-partial", "success");

            when(paymentService.refund(eq("cap-partial"), eq(new BigDecimal("10.00")), eq("5678")))
                    .thenReturn(Optional.of(tx));

            String payload = "{\"transactionId\":\"cap-partial\",\"amount\":10.00,\"last4\":\"5678\"}";

            mockMvc.perform(post("/payments/refund")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isOk());
        }
    }

    // Helper methods

    private Order createOrder(Long id, PaymentState state) {
        Order order = new Order();
        order.setId(id);
        order.setAmount(new BigDecimal("100.00"));
        order.setCurrency("USD");
        order.setState(state);
        order.setStatus(state.getCode());
        return order;
    }

    private Transaction createTransaction(Order order, String providerTxId, String status) {
        Transaction tx = new Transaction();
        tx.setId(order.getId());
        tx.setOrder(order);
        tx.setProviderTxId(providerTxId);
        tx.setAmount(order.getAmount());
        tx.setStatus(status);
        return tx;
    }
}

