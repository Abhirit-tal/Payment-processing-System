package com.example.payment.controller;

import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class PaymentControllerNotFoundTest {

    private MockMvc mockMvc;
    private PaymentService paymentService;

    @BeforeEach
    public void setup() {
        paymentService = mock(PaymentService.class);
        when(paymentService.capture("missing", null)).thenReturn(java.util.Optional.empty());
        when(paymentService.voidTransaction("missing")).thenReturn(java.util.Optional.empty());
        when(paymentService.refund("missing", null, null)).thenReturn(java.util.Optional.empty());
        PaymentController controller = new PaymentController(paymentService);
        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    public void testCaptureNotFound() throws Exception {
        mockMvc.perform(post("/payments/capture").contentType(MediaType.APPLICATION_JSON).content("{\"transactionId\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCancelNotFound() throws Exception {
        mockMvc.perform(post("/payments/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"transactionId\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testRefundNotFound() throws Exception {
        mockMvc.perform(post("/payments/refund").contentType(MediaType.APPLICATION_JSON).content("{\"transactionId\":\"missing\"}"))
                .andExpect(status().isNotFound());
    }
}

