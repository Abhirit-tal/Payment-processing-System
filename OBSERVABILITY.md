# Observability

This document describes the service-level metrics, tracing, and logging strategy for the Payment Processing System.

## Goals

- Provide actionable, high-cardinality metrics for health and SLOs.
- Enable distributed tracing for request flow and performance debugging.
- Produce structured, searchable logs that link to traces and support incident response.

## Instrumentation Stack (recommended)

- Metrics: Micrometer -> Prometheus (exporter)
- Tracing: OpenTelemetry (SDK) -> Jaeger/Zipkin collector
- Logging: SLF4J + Logback (or Log4j2) with JSON output to a centralized log platform (ELK / Loki / Splunk)

## Metrics

Metric naming convention: kebab-case, dot-separated hierarchical grouping is discouraged — prefer `service_<resource>_<metric>` or `payment_processor_<resource>_<metric>`.

Labels/tags: add `service`, `env`, `region`, `instance_id`, `merchant_id` (when applicable), and `payment_method`/`card_brand` for payment metrics.

Primary metrics to expose (examples and suggested types):

- **service_up**: gauge — 1 if service is running
- **http_server_requests_total**: counter — total HTTP requests (labels: method, route, status)
- **http_server_request_duration_seconds**: histogram — request latency by route
- **payment_requests_total**: counter — number of payment attempts (labels: status: success|declined|error, method)
- **payment_success_rate**: gauge — derived (or implement as success/total counters and compute in prometheus)
- **payment_processing_duration_seconds**: histogram — time to process a payment
- **auth_token_issuance_total**: counter — auth token issued
- **db_connection_pool_active**: gauge — DB pool usage
- **db_query_duration_seconds**: histogram — DB query latency
- **external_authorizenet_requests_total**: counter — external gateway calls (labels: status)
- **external_authorizenet_request_duration_seconds**: histogram — external gateway latency
- **queue_messages_in_flight**: gauge — if using queues for async processing
- **failed_jobs_total**: counter — background job failures
- **cache_hit_ratio**: gauge — cache hits / requests

Suggested buckets for histograms: [50ms, 100ms, 250ms, 500ms, 1s, 2.5s, 5s, 10s]

Export: expose `/actuator/prometheus` or `/metrics` endpoint secured by network/ACLs.

Alerting (examples):

- Payment failure rate > X% for 5m
- 95th percentile payment_processing_duration_seconds > threshold
- External gateway error rate > Y% or elevated latency
- `service_up` == 0

Dashboards: SLO overview (success rate, latency percentiles), payment funnel, external gateway health, database and JVM metrics.

## Tracing

Purpose: trace requests end-to-end across services and external dependencies to diagnose high-latency paths and errors.

Standards:

- Use OpenTelemetry for instrumentation and exporting traces.
- Propagate W3C Trace Context (`traceparent`) headers across HTTP/gRPC/queue boundaries.
- Ensure that asynchronous work (background jobs, message handlers) continues trace context by extracting and injecting trace headers on the message envelope.

Span naming and structure:

- Root span: `http.request /{route}` or `internal.request <component>`
- Child spans: name them after the action and target, e.g., `db.query.payment_table.select`, `http.client.authorizenet.charge`, `auth.validate_token`.
- Include attributes/tags: `http.method`, `http.status_code`, `db.system`, `db.statement` (sanitized), `merchant_id`, `amount`, `payment_id`, `card_brand` (non-sensitive).

Sampling and retention:

- Default sampling: tail-based or probabilistic sampling — keep a low fixed rate for production (e.g., 1-5%) and increase for high-error traces (use error-based sampling to keep traces that contain errors).
- Retain high-signal traces longer (errors, p95/p99 latency traces).

Error handling:

- Mark spans with error status on exceptions and record exception.message and exception.stacktrace (truncate stack traces in exporters if needed).

Trace-log linking:

- Include `trace_id` and `span_id` in all logs (structured fields) so logs can be filtered by trace.

Collectors/exporters:

- Send traces to a vendor or self-hosted Jaeger/Zipkin/OpenTelemetry collector.
- Consider sampling configuration and exporting cost when selecting retention and ingestion rates.

## Logging

Format:

