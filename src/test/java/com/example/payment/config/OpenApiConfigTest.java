package com.example.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OpenApiConfig.
 */
public class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void testPaymentOpenAPICreation() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI);
    }

    @Test
    void testOpenAPIInfo() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getInfo());
        assertEquals("Payment Processing API", openAPI.getInfo().getTitle());
        assertEquals("v0.0.1", openAPI.getInfo().getVersion());
    }

    @Test
    void testOpenAPIContact() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getInfo().getContact());
        assertEquals("Dev Team", openAPI.getInfo().getContact().getName());
        assertEquals("dev@example.com", openAPI.getInfo().getContact().getEmail());
    }

    @Test
    void testOpenAPILicense() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getInfo().getLicense());
        assertEquals("MIT", openAPI.getInfo().getLicense().getName());
    }

    @Test
    void testOpenAPISecurityScheme() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getComponents());
        assertNotNull(openAPI.getComponents().getSecuritySchemes());
        assertTrue(openAPI.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
    }

    @Test
    void testOpenAPISecurityRequirement() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getSecurity());
        assertFalse(openAPI.getSecurity().isEmpty());
    }

    @Test
    void testOpenAPIExternalDocs() {
        OpenAPI openAPI = config.paymentOpenAPI();

        assertNotNull(openAPI.getExternalDocs());
        assertEquals("Project README", openAPI.getExternalDocs().getDescription());
    }
}

