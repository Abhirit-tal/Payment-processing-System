package com.example.payment.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Component
public class JwtTokenProvider {

    private final Key key;
    private final long expirationMillis;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration-seconds}") long expSeconds) {
        if (secret == null || secret.isBlank() || "change-me-please".equals(secret)) {
            // For demo only: generate a random key if not set (logs should warn in real app)
            this.key = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes());
        }
        this.expirationMillis = expSeconds * 1000L;
    }

    public String createToken(String subject) {
        long now = System.currentTimeMillis();
        Date expiry = new Date(now + expirationMillis);
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(new Date(now))
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    public void validateToken(String token) {
        Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }
}