- Structured JSON logs are required for downstream parsing and searching. Example fields:

  - `timestamp` (ISO-8601)
  - `level` (INFO/WARN/ERROR/DEBUG)
  - `message`
  - `service` (e.g., payment-processing)
  - `env` (production/staging)
  - `instance_id`/`host`
  - `logger`
  - `thread`
  - `trace_id` and `span_id` (if available)
  - `request_id` / `correlation_id` (a short, business-level identifier)
  - `merchant_id`, `payment_id`, `amount` (non-PII)

Logging guidelines:

- Default log level: `INFO`. Use `DEBUG` for development and elevated troubleshooting only.
- Use structured key-value fields, avoid free-form concatenated messages for important attributes.
- Ensure `trace_id` is added to request-scoped logs by populating MDC (or equivalent) from tracing context.

Sensitive data handling:

- Never log full card PAN/CVV. Mask or omit sensitive fields. For card numbers, log only BIN (6) or last 4 digits when strictly necessary and permitted.
- PII (names, emails) should be omitted or stored in separate, access-controlled systems.

Log levels and use cases:

- `ERROR`: uncaught exceptions, failed payments after retries, external gateway timeouts
- `WARN`: degraded performance, retryable errors, partial failures
- `INFO`: successful payments, high-level lifecycle events (authorization, capture), health events
- `DEBUG`: detailed flow traces, included only when debug is enabled for the instance

Retention and indexing:

- Define retention in the logging backend (e.g., 30d hot + 90d cold) according to compliance.
- Index fields commonly queried (`trace_id`, `payment_id`, `merchant_id`, `status`, `env`) to speed searches.

Log routing and aggregation:

- Forward logs to a central system (ELK, Splunk, Loki). Consider batching/transport (Fluentd/Fluent Bit/Logstash) and network cost.

## Correlation & Request IDs

- Generate a `request_id` at the edge (API gateway or ingress) and propagate it via `X-Request-Id`. Use it in logs and traces for fast lookup.
- Also propagate W3C TraceContext for tracing.

## Alerts and Runbooks (brief)

- Payment failure rate alert: runbook should include quick checks for gateway status, recent deploy, and error trace lookup.
- High latency alert: check top trace spans by duration, DB/queue/external dependencies.
- Service down: `service_up` and health endpoint, check process, JVM metrics, GC pauses.

## Implementation notes

### ✅ What's Actually Integrated in Code

| Feature | Status | Implementation |
|---------|--------|----------------|
| **Prometheus Metrics** | ✅ Implemented | `spring-boot-starter-actuator` + `micrometer-registry-prometheus` → `/actuator/prometheus` |
| **Custom Payment Metrics** | ✅ Implemented | `MetricsConfig` registers `payment_events_total`, `webhook_events_total`, `subscription_events_total`, `payment_queue_size` via Micrometer counters/gauges |
| **Retry Metrics** | ✅ Implemented | `PendingTransactionRetryService` exposes `payment_retry_attempts_total`, `payment_retry_success_total`, `payment_retry_exhausted_total` |
| **Health Endpoint** | ✅ Implemented | `/actuator/health` shows app + circuit breaker + RabbitMQ health |
| **Correlation ID (MDC)** | ✅ Implemented | `CorrelationIdFilter` reads/generates `X-Correlation-ID`, puts in MDC, adds to response |
| **Distributed Tracing (OTel)** | ✅ Implemented | `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp` → Jaeger (OTLP HTTP on port 4318) |
| **Jaeger Tracing UI** | ✅ Implemented | Jaeger `all-in-one` in docker-compose → `http://localhost:16686` |
| **JSON Structured Logging** | ✅ Implemented | `logback-spring.xml` + `logstash-logback-encoder` for production profile; includes `traceId`, `spanId`, `correlationId` |
| **Structured Log Pattern** | ✅ Implemented | Dev profile: `logging.pattern.console` with `%X{correlationId}` and `%X{traceId}`. Prod: JSON via Logstash encoder |
| **Circuit Breaker Metrics** | ✅ Implemented | Resilience4j `authorizeNet` instance → health indicator + Prometheus metrics |
| **Event Queue Metrics** | ✅ Implemented | `PaymentEventQueue.getQueueSize()` + Micrometer gauge `payment_queue_size` |
| **RabbitMQ Queue Processing** | ✅ Implemented | `PaymentEventQueue` publishes to RabbitMQ durable queues with DLQ; `@RabbitListener` consumers dispatch events |
| **Audit Logging** | ✅ Implemented | `AuditService` persists all state transitions, gateway calls, errors to `audit_logs` table |
| **Pending Tx Retry** | ✅ Implemented | `PendingTransactionRetryService` — scheduled job retries stale PENDING/ERROR orders with audit trail and Micrometer metrics |
| **Webhook Retry** | ✅ Implemented | `WebhookRetryService` — scheduled job retries failed webhook events with exponential backoff |
| **Billing Cycle Scheduler** | ✅ Implemented | `BillingCycleService` — processes due billing cycles, tracks `next_billing_date`, failure counts |
| **Subscription Sync** | ✅ Implemented | `SubscriptionScheduler` — syncs subscription statuses with Authorize.Net ARB every 6 hours |

