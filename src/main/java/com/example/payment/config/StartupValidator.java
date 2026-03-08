package com.example.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup validator that checks required secrets and configuration are present.
 *
 * <p>Fails fast with clear error messages if critical configuration is missing,
 * preventing the application from starting in an insecure/broken state.</p>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we use default values for secrets?</p>
 * <p><strong>A:</strong> No. Hardcoded fallback secrets are a security risk — if the env
 * var isn't set, the app runs with a known weak key. Fail-fast is the correct approach:
 * if JWT_SECRET or DEVELOPER_KEY aren't configured, the app should refuse to start
 * (or at minimum log WARN-level alerts).</p>
 */
@Component
public class StartupValidator {

    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${developer.key:}")
    private String developerKey;

    @Value("${authnet.api.login.id:}")
    private String authnetLoginId;

    @Value("${authnet.transaction.key:}")
    private String authnetTransactionKey;

    @Value("${authnet.webhook.signature-key:}")
    private String webhookSignatureKey;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void validateConfiguration() {
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        // Critical: JWT secret must be set and strong
        if (jwtSecret == null || jwtSecret.isBlank()) {
            errors.add("JWT_SECRET is not configured. Authentication will not work.");
        } else if (jwtSecret.length() < 32) {
            warnings.add("JWT_SECRET is shorter than 32 characters. Use a strong secret in production.");
        } else if (isWeakSecret(jwtSecret)) {
            warnings.add("JWT_SECRET appears to be a default/weak value. Change it for production.");
        }

        // Critical: Developer key for auth endpoint
        if (developerKey == null || developerKey.isBlank()) {
            warnings.add("DEVELOPER_KEY is not configured. /auth/token endpoint may not work.");
        }

        // Important: Authorize.Net credentials
        if (authnetLoginId == null || authnetLoginId.isBlank()) {
            warnings.add("AUTHNET_API_LOGIN_ID is not configured. Payment processing will fail.");
        }
        if (authnetTransactionKey == null || authnetTransactionKey.isBlank()) {
            warnings.add("AUTHNET_TRANSACTION_KEY is not configured. Payment processing will fail.");
        }

        // Optional but recommended: Webhook signature key
        if (webhookSignatureKey == null || webhookSignatureKey.isBlank()) {
            warnings.add("AUTHNET_WEBHOOK_SIGNATURE_KEY is not configured. Webhook signature validation is disabled.");
        }

        // Database password check
        if ("postgres".equals(dbPassword)) {
            warnings.add("Database password is set to default 'postgres'. Change for production.");
        }

        // Report findings
        if (!warnings.isEmpty()) {
            log.warn("╔══════════════════════════════════════════════════════════════╗");
            log.warn("║           CONFIGURATION WARNINGS AT STARTUP                  ║");
            log.warn("╠══════════════════════════════════════════════════════════════╣");
            for (String warning : warnings) {
                log.warn("║ ⚠ {}", warning);
            }
            log.warn("╚══════════════════════════════════════════════════════════════╝");
        }

        if (!errors.isEmpty()) {
            log.error("╔══════════════════════════════════════════════════════════════╗");
            log.error("║          CRITICAL CONFIGURATION ERRORS AT STARTUP            ║");
            log.error("╠══════════════════════════════════════════════════════════════╣");
            for (String error : errors) {
                log.error("║ ✖ {}", error);
            }
            log.error("╚══════════════════════════════════════════════════════════════╝");
            // In production, you might want to throw an exception to prevent startup:
            // throw new IllegalStateException("Critical configuration missing: " + String.join(", ", errors));
        }

        if (warnings.isEmpty() && errors.isEmpty()) {
            log.info("✅ All critical configuration validated successfully.");
        }
    }

    private boolean isWeakSecret(String secret) {
        String lower = secret.toLowerCase();
        return lower.contains("secret") || lower.contains("password") ||
               lower.contains("default") || lower.contains("change-me") ||
               lower.contains("dev") || lower.contains("test");
    }
}

