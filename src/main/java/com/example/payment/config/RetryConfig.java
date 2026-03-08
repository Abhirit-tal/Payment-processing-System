package com.example.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration to enable retry, async, and scheduling features.
 *
 * <h2>Enabled Features:</h2>
 * <ul>
 *   <li><strong>@EnableRetry:</strong> Enables Spring Retry for @Retryable methods</li>
 *   <li><strong>@EnableAsync:</strong> Enables async execution for @Async methods</li>
 *   <li><strong>@EnableScheduling:</strong> Enables scheduled tasks for @Scheduled methods</li>
 * </ul>
 *
 * <h2>Thread Pool (Fix for unbounded thread creation):</h2>
 * <p>Spring Boot's default SimpleAsyncTaskExecutor creates a new thread per @Async call
 * with no bounds. For production, we configure a bounded ThreadPoolTaskExecutor to prevent
 * resource exhaustion from audit logging and other async operations.</p>
 */
@Configuration
@EnableRetry
@EnableAsync
@EnableScheduling
public class RetryConfig {

    /**
     * Bounded thread pool for @Async methods (audit logging, gateway response logging, etc.).
     * Prevents unbounded thread creation from the default SimpleAsyncTaskExecutor.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-audit-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