### Custom Micrometer Metrics (MetricsConfig + PendingTransactionRetryService)

| Metric Name | Type | Tags | Description |
|-------------|------|------|-------------|
| `payment_events_total` | Counter | `type`: purchase_success, purchase_failed, authorize_success, capture_success, void_success, refund_success | Total payment events by type |
| `webhook_events_total` | Counter | — | Total webhook events received from Authorize.Net |
| `subscription_events_total` | Counter | `type`: created, cancelled | Total subscription lifecycle events |
| `payment_queue_size` | Gauge | — | Current depth of the fallback in-memory payment event queue |
| `payment_retry_attempts_total` | Counter | — | Total pending transaction retry attempts |
| `payment_retry_success_total` | Counter | — | Total successful retries that reconciled with gateway |
| `payment_retry_exhausted_total` | Counter | — | Total retries that exceeded max attempts |

### Correlation ID Flow

```
Client Request                    Server Response
┌─────────────┐                  ┌──────────────┐
│ X-Correlation-ID: abc-123 │ →  │ X-Correlation-ID: abc-123 │
│ (or auto-generated UUID)  │    │                            │
└─────────────┘                  └──────────────┘
         │
         ▼
┌─────────────────────────┐
│ MDC: correlationId=abc-123  │
│ MDC: requestId=req_abc-1234 │
│                              │
│ All log lines include:       │
│ [abc-123] INFO ...message    │
└─────────────────────────┘
```

### Log Format (Console)

```
2026-03-07 14:30:00.123 [main] [abc-12345-678] INFO  c.e.p.s.PaymentService - Created order 1 with amount 10.00 USD
```

### Prometheus Metrics Endpoint

Exposed at `/actuator/prometheus`. Key metrics automatically collected:
- `http_server_requests_seconds_*` — request latency by method, URI, status
- `jvm_memory_*`, `jvm_gc_*` — JVM metrics
- `resilience4j_circuitbreaker_*` — circuit breaker state, failure rate, calls
- `spring_data_*` — database connection pool metrics
- `payment_events_total` — payment event counters by type (purchase, auth, capture, void, refund)
- `webhook_events_total` — webhook receipt counter
- `subscription_events_total` — subscription lifecycle counters
- `payment_queue_size` — current event queue depth

### Configuration

```properties
# application.properties
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.endpoint.health.show-details=when-authorized
management.metrics.tags.application=Payment Processing System
```

- Java: use `micrometer-core` + `micrometer-registry-prometheus`, `opentelemetry-javaagent` or manual OpenTelemetry SDK instrumentation. Use `logback` with `logstash-logback-encoder` for JSON logs.
- Expose `/actuator/health` and `/actuator/prometheus` (if using Spring Boot) on an internal port or behind the metrics collector.
- Secure metrics and trace endpoints using network-level controls and/or basic auth when necessary.

## Example log (JSON)

{
  "timestamp":"2026-02-10T12:34:56.789Z",
  "level":"ERROR",
  "service":"payment-processing",
  "env":"production",
  "message":"payment failed: gateway timeout",
  "payment_id":"pmt_12345",
  "merchant_id":"m_987",
  "trace_id":"abcd1234ef...",
  "span_id":"00f1...",
  "request_id":"req-20260210-xyz",
  "error":"TimeoutException"
}

## Example trace header

- W3C Trace-Context header: `traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`

## Next steps / ownership

- Instrument key flows (HTTP endpoints, DB, external gateway) and validate metrics and traces end-to-end in staging.
- Create basic dashboards and three key alerts (failure rate, latency, service down).
- Review retention, sampling and cost with platform/ops.

---

If you want, I can also:

- Add example code snippets for Micrometer/OpenTelemetry/Logback configuration.
- Create a basic Prometheus alerting rule and Grafana dashboard JSON.
