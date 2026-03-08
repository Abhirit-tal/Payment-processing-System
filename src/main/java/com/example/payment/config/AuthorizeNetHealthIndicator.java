package com.example.payment.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Custom health indicator for Authorize.Net gateway connectivity.
 *
 * <p>Reports the gateway health based on the Resilience4j circuit breaker state.
 * When the circuit breaker is OPEN (too many failures), gateway health is DOWN.
 * When HALF_OPEN, it's degraded. When CLOSED, it's UP.</p>
 *
 * <p>This avoids making a real gateway call on every health check while still
 * reflecting actual gateway availability.</p>
 */
@Component
public class AuthorizeNetHealthIndicator implements HealthIndicator {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public AuthorizeNetHealthIndicator(CircuitBreakerRegistry circuitBreakerRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    @Override
    public Health health() {
        try {
            CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("authorizeNet");
            CircuitBreaker.State state = cb.getState();
            CircuitBreaker.Metrics metrics = cb.getMetrics();

            Health.Builder builder;
            switch (state) {
                case CLOSED:
                    builder = Health.up();
                    break;
                case HALF_OPEN:
                    builder = Health.status("DEGRADED");
                    break;
                case OPEN:
                    builder = Health.down();
                    break;
                default:
                    builder = Health.unknown();
            }

            return builder
                    .withDetail("circuitBreakerState", state.name())
                    .withDetail("failureRate", metrics.getFailureRate() + "%")
                    .withDetail("totalCalls", metrics.getNumberOfBufferedCalls())
                    .withDetail("failedCalls", metrics.getNumberOfFailedCalls())
                    .withDetail("successfulCalls", metrics.getNumberOfSuccessfulCalls())
                    .build();
        } catch (Exception e) {
            return Health.unknown()
                    .withDetail("error", "Could not determine gateway health: " + e.getMessage())
                    .build();
        }
    }
}

