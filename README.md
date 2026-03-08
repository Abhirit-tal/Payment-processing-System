# Payment Processing System (Spring Boot)

A robust Spring Boot backend that integrates with Authorize.Net sandbox for payment processing with enterprise-grade features including state machine, idempotency, recurring billing, webhook handling, audit logging, distributed tracing, and resilience patterns.

## Key Features

- **Payment Flows**: Purchase, Authorize, Capture, Cancel/Void, Refund (full & partial)
- **Recurring Billing**: Subscription CRUD via Authorize.Net ARB (Automated Recurring Billing) API with billing cycle scheduler
- **Webhook Handling**: Async webhook endpoint with HMAC signature validation, idempotent deduplication, and RabbitMQ-backed queue processing with dead-letter handling
- **Payment State Machine**: Explicit state transitions with integrity enforcement
- **Idempotency**: Prevent duplicate payment processing via `Idempotency-Key` header (wired into all payment endpoints)
- **Retry & Circuit Breaker**: `@Retryable` (exponential backoff) + Resilience4j `@CircuitBreaker` on all gateway calls, plus scheduled retry for pending/error transactions
- **Message Queue**: RabbitMQ-backed durable event processing with in-memory fallback, dead-letter queues, and topic exchanges
- **Distributed Tracing**: Correlation ID filter (MDC) + OpenTelemetry/Jaeger integration — traceId/spanId propagated in all logs and response headers
- **Observability**: Prometheus metrics via `/actuator/prometheus`, JSON structured logging (production), Jaeger tracing UI, Spring Boot Actuator health
- **Audit Logging**: Comprehensive tracking of all payment operations with persistent audit trail
- **Structured Error Handling**: Clear error codes, retry guidance, provider details for all gateway failure scenarios
- **JWT Authentication**: Secure API access with token-based auth
- **PCI DSS Compliance**: Card data masking, no CVV storage, secrets management via HashiCorp Vault
- **Secrets Management**: HashiCorp Vault integration (optional `vault` profile) for API keys and sensitive credentials

## Quick Start

### 1. Start PostgreSQL (using Docker)

```powershell
docker-compose up -d postgres
```

Or install PostgreSQL locally and create a database:
```sql
CREATE DATABASE payments;
```

### 2. Build and run tests

```powershell
mvn clean test
```

### 3. Run the application

```powershell
mvn spring-boot:run
```

Or run everything with Docker:
```powershell
docker-compose up
```

### 4. Configuration

Set environment variables or update `src/main/resources/application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/payments` |
| `DATABASE_USERNAME` | Database username | `postgres` |
| `DATABASE_PASSWORD` | Database password | `postgres` |
| `AUTHNET_API_LOGIN_ID` | Authorize.Net API Login ID (sandbox) | - |
| `AUTHNET_TRANSACTION_KEY` | Authorize.Net Transaction Key (sandbox) | - |
| `AUTHNET_WEBHOOK_SIGNATURE_KEY` | Webhook HMAC signature key | - |
| `JWT_SECRET` | Secret for signing JWTs | `my-super-secret-key-for-dev-32bytes` |
| `DEVELOPER_KEY` | Dev key for `/auth/token` endpoint | `dev-local-key` |

### 5. Secrets Management

For production, secrets should NOT be stored in environment variables or config files. Use:
- **HashiCorp Vault** (recommended): `spring.config.import=vault://` — see commented config in `application.properties`
- **AWS Secrets Manager / Azure Key Vault**: via Spring Cloud integrations
- **Kubernetes Secrets**: mounted as env vars or files

The application supports a `vault` Spring profile for Vault-based secret injection.

## API Endpoints

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/payments/health` | Health check | No |
| POST | `/auth/token` | Get JWT token | No |
| POST | `/payments/purchase` | Purchase (auth+capture) | JWT |
| POST | `/payments/authorize` | Authorize only | JWT |
| POST | `/payments/capture` | Capture authorized tx | JWT |
| POST | `/payments/cancel` | Void/Cancel | JWT |
| POST | `/payments/refund` | Refund (full/partial) | JWT |
| POST | `/payments/subscriptions` | Create recurring subscription | JWT |
| GET | `/payments/subscriptions/{id}` | Get subscription | JWT |
| PUT | `/payments/subscriptions/{id}` | Update subscription | JWT |
| DELETE | `/payments/subscriptions/{id}` | Cancel subscription | JWT |
| POST | `/webhooks/authorize-net` | Receive Authorize.Net webhooks | HMAC |
| GET | `/actuator/prometheus` | Prometheus metrics | No |
| GET | `/actuator/health` | Actuator health | No |

### Idempotency

All payment mutation endpoints accept an optional `Idempotency-Key` header. If provided:
- First request: processed normally, response cached for 24h
- Subsequent requests with same key + same body: returns cached response
- Same key + different body: returns 409 Conflict

### Webhook Integration

Configure your Authorize.Net sandbox webhook URL to `https://your-domain/webhooks/authorize-net`. Events are:
1. Validated via SHA-512 HMAC signature (`X-ANET-Signature` header)
2. Deduplicated by `notificationId` (idempotent — duplicate events return 200 without re-processing)
3. Persisted to `webhook_events` table with full payload
4. Published to RabbitMQ durable queue (`webhook-events-queue`) for async processing
5. Failed webhook events retried automatically by `WebhookRetryService` (up to 3 retries with exponential backoff)
6. Dead-letter queue (`webhook-events-dlq`) captures permanently failed events for manual investigation

