package com.example.payment.service;

import com.example.payment.model.AuditLog;
import com.example.payment.model.Order;
import com.example.payment.model.PaymentState;
import com.example.payment.model.Transaction;
import com.example.payment.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 */
public class AuditServiceTest {

    private AuditLogRepository auditLogRepository;
    private ObjectMapper objectMapper;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditLogRepository = mock(AuditLogRepository.class);
        objectMapper = new ObjectMapper();
        auditService = new AuditService(auditLogRepository, objectMapper);
        SecurityContextHolder.clearContext();
    }

    @Nested
    class LogStateTransitionTests {

        @Test
        void testLogStateTransition() {
            Order order = createTestOrder();

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logStateTransition(order, PaymentState.CREATED, PaymentState.PENDING);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ENTITY_ORDER, log.getEntityType());
            assertEquals(order.getId(), log.getEntityId());
            assertEquals(AuditService.ACTION_STATE_CHANGE, log.getAction());
            assertEquals("created", log.getOldValue());
            assertEquals("pending", log.getNewValue());
        }

        @Test
        void testLogStateTransitionWithNullFromState() {
            Order order = createTestOrder();

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logStateTransition(order, null, PaymentState.PENDING);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertNull(log.getOldValue());
            assertEquals("pending", log.getNewValue());
        }

        @Test
        void testLogStateTransitionHandlesException() {
            Order order = createTestOrder();

            when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB error"));

            // Should not throw
            assertDoesNotThrow(() ->
                auditService.logStateTransition(order, PaymentState.CREATED, PaymentState.PENDING));
        }
    }

    @Nested
    class LogOrderCreatedTests {

        @Test
        void testLogOrderCreated() {
            Order order = createTestOrder();

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logOrderCreated(order);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ENTITY_ORDER, log.getEntityType());
            assertEquals(AuditService.ACTION_CREATED, log.getAction());
            assertTrue(log.getMetadata().contains("100.00"));
        }

        @Test
        void testLogOrderCreatedHandlesException() {
            Order order = createTestOrder();

            when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() -> auditService.logOrderCreated(order));
        }
    }

    @Nested
    class LogTransactionCreatedTests {

        @Test
        void testLogTransactionCreated() {
            Transaction tx = createTestTransaction();

            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logTransactionCreated(tx, AuditService.ACTION_PURCHASE);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ENTITY_TRANSACTION, log.getEntityType());
            assertEquals(AuditService.ACTION_PURCHASE, log.getAction());
            assertEquals("success", log.getNewValue());
        }

        @Test
        void testLogTransactionCreatedHandlesException() {
            Transaction tx = createTestTransaction();

            when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() -> auditService.logTransactionCreated(tx, AuditService.ACTION_PURCHASE));
        }
    }

    @Nested
    class LogGatewayCallAsyncTests {

        @Test
        void testLogGatewayCallAsync() {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logGatewayCallAsync(1L, "AUTH_CAPTURE", "amount=100.00");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ACTION_GATEWAY_CALL, log.getAction());
            assertTrue(log.getMetadata().contains("AUTH_CAPTURE"));
        }

        @Test
        void testLogGatewayCallAsyncHandlesException() {
            when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("DB error"));

            assertDoesNotThrow(() -> auditService.logGatewayCallAsync(1L, "AUTH_CAPTURE", "amount=100.00"));
        }
    }

    @Nested
    class LogGatewayResponseAsyncTests {

        @Test
        void testLogGatewayResponseAsyncSuccess() {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logGatewayResponseAsync(1L, "AUTH_CAPTURE", true, "approved");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ACTION_GATEWAY_RESPONSE, log.getAction());
            assertTrue(log.getMetadata().contains("true"));
        }

        @Test
        void testLogGatewayResponseAsyncFailure() {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logGatewayResponseAsync(1L, "AUTH_CAPTURE", false, "declined");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertTrue(log.getMetadata().contains("false"));
        }
    }

    @Nested
    class LogErrorAsyncTests {

        @Test
        void testLogErrorAsync() {
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logErrorAsync(AuditService.ENTITY_ORDER, 1L, "GATEWAY_ERROR", "Connection timeout");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            AuditLog log = captor.getValue();
            assertEquals(AuditService.ACTION_ERROR, log.getAction());
            assertTrue(log.getMetadata().contains("GATEWAY_ERROR"));
            assertTrue(log.getMetadata().contains("Connection timeout"));
        }
    }

    @Nested
    class GetAuditHistoryTests {

        @Test
        void testGetOrderAuditHistory() {
            List<AuditLog> mockLogs = Arrays.asList(new AuditLog(), new AuditLog());
            when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                    AuditService.ENTITY_ORDER, 1L)).thenReturn(mockLogs);

            List<AuditLog> result = auditService.getOrderAuditHistory(1L);

            assertEquals(2, result.size());
        }

        @Test
        void testGetTransactionAuditHistory() {
            List<AuditLog> mockLogs = Arrays.asList(new AuditLog());
            when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
                    AuditService.ENTITY_TRANSACTION, 1L)).thenReturn(mockLogs);

            List<AuditLog> result = auditService.getTransactionAuditHistory(1L);

            assertEquals(1, result.size());
        }

        @Test
        void testGetAuditHistoryEmpty() {
            when(auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(any(), any()))
                    .thenReturn(Collections.emptyList());

            List<AuditLog> result = auditService.getOrderAuditHistory(999L);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class ActorTests {

        @Test
        void testCurrentActorFromSecurityContext() {
            // Set up authenticated user
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("testuser", null, Collections.emptyList()));

            Order order = createTestOrder();
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logOrderCreated(order);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertEquals("testuser", captor.getValue().getActor());
        }

        @Test
        void testCurrentActorDefaultsToSystem() {
            // No authentication set
            SecurityContextHolder.clearContext();

            Order order = createTestOrder();
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            auditService.logOrderCreated(order);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());

            assertEquals("SYSTEM", captor.getValue().getActor());
        }
    }

    @Nested
    class ConstantsTests {

        @Test
        void testEntityConstants() {
            assertEquals("ORDER", AuditService.ENTITY_ORDER);
            assertEquals("TRANSACTION", AuditService.ENTITY_TRANSACTION);
        }

        @Test
        void testActionConstants() {
            assertEquals("STATE_CHANGE", AuditService.ACTION_STATE_CHANGE);
            assertEquals("CREATED", AuditService.ACTION_CREATED);
            assertEquals("UPDATED", AuditService.ACTION_UPDATED);
            assertEquals("PURCHASE", AuditService.ACTION_PURCHASE);
            assertEquals("AUTHORIZE", AuditService.ACTION_AUTHORIZE);
            assertEquals("CAPTURE", AuditService.ACTION_CAPTURE);
            assertEquals("VOID", AuditService.ACTION_VOID);
            assertEquals("REFUND", AuditService.ACTION_REFUND);
            assertEquals("GATEWAY_CALL", AuditService.ACTION_GATEWAY_CALL);
            assertEquals("GATEWAY_RESPONSE", AuditService.ACTION_GATEWAY_RESPONSE);
            assertEquals("ERROR", AuditService.ACTION_ERROR);
        }
    }

    // Helper methods

    private Order createTestOrder() {
        Order order = new Order();
        order.setId(1L);
        order.setExternalId("ext-1");
        order.setAmount(new BigDecimal("100.00"));
        order.setCurrency("USD");
        order.setState(PaymentState.CREATED);
        return order;
    }

    private Transaction createTestTransaction() {
        Order order = createTestOrder();

        Transaction tx = new Transaction();
        tx.setId(1L);
        tx.setOrder(order);
        tx.setType("purchase");
        tx.setAmount(new BigDecimal("100.00"));
        tx.setStatus("success");
        tx.setProviderTxId("prov-123");
        return tx;
    }
}

