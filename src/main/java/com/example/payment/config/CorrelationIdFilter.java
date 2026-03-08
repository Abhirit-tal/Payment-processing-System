package com.example.payment.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that injects a correlation ID into every request for distributed tracing.
 *
 * <p>Reads {@code X-Correlation-ID} header from the incoming request. If absent,
 * generates a new UUID. The correlation ID is:</p>
 * <ul>
 *   <li>Placed in SLF4J MDC so all log statements automatically include it</li>
 *   <li>Added to the response header for client correlation</li>
 * </ul>
 *
 * <h2>Design Decision (AI Dialogue):</h2>
 * <p><strong>Q:</strong> Should we use Spring Cloud Sleuth/Micrometer Tracing for distributed tracing?</p>
 * <p><strong>A:</strong> For this project, a lightweight MDC-based approach is sufficient and has zero
 * external dependencies. It provides end-to-end request correlation in logs. For production with
 * microservices, we'd upgrade to OpenTelemetry with Zipkin/Jaeger.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Put into MDC for structured logging
        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, "req_" + correlationId.substring(0, 8));

        // Add to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY);
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }
}

