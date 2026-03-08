package com.example.payment.service;

import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SubscriptionScheduler.
 */
public class SubscriptionSchedulerTest {

    private SubscriptionRepository subscriptionRepository;
    private AuthorizeNetClient authorizeNetClient;
    private SubscriptionScheduler scheduler;

    @BeforeEach
    void setup() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        authorizeNetClient = mock(AuthorizeNetClient.class);
        scheduler = new SubscriptionScheduler(subscriptionRepository, authorizeNetClient);
    }

    @Test
    void syncSubscriptionStatusesWithActiveSubscriptions() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setGatewaySubscriptionId("gw-123");
        sub.setName("Test");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setStartDate(LocalDate.of(2026, 1, 1));
        sub.setStatus("active");

        when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));
        when(authorizeNetClient.getSubscription("gw-123"))
                .thenReturn(Map.of("status", "success", "subscription", new Object()));

        scheduler.syncSubscriptionStatuses();

        verify(authorizeNetClient).getSubscription("gw-123");
    }

    @Test
    void syncSubscriptionStatusesHandlesGatewayFailure() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setGatewaySubscriptionId("gw-456");
        sub.setName("Test");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setStartDate(LocalDate.of(2026, 1, 1));
        sub.setStatus("active");

        when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));
        when(authorizeNetClient.getSubscription("gw-456"))
                .thenReturn(Map.of("status", "failed"));

        scheduler.syncSubscriptionStatuses();

        verify(authorizeNetClient).getSubscription("gw-456");
    }

    @Test
    void syncSubscriptionStatusesHandlesException() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setGatewaySubscriptionId("gw-789");
        sub.setName("Test");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setStartDate(LocalDate.of(2026, 1, 1));
        sub.setStatus("active");

        when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));
        when(authorizeNetClient.getSubscription("gw-789"))
                .thenThrow(new RuntimeException("network error"));

        scheduler.syncSubscriptionStatuses();

        verify(authorizeNetClient).getSubscription("gw-789");
    }

    @Test
    void syncSubscriptionStatusesSkipsNullGatewayId() {
        Subscription sub = new Subscription();
        sub.setId(1L);
        sub.setGatewaySubscriptionId(null);
        sub.setName("Test");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setStartDate(LocalDate.of(2026, 1, 1));
        sub.setStatus("active");

        when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));

        scheduler.syncSubscriptionStatuses();

        verify(authorizeNetClient, never()).getSubscription(any());
    }

    @Test
    void syncSubscriptionStatusesWithNoActiveSubscriptions() {
        when(subscriptionRepository.findByStatus("active")).thenReturn(List.of());

        scheduler.syncSubscriptionStatuses();

        verify(authorizeNetClient, never()).getSubscription(any());
    }

    @Test
    void cleanupExpiredIdempotencyKeysDoesNotThrow() {
        scheduler.cleanupExpiredIdempotencyKeys();
        // Just verifies the method runs without error
    }
}

