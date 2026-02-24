package com.example.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Configuration to enable retry, async, and scheduling features.
 *
 * <h2>Enabled Features:</h2>
 * <ul>
 *   <li><strong>@EnableRetry:</strong> Enables Spring Retry for @Retryable methods</li>
 *   <li><strong>@EnableAsync:</strong> Enables async execution for @Async methods</li>
 *   <li><strong>@EnableScheduling:</strong> Enables scheduled tasks for @Scheduled methods</li>
 * </ul>
 */
@Configuration
@EnableRetry
@EnableAsync
@EnableScheduling
public class RetryConfig {
    // Configuration properties can be added here if needed
    // Currently using defaults from application.properties
}

