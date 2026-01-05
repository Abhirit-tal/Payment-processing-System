package com.example.payment.controller;

import com.example.payment.model.Order;
import com.example.payment.model.Transaction;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PaymentControllerTest {

    private MockMvc mockMvc;
    private PaymentService paymentService;

    @BeforeEach
    public void setup() {
        paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(paymentService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testPurchaseBadRequest() throws Exception {
        mockMvc.perform(post("/payments/purchase").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testPurchaseSuccess() throws Exception {
        // prepare mocked Transaction + Order
        Order order = new Order();
        order.setId(100L);
        order.setAmount(new BigDecimal("12.34"));
        order.setCurrency("USD");
        order.setStatus("captured");

        Transaction tx = new Transaction();
        tx.setId(200L);
        tx.setOrder(order);
        tx.setProviderTxId("prov-123");
        tx.setAmount(new BigDecimal("12.34"));
        tx.setStatus("success");

        when(paymentService.purchase(any(BigDecimal.class), anyString(), any(Map.class), anyString())).thenReturn(tx);

        String payload = "{\"amount\":12.34,\"currency\":\"USD\",\"card\":{\"number\":\"4111111111111111\",\"expMonth\":12,\"expYear\":2030,\"cvv\":\"123\"},\"orderId\":\"ext-100\"}";

        mockMvc.perform(post("/payments/purchase").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transaction_id").value("prov-123"))
                .andExpect(jsonPath("$.status").value("success"));
    }
}