### Resilience

- **Retry**: All `AuthorizeNetClient` gateway calls use `@Retryable(maxAttempts=3, backoff=@Backoff(delay=500, multiplier=2))`
- **Pending Transaction Retry**: `PendingTransactionRetryService` — scheduled job (every 5 min) retries stale PENDING orders up to 3 times, then marks ERROR with audit trail. Exposes Micrometer metrics: `payment_retry_attempts_total`, `payment_retry_success_total`, `payment_retry_exhausted_total`
- **Error Transaction Retry**: Separate scheduled job (every 10 min) retries ERROR-state orders that haven't exhausted max attempts
- **Webhook Retry**: `WebhookRetryService` — scheduled job (every 2 min) retries failed webhook events with exponential backoff (up to 3 retries)
- **Circuit Breaker**: Resilience4j circuit breaker (`authorizeNet`) with 50% failure threshold, 30s open wait
- **Rate Limiter**: Resilience4j `@RateLimiter` on all payment endpoints (100 req/s) and webhook endpoint (200 req/s)
- **Monitoring**: Circuit breaker health exposed via `/actuator/health`, retry metrics at `/actuator/prometheus`

### Observability & Metrics

- **Custom Micrometer Metrics**: `payment_events_total` (by type), `webhook_events_total`, `subscription_events_total`, `payment_queue_size`, `payment_retry_attempts_total`, `payment_retry_success_total`, `payment_retry_exhausted_total` — all exposed at `/actuator/prometheus`
- **Distributed Tracing**: `CorrelationIdFilter` — `X-Correlation-ID` in all logs and response headers. OpenTelemetry bridge exports traces to Jaeger (UI at `http://localhost:16686`)
- **JSON Structured Logging**: Production profile uses `logstash-logback-encoder` for JSON output with `traceId`, `spanId`, `correlationId` fields — compatible with ELK/Loki/Splunk
- **Audit Logging**: All state transitions, gateway calls, and errors persisted to `audit_logs` table
- **Subscription Sync**: `SubscriptionScheduler` syncs subscription statuses with Authorize.Net every 6 hours
- **Billing Cycle Scheduler**: `BillingCycleService` processes due billing cycles every hour, tracks `next_billing_date`, failure counts

## Architecture Highlights

### Payment State Machine

```
CREATED → PENDING → AUTHORIZED → CAPTURED → REFUNDED
                 ↘ DECLINED    ↘ VOIDED
                 ↘ ERROR (retriable)
                 ↘ HELD_FOR_REVIEW
```

See [Architecture.md](Architecture.md) for detailed state transitions.

### Error Response Format

```json
{
  "error": {
    "code": "CARD_DECLINED",
    "message": "The card was declined",
    "category": "DECLINE_ERROR",
    "retryable": false,
    "suggestions": ["Try a different payment method"]
  }
}
```

## Documentation

| Document | Description |
|----------|-------------|
| [Architecture.md](Architecture.md) | API flows, state machine, DB schema, design trade-offs |
| [API-SPECIFICATION.yml](API-SPECIFICATION.yml) | OpenAPI specification |
| [COMPLIANCE.md](COMPLIANCE.md) | PCI DSS awareness, secrets management, rate limits |
| [OBSERVABILITY.md](OBSERVABILITY.md) | Metrics, tracing, logging strategy |
| [TESTING_STRATEGY.md](TESTING_STRATEGY.md) | Test coverage strategy |
| [TEST_REPORT.md](TEST_REPORT.md) | Coverage report |
| [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) | Project layout guide |
| [CHAT_HISTORY.md](CHAT_HISTORY.md) | AI collaboration dialogue |

## Swagger UI

After starting the app (`mvn spring-boot:run`) open the interactive API docs at:

- http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

### Authorize the UI

1. Request a developer JWT token (development only):

```powershell
curl -X POST "http://localhost:8080/auth/token" -H "Content-Type: application/json" -d '{"developer_key":"dev-local-key"}' | jq
```

Response (example):

```json
{
  "access_token": "eyJhbGciOiJI...",
  "token_type": "bearer",
  "expires_in": 3600
}
```

2. In the Swagger UI click "Authorize" and paste the token as:
```
Bearer eyJhbGciOiJI...
```

3. Call secured endpoints (e.g., `/payments/purchase`) from the UI.

## Running integration tests (Authorize.Net sandbox)

Integration tests are gated behind a Maven profile named `integration` and will only run when you enable the profile and provide sandbox credentials via environment variables.

