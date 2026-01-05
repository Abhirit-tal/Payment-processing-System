package com.example.payment.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JwtTokenProviderTest {

    @Test
    public void createAndValidateToken() {
        String secret = "01234567890123456789012345678901"; // 32 bytes
        JwtTokenProvider provider = new JwtTokenProvider(secret, 3600);
        String token = provider.createToken("dev-1");
        assertNotNull(token);
        assertDoesNotThrow(() -> provider.validateToken(token));
    }

    @Test
    public void invalidTokenThrows() {
        String secret = "01234567890123456789012345678901";
        JwtTokenProvider provider = new JwtTokenProvider(secret, 3600);
        assertThrows(Exception.class, () -> provider.validateToken("invalid.token.here"));
    }
}

