package com.example.payment.controller;

import com.example.payment.auth.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final String developerKey;

    public AuthController(JwtTokenProvider jwtTokenProvider, @Value("${developer.key:dev-local-key}") String developerKey) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.developerKey = developerKey;
    }

    @Operation(summary = "Issue a developer JWT token",
            description = "Exchanges a developer_key for a short-lived JWT (development use only)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token issued"),
            @ApiResponse(responseCode = "401", description = "Invalid developer key")
    })
    @PostMapping("/token")
    public ResponseEntity<?> token(@RequestBody Map<String, String> body) {
        String devKey = body.get("developer_key");
        if (devKey == null || !devKey.equals(developerKey)) {
            return ResponseEntity.status(401).body(Map.of("detail", "invalid developer key"));
        }
        String token = jwtTokenProvider.createToken("developer");
        return ResponseEntity.ok(Map.of("access_token", token, "token_type", "bearer", "expires_in", 3600));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return ResponseEntity.status(401).body(Map.of("authenticated", false));
        return ResponseEntity.ok(Map.of(
                "authenticated", auth.isAuthenticated(),
                "principal", auth.getPrincipal(),
                "authorities", auth.getAuthorities()
        ));
    }
}