1) Set environment variables (PowerShell):

```powershell
$env:AUTHNET_API_LOGIN_ID = "your_sandbox_api_login_id"
$env:AUTHNET_TRANSACTION_KEY = "your_sandbox_transaction_key"
```

2) Run the integration profile (this runs unit tests + integration tests):

```powershell
mvn -P integration -DskipTests=false verify
```

If you prefer to run integration tests only (skip unit tests):

```powershell
mvn -P integration -DskipTests=true failsafe:integration-test failsafe:verify
```

Notes:
- Integration tests will be skipped automatically if credentials are not present.
- Integration tests hit the Authorize.Net sandbox and may create transactions in your sandbox account.

## Scaling & Production Readiness

### Message Queue (RabbitMQ-Backed)
The implementation uses **RabbitMQ durable queues** for async event processing (webhook handling, payment notifications, audit events). The architecture includes:
- **Payment Events Exchange** (topic): `payment-events-exchange` → `payment-events-queue`
- **Webhook Events Exchange** (topic): `webhook-events-exchange` → `webhook-events-queue`
- **Dead Letter Queues**: `payment-events-dlq` and `webhook-events-dlq` for failed messages
- **In-Memory Fallback**: If RabbitMQ is unavailable, events fall back to `LinkedBlockingQueue` for local dispatch

Critical payment state is always persisted to the database **before** being queued, so event loss does not cause data inconsistency.

For higher throughput, replace RabbitMQ with Apache Kafka using `spring-kafka` with `@KafkaListener`.

### Distributed Tracing (Jaeger)
- **Jaeger UI**: `http://localhost:16686` (included in docker-compose)
- **OTLP Export**: Traces sent via OpenTelemetry OTLP HTTP to Jaeger
- **Sampling**: 100% in dev, 5% in production (`management.tracing.sampling.probability`)
- **Trace Context**: W3C `traceparent` propagated across HTTP boundaries

### Secrets Management
- **Development**: Environment variables or `application.properties`
- **Production**: HashiCorp Vault via `spring-cloud-starter-vault-config` — activate with `SPRING_PROFILES_ACTIVE=prod,vault`
- **Vault Profile**: `application-vault.properties` configures Vault URI, authentication (Token or AppRole), and KV backend
- **Docker**: `docker-compose --profile vault up` starts Vault server for local testing
- **API Key Rotation**: Vault supports TTL-based automatic rotation; application re-fetches after expiry

See [COMPLIANCE.md](COMPLIANCE.md) for full production secrets management, PCI DSS awareness, rate limiting, and compliance checklist.

### Background Schedulers
- **PendingTransactionRetryService**: Retries stale PENDING orders every 5 min (configurable via `pending.retry.interval-ms`)
- **WebhookRetryService**: Retries failed webhook events every 2 min with exponential backoff
- **BillingCycleService**: Processes due billing cycles every hour, tracks `next_billing_date`
- **SubscriptionScheduler**: Syncs subscription statuses with Authorize.Net every 6 hours
- **IdempotencyService**: Cleans up expired idempotency keys hourly

All schedulers are enabled via `@EnableScheduling` on the main application class.

### Load Testing

The project includes a k6 load test script (`load-test.js`):

```powershell
# Install k6: https://k6.io/docs/getting-started/installation/
# Start the application
docker-compose up -d

# Run load test
k6 run load-test.js

# Run with custom config
k6 run --vus 100 --duration 120s load-test.js
```

The load test covers:
- JWT token acquisition
- Purchase flow under concurrent load
- Idempotency key generation per request
- Configurable VUs and thresholds (p95 < 2s, error rate < 10%)

### Production Deployment

For production deployments:
1. **Activate production profile**: `SPRING_PROFILES_ACTIVE=prod` (disables Swagger UI, enables JSON logging, reduces trace sampling)
2. **Enable Vault secrets**: `SPRING_PROFILES_ACTIVE=prod,vault` with `VAULT_ADDR` and `VAULT_TOKEN` environment variables
3. **TLS termination**: Use a reverse proxy (nginx/ALB) with TLS 1.2+ certificates
4. **Horizontal scaling**: Stateless design allows multiple instances behind a load balancer; RabbitMQ provides shared queue processing
5. **Database**: Use managed PostgreSQL (RDS/Cloud SQL) with connection pooling (HikariCP tuned in `application-prod.properties`)
6. **Health checks**: `/actuator/health` for liveness, `/payments/health` for readiness
7. **Monitoring**: Prometheus scraping `/actuator/prometheus`, Jaeger for distributed traces, centralized JSON logs to ELK/Loki

# CI / Coverage

[![CI](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml/badge.svg)](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml)
[![Codecov](https://img.shields.io/codecov/c/github/lenovo/Payment-processing-System.svg)](https://codecov.io/gh/lenovo/Payment-processing-System)

To publish coverage to Codecov from CI, add a repository secret `CODECOV_TOKEN` containing your Codecov upload token.
