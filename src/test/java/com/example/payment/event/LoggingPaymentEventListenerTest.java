package com.example.payment.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LoggingPaymentEventListener.
 */
@ExtendWith(MockitoExtension.class)
class LoggingPaymentEventListenerTest {

    @Mock
    private PaymentEventQueue eventQueue;

    @Test
    void registerAddsItselfAsListener() {
        LoggingPaymentEventListener listener = new LoggingPaymentEventListener(eventQueue);
        listener.register();
        verify(eventQueue).addListener(listener);
    }

    @Test
    void onEventDoesNotThrow() {
        LoggingPaymentEventListener listener = new LoggingPaymentEventListener(eventQueue);
        PaymentEvent event = PaymentEvent.of(PaymentEvent.PURCHASE_SUCCESS, 1L, "tx-123");

        assertDoesNotThrow(() -> listener.onEvent(event));
    }

    @Test
    void onEventHandlesNullFields() {
        LoggingPaymentEventListener listener = new LoggingPaymentEventListener(eventQueue);
        PaymentEvent event = PaymentEvent.of("UNKNOWN_TYPE", null, null);

        assertDoesNotThrow(() -> listener.onEvent(event));
    }
}

