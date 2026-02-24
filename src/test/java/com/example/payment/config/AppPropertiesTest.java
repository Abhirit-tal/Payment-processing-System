package com.example.payment.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AppProperties.
 */
public class AppPropertiesTest {

    @Test
    void testDefaultExpirationSeconds() {
        AppProperties props = new AppProperties();
        assertEquals(3600, props.getExpirationSeconds());
    }

    @Test
    void testSettersAndGetters() {
        AppProperties props = new AppProperties();

        props.setSecret("my-secret-key");
        props.setExpirationSeconds(7200);

        assertEquals("my-secret-key", props.getSecret());
        assertEquals(7200, props.getExpirationSeconds());
    }

    @Test
    void testSecretDefaultsToNull() {
        AppProperties props = new AppProperties();
        assertNull(props.getSecret());
    }
}

