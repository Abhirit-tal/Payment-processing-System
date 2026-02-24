package com.example.payment.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AuditLog entity and builder.
 */
public class AuditLogTest {

    @Test
    void testDefaultConstructor() {
        AuditLog log = new AuditLog();
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void testParameterizedConstructor() {
        AuditLog log = new AuditLog("ORDER", 123L, "STATE_CHANGE");
        assertEquals("ORDER", log.getEntityType());
        assertEquals(123L, log.getEntityId());
        assertEquals("STATE_CHANGE", log.getAction());
        assertNotNull(log.getCreatedAt());
    }

    @Test
    void testSettersAndGetters() {
        AuditLog log = new AuditLog();

        log.setId(1L);
        log.setEntityType("TRANSACTION");
        log.setEntityId(456L);
        log.setAction("CAPTURE");
        log.setActor("user@example.com");
        log.setActorIp("192.168.1.1");
        log.setOldValue("authorized");
        log.setNewValue("captured");
        log.setMetadata("{\"amount\": 100.00}");

        Instant now = Instant.now();
        log.setCreatedAt(now);

        assertEquals(1L, log.getId());
        assertEquals("TRANSACTION", log.getEntityType());
        assertEquals(456L, log.getEntityId());
        assertEquals("CAPTURE", log.getAction());
        assertEquals("user@example.com", log.getActor());
        assertEquals("192.168.1.1", log.getActorIp());
        assertEquals("authorized", log.getOldValue());
        assertEquals("captured", log.getNewValue());
        assertEquals("{\"amount\": 100.00}", log.getMetadata());
        assertEquals(now, log.getCreatedAt());
    }

    @Test
    void testBuilder() {
        AuditLog log = AuditLog.builder()
                .entityType("ORDER")
                .entityId(789L)
                .action("CREATED")
                .actor("system")
                .actorIp("127.0.0.1")
                .oldValue(null)
                .newValue("created")
                .metadata("{\"currency\": \"USD\"}")
                .build();

        assertEquals("ORDER", log.getEntityType());
        assertEquals(789L, log.getEntityId());
        assertEquals("CREATED", log.getAction());
        assertEquals("system", log.getActor());
        assertEquals("127.0.0.1", log.getActorIp());
        assertNull(log.getOldValue());
        assertEquals("created", log.getNewValue());
        assertEquals("{\"currency\": \"USD\"}", log.getMetadata());
    }

    @Test
    void testBuilderPartial() {
        AuditLog log = AuditLog.builder()
                .entityType("ORDER")
                .entityId(100L)
                .action("ERROR")
                .build();

        assertEquals("ORDER", log.getEntityType());
        assertEquals(100L, log.getEntityId());
        assertEquals("ERROR", log.getAction());
        assertNull(log.getActor());
        assertNull(log.getActorIp());
    }

    @Test
    void testBuilderChaining() {
        AuditLog.AuditLogBuilder builder = AuditLog.builder();

        // Verify each method returns the builder for chaining
        assertSame(builder, builder.entityType("TEST"));
        assertSame(builder, builder.entityId(1L));
        assertSame(builder, builder.action("TEST_ACTION"));
        assertSame(builder, builder.actor("actor"));
        assertSame(builder, builder.actorIp("1.2.3.4"));
        assertSame(builder, builder.oldValue("old"));
        assertSame(builder, builder.newValue("new"));
        assertSame(builder, builder.metadata("{}"));
    }

    @Test
    void testNullValues() {
        AuditLog log = new AuditLog();
        log.setActor(null);
        log.setActorIp(null);
        log.setOldValue(null);
        log.setNewValue(null);
        log.setMetadata(null);

        assertNull(log.getActor());
        assertNull(log.getActorIp());
        assertNull(log.getOldValue());
        assertNull(log.getNewValue());
        assertNull(log.getMetadata());
    }
}

