package com.example.payment.service;

import com.example.payment.event.PaymentEvent;
import com.example.payment.event.PaymentEventQueue;
import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionService.
 */
public class SubscriptionServiceTest {

    private AuthorizeNetClient authorizeNetClient;
    private SubscriptionRepository subscriptionRepository;
    private AuditService auditService;
    private PaymentEventQueue eventQueue;
    private SubscriptionService subscriptionService;

    @BeforeEach
    void setup() {
        authorizeNetClient = mock(AuthorizeNetClient.class);
        subscriptionRepository = mock(SubscriptionRepository.class);
        auditService = mock(AuditService.class);
        eventQueue = mock(PaymentEventQueue.class);
        subscriptionService = new SubscriptionService(authorizeNetClient, subscriptionRepository, auditService, eventQueue);
    }

    private Subscription createMockSubscription(Long id, String status) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setGatewaySubscriptionId("gw-" + id);
        sub.setName("Plan");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setIntervalLength(1);
        sub.setIntervalUnit("months");
        sub.setStartDate(LocalDate.of(2026, 4, 1));
        sub.setStatus(status);
        return sub;
    }

    @Nested
    class CreateSubscriptionTests {

        @Test
        void createSubscriptionSuccess() {
            when(authorizeNetClient.createSubscription(anyString(), any(), anyInt(), anyString(), anyString(), anyMap()))
                    .thenReturn(Map.of("status", "success", "subscription_id", "12345"));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setId(1L);
                return s;
            });
            when(eventQueue.publish(any())).thenReturn(true);

            Map<String, String> card = Map.of("number", "4111111111111111", "expMonth", "12", "expYear", "2030", "cvv", "123");
            Subscription result = subscriptionService.createSubscription("Monthly", new BigDecimal("29.99"),
                    1, "months", "2026-04-01", card);

            assertNotNull(result);
            assertEquals("active", result.getStatus());
            assertEquals("12345", result.getGatewaySubscriptionId());
            verify(subscriptionRepository).save(any());
            verify(eventQueue).publish(any());
        }

        @Test
        void createSubscriptionFailure() {
            when(authorizeNetClient.createSubscription(anyString(), any(), anyInt(), anyString(), anyString(), anyMap()))
                    .thenReturn(Map.of("status", "failed"));
            when(subscriptionRepository.save(any(Subscription.class))).thenAnswer(i -> {
                Subscription s = i.getArgument(0);
                s.setId(1L);
                return s;
            });
            when(eventQueue.publish(any())).thenReturn(true);

            Map<String, String> card = Map.of("number", "4111111111111111", "expMonth", "12", "expYear", "2030", "cvv", "123");
            Subscription result = subscriptionService.createSubscription("Monthly", new BigDecimal("29.99"),
                    1, "months", "2026-04-01", card);

            assertEquals("failed", result.getStatus());
        }
    }

    @Nested
    class GetSubscriptionTests {

        @Test
        void getSubscriptionFound() {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));

            Optional<Subscription> result = subscriptionService.getSubscription(1L);
            assertTrue(result.isPresent());
            assertEquals("active", result.get().getStatus());
        }

        @Test
        void getSubscriptionNotFound() {
            when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
            assertTrue(subscriptionService.getSubscription(99L).isEmpty());
        }
    }

    @Nested
    class UpdateSubscriptionTests {

        @Test
        void updateSubscriptionSuccess() {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
            when(authorizeNetClient.updateSubscription(anyString(), anyString(), any()))
                    .thenReturn(Map.of("status", "success"));
            when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            Optional<Subscription> result = subscriptionService.updateSubscription(1L, "Updated", new BigDecimal("49.99"));
            assertTrue(result.isPresent());
            assertEquals("Updated", result.get().getName());
            assertEquals(new BigDecimal("49.99"), result.get().getAmount());
        }

        @Test
        void updateSubscriptionNotFound() {
            when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
            assertTrue(subscriptionService.updateSubscription(99L, "Name", null).isEmpty());
        }

        @Test
        void updateSubscriptionGatewayFails() {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
            when(authorizeNetClient.updateSubscription(anyString(), anyString(), any()))
                    .thenReturn(Map.of("status", "failed"));

            assertTrue(subscriptionService.updateSubscription(1L, "Updated", null).isEmpty());
        }
    }

    @Nested
    class CancelSubscriptionTests {

        @Test
        void cancelSubscriptionSuccess() {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
            when(authorizeNetClient.cancelSubscription(anyString()))
                    .thenReturn(Map.of("status", "success"));
            when(subscriptionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
            when(eventQueue.publish(any())).thenReturn(true);

            Optional<Subscription> result = subscriptionService.cancelSubscription(1L);
            assertTrue(result.isPresent());
            assertEquals("cancelled", result.get().getStatus());
            assertNotNull(result.get().getCancelledAt());
        }

        @Test
        void cancelSubscriptionNotFound() {
            when(subscriptionRepository.findById(99L)).thenReturn(Optional.empty());
            assertTrue(subscriptionService.cancelSubscription(99L).isEmpty());
        }

        @Test
        void cancelSubscriptionGatewayFails() {
            Subscription sub = createMockSubscription(1L, "active");
            when(subscriptionRepository.findById(1L)).thenReturn(Optional.of(sub));
            when(authorizeNetClient.cancelSubscription(anyString()))
                    .thenReturn(Map.of("status", "failed"));

            assertTrue(subscriptionService.cancelSubscription(1L).isEmpty());
        }
    }
}

