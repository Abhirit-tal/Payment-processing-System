package com.example.payment.service;

import com.example.payment.model.Subscription;
import com.example.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BillingCycleService — recurring billing scheduler.
 */
public class BillingCycleServiceTest {

    private SubscriptionRepository subscriptionRepository;
    private AuthorizeNetClient authorizeNetClient;
    private AuditService auditService;
    private BillingCycleService billingCycleService;

    @BeforeEach
    void setup() {
        subscriptionRepository = mock(SubscriptionRepository.class);
        authorizeNetClient = mock(AuthorizeNetClient.class);
        auditService = mock(AuditService.class);
        billingCycleService = new BillingCycleService(
                subscriptionRepository, authorizeNetClient, auditService);

        doNothing().when(auditService).logGatewayCallAsync(any(), any(), any());
        doNothing().when(auditService).logErrorAsync(any(), any(), any(), any());
    }

    private Subscription createSubscription(Long id, String status, LocalDate nextBilling,
                                             int failures, String gatewayId) {
        Subscription sub = new Subscription();
        sub.setId(id);
        sub.setName("Test Plan");
        sub.setAmount(new BigDecimal("29.99"));
        sub.setIntervalLength(1);
        sub.setIntervalUnit("months");
        sub.setStartDate(LocalDate.of(2026, 1, 1));
        sub.setStatus(status);
        sub.setNextBillingDate(nextBilling);
        sub.setBillingFailures(failures);
        sub.setGatewaySubscriptionId(gatewayId);
        sub.setCreatedAt(Instant.now());
        sub.setUpdatedAt(Instant.now());
        return sub;
    }

    @Nested
    @DisplayName("processBillingCycles")
    class ProcessBillingCyclesTests {

        @Test
        @DisplayName("No subscriptions due — no processing")
        void noDueSubscriptions() {
            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of());

            billingCycleService.processBillingCycles();

            verify(authorizeNetClient, never()).getSubscription(any());
        }

        @Test
        @DisplayName("Due subscription — gateway success — advances billing cycle")
        void dueSubscriptionSuccess() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.now().minusDays(1), 0, "gw-123");

            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of(sub));
            when(authorizeNetClient.getSubscription("gw-123"))
                    .thenReturn(Map.of("status", "success"));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.processBillingCycles();

            verify(subscriptionRepository, atLeastOnce()).save(any(Subscription.class));
            assertEquals(1, sub.getTotalBilled());
            assertNotNull(sub.getLastBilledAt());
            assertEquals(0, sub.getBillingFailures());
        }

        @Test
        @DisplayName("Due subscription — gateway failure — increments failure count")
        void dueSubscriptionGatewayFailure() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.now().minusDays(1), 0, "gw-456");

            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of(sub));
            when(authorizeNetClient.getSubscription("gw-456"))
                    .thenReturn(Map.of("status", "failed"));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.processBillingCycles();

            assertEquals(1, sub.getBillingFailures());
        }

        @Test
        @DisplayName("Due subscription — exceeds max billing failures — suspended")
        void maxBillingFailuresSuspends() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.now().minusDays(1), 3, "gw-789");

            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of(sub));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.processBillingCycles();

            assertEquals("suspended", sub.getStatus());
            verify(auditService).logErrorAsync(eq("SUBSCRIPTION"), eq(1L),
                    eq("MAX_BILLING_FAILURES"), any());
        }

        @Test
        @DisplayName("Due subscription without gateway ID — advances locally")
        void noGatewayIdAdvancesLocally() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.now().minusDays(1), 0, null);

            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of(sub));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.processBillingCycles();

            assertEquals(1, sub.getTotalBilled());
        }

        @Test
        @DisplayName("Gateway exception — increments failure count")
        void gatewayException() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.now().minusDays(1), 1, "gw-error");

            when(subscriptionRepository.findSubscriptionsDueForBilling(any()))
                    .thenReturn(List.of(sub));
            when(authorizeNetClient.getSubscription("gw-error"))
                    .thenThrow(new RuntimeException("Connection refused"));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.processBillingCycles();

            assertEquals(2, sub.getBillingFailures());
        }
    }

    @Nested
    @DisplayName("initializeBillingDates")
    class InitializeBillingDatesTests {

        @Test
        @DisplayName("Active subscriptions without billing date get initialized")
        void initializesMissingBillingDates() {
            Subscription sub = createSubscription(1L, "active", null, 0, "gw-123");
            sub.setNextBillingDate(null);

            when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));
            when(subscriptionRepository.save(any(Subscription.class)))
                    .thenAnswer(i -> i.getArgument(0));

            billingCycleService.initializeBillingDates();

            assertNotNull(sub.getNextBillingDate());
            verify(subscriptionRepository).save(sub);
        }

        @Test
        @DisplayName("Active subscriptions with existing billing date are skipped")
        void skipsExistingBillingDates() {
            Subscription sub = createSubscription(1L, "active",
                    LocalDate.of(2026, 6, 1), 0, "gw-123");

            when(subscriptionRepository.findByStatus("active")).thenReturn(List.of(sub));

            billingCycleService.initializeBillingDates();

            verify(subscriptionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("calculateNextBillingDate")
    class CalculateNextBillingDateTests {

        @Test
        @DisplayName("Monthly interval advances by months")
        void monthlyInterval() {
            LocalDate result = billingCycleService.calculateNextBillingDate(
                    LocalDate.of(2026, 1, 15), 1, "months");
            assertEquals(LocalDate.of(2026, 2, 15), result);
        }

        @Test
        @DisplayName("Daily interval advances by days")
        void dailyInterval() {
            LocalDate result = billingCycleService.calculateNextBillingDate(
                    LocalDate.of(2026, 1, 1), 30, "days");
            assertEquals(LocalDate.of(2026, 1, 31), result);
        }

        @Test
        @DisplayName("Null fromDate returns today")
        void nullFromDate() {
            LocalDate result = billingCycleService.calculateNextBillingDate(null, 1, "months");
            assertEquals(LocalDate.now(), result);
        }
    }
}

