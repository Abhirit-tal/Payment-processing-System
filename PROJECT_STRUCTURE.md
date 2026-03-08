# Project structure — Payment Processing System

This document explains the layout of the project and the purpose of the main folders and key modules. It is intended to help new developers quickly understand where to look for code, configuration, tests, and how core features are implemented.

## Repository root

- `pom.xml` — Maven build configuration, dependencies (Spring Boot, Authorize.Net SDK, testing, JaCoCo, etc.) and plugins.
- `README.md` — How to run the project and other operational notes.
- `PROJECT_STRUCTURE.md` — (this file) high-level overview of the project structure.
- `Architecture.md` — API endpoints, payment flows, DB schema, state machine, design trade-offs.
- `API-SPECIFICATION.yml` — OpenAPI 3.0 specification for all REST endpoints.
- `COMPLIANCE.md` — PCI DSS compliance awareness, secrets management, rate limits, audit.
- `OBSERVABILITY.md` — Metrics list, tracing/logging strategy, alerting guidance.
- `TESTING_STRATEGY.md` — Test plan and strategy.
- `TEST_REPORT.md` — Unit test coverage summary (verified JaCoCo numbers).
- `CHAT_HISTORY.md` — AI collaboration dialogue and design decision log.
- `IMPROVEMENT_PLAN.md` — Roadmap for future improvements.
- `docker-compose.yml` — Docker Compose to start all infrastructure (PostgreSQL, RabbitMQ, Jaeger, Vault, app).
- `Dockerfile` — Multi-stage Docker build for the Spring Boot application.
- `load-test.js` — k6 load test script.

## Top-level folders

- `src/main/java` — Production Java sources. Main package root: `com.example.payment`.
- `src/main/resources` — Runtime resources and configuration, e.g. `application.properties`.
- `src/test/java` — Unit and integration test sources.
- `target` — Build output (ignored in VCS). Contains compiled classes, test reports and JaCoCo coverage.
- `Screenshots/` — Reference screenshots (Authorize.Net sandbox, local DB).

## Key Java packages and classes

### com.example.payment
- `PaymentProcessingApplication` — Spring Boot entrypoint (main). Enables `@EnableScheduling` for background jobs.

### com.example.payment.controller
- `PaymentController` — REST endpoints for payments (purchase, authorize, capture, cancel, refund) and `/payments/health`. Integrates idempotency via `Idempotency-Key` header. Uses `@RateLimiter` on all endpoints.
- `SubscriptionController` — REST endpoints for recurring billing CRUD (create, get, update, cancel subscriptions).
- `WebhookController` — Receives Authorize.Net webhook callbacks. Validates HMAC signatures, deduplicates by `notificationId`, and queues for async processing.
- `AuthController` — Development helper endpoint to exchange a `developer_key` for a short-lived JWT used to call protected endpoints.
- `GlobalExceptionHandler` — Centralized `@ControllerAdvice` handling validation errors, gateway failures (decline, timeout, transient, permanent), state transition errors, idempotency conflicts, rate limiting (429), and generic exceptions. Sanitizes sensitive card field values from validation error messages. Returns structured `PaymentErrorResponse` with error codes, categories, retry guidance, and provider details.

### com.example.payment.service
- `PaymentService` — Core business logic coordinating orders, transactions and calls to the payment provider. Implements all payment flows:
  - `purchase` (auth + capture in one step)
  - `authorizeOnly` (two-step auth)
  - `capture` (prior auth capture)
  - `voidTransaction` (cancel/void before capture)
  - `refund` (full and partial refunds)
  Uses `PaymentStateMachine` for state transition integrity, `AuditService` for audit trails, and `PaymentEventQueue` for async event publishing.

- `PaymentStateMachine` — Enforces valid payment state transitions (CREATED→PENDING→AUTHORIZED→CAPTURED→REFUNDED etc.). Validates transitions and provides error messages for invalid state changes.

- `AuthorizeNetClient` — Thin wrapper around the Authorize.Net official Java SDK (anet-java-sdk). Handles createTransaction, captureTransaction, voidTransaction, refundTransaction, createSubscription, getSubscription, updateSubscription, cancelSubscription. Decorated with `@Retryable` and `@CircuitBreaker` for resilience.

- `IdempotencyService` — Manages idempotency keys: check existing, create and lock, complete with response, release on error. Uses pessimistic DB locking (`SELECT FOR UPDATE`) for race condition safety. Detects stale locks (5 min timeout). Scheduled cleanup of expired keys.

- `SubscriptionService` — Creates, updates, cancels subscriptions via Authorize.Net ARB API.

- `SubscriptionScheduler` — Scheduled service (every 6 hours) that syncs subscription statuses with Authorize.Net ARB API.

- `BillingCycleService` — Scheduled service (every hour) that processes due billing cycles. Tracks `next_billing_date`, failure counts, and suspends subscriptions after 3 consecutive failures.

- `PendingTransactionRetryService` — Scheduled service that retries stale PENDING orders (every 5 min) and ERROR orders (every 10 min). Reconciles with gateway status. Max 3 retries, then marks as permanent ERROR with audit trail. Exposes Micrometer metrics.

- `WebhookService` — Processes incoming webhooks: validates SHA-512 HMAC signature, deduplicates by `notificationId`, persists to `webhook_events` table, publishes to event queue.

- `WebhookRetryService` — Scheduled service (every 2 min) that retries failed webhook events with exponential backoff (up to 3 retries).

- `AuditService` — Comprehensive audit logging. Persists state transitions, gateway calls, order creation, transaction creation, and errors to `audit_logs` table.

### com.example.payment.model
- `Order` — JPA entity representing an order (external id, amount, currency, state, retry count, timestamps). Uses `PaymentState` for state management and `@Version` for optimistic locking.
- `Transaction` — JPA entity representing a provider transaction (type, provider tx id, amount, status, raw response). Linked to `Order` via `@ManyToOne`.
- `PaymentState` — Enum defining all valid payment states (CREATED, PENDING, AUTHORIZED, CAPTURED, VOIDED, REFUNDED, PARTIALLY_REFUNDED, DECLINED, ERROR, HELD_FOR_REVIEW) with transition validation logic.
- `GatewayResponseType` — Enum mapping Authorize.Net response codes (1=APPROVED, 2=DECLINED, 3=ERROR, 4=HELD_FOR_REVIEW) and transaction statuses to internal payment states.
- `AuditLog` — JPA entity for persistent audit trail (entity type, entity id, action, actor, old/new values, metadata).
- `IdempotencyKey` — JPA entity for idempotency key storage (key, request hash, response cache, lock/complete timestamps, expiry).
- `Subscription` — JPA entity for recurring billing subscriptions (gateway ID, name, amount, interval, status, billing dates, failure counts).
- `WebhookEvent` — JPA entity for webhook event persistence (notification ID, event type, payload, status, retry count).

### com.example.payment.repository
- `OrderRepository` — Spring Data JPA repository for `Order` with helpers for `findByExternalId`, stale pending orders, error state orders.
- `TransactionRepository` — Spring Data JPA repository for `Transaction` with `findByProviderTxId` and order-based lookups.
- `IdempotencyKeyRepository` — Repository with pessimistic-locked `findValidKey()` query and expired key cleanup.
- `AuditLogRepository` — Repository for audit log queries (by entity type, entity ID, date range).
- `SubscriptionRepository` — Repository for subscription queries (by status, due billing dates, gateway ID).
- `WebhookEventRepository` — Repository for webhook event queries (by notification ID, failed events for retry).

### com.example.payment.dto
- `PaymentRequests` — DTOs for API requests: `Card` (with masked `toString()` and `toGatewayMap()` helper), `PurchaseRequest`, `AuthorizeRequest`, `CaptureRequest`, `RefundRequest`, `CancelRequest`. Uses Jakarta Validation annotations.
- `SubscriptionRequests` — DTOs for subscription CRUD: `CreateSubscriptionRequest`, `UpdateSubscriptionRequest`.
- `PaymentErrorCode` — Enum of all error codes (CARD_DECLINED, INSUFFICIENT_FUNDS, GATEWAY_TIMEOUT, etc.) with HTTP status mapping, categories, retryability, and user-facing suggestions.
- `PaymentErrorResponse` — Structured error response DTO with builder pattern. Includes error code, message, category, retryability, provider error details, request ID, timestamp, retry-after hint, and suggestions.

### com.example.payment.exception
- `PaymentException` — Base exception for all payment errors.
- `TransientPaymentException` — Retriable gateway errors (timeout, transient failure).
- `PermanentPaymentException` — Non-retriable errors (validation, permanent decline).
- `GatewayTimeoutException` — Specific timeout exception.
- `GatewayDeclinedException` — Card declined with provider error details.
- `InvalidStateTransitionException` — Invalid payment state transition attempts.
- `IdempotencyConflictException` — Duplicate idempotency key with different request body, or key currently being processed.

### com.example.payment.validation
- `ValidCardNumber`, `CardNumberValidator` — Luhn algorithm based card number validation annotation and implementation.
- `ValidCardExpiry`, `CardExpiryValidator` — Validates expiry month/year, ensures card not expired, and applies CVV length rules for AMEX vs others.

### com.example.payment.auth
- `JwtTokenProvider` — JWT creation and validation using HMAC-SHA. Uses `jwt.secret` and `jwt.expiration-seconds` settings. Generates a random key for dev if none configured.

### com.example.payment.config
- `SecurityConfig` — Spring Security configuration enforcing JWT bearer on protected endpoints. Public paths: `/auth/**`, `/payments/health`, `/webhooks/**`, `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
- `JwtFilter` — `OncePerRequestFilter` that extracts JWT from `Authorization: Bearer` header, validates it, and sets `SecurityContext`.
- `AppProperties` — `@ConfigurationProperties` binding for `jwt.*` and `developer.*` properties.
- `OpenApiConfig` — OpenAPI / springdoc configuration with bearer auth scheme.
- `CorrelationIdFilter` — `OncePerRequestFilter` that reads/generates `X-Correlation-ID` header, adds to SLF4J MDC for distributed tracing, echoes in response.
- `MetricsConfig` — Custom Micrometer metrics registration: `payment_events_total`, `webhook_events_total`, `subscription_events_total` counters and `payment_queue_size` gauge. Implements `PaymentEventListener` for zero-coupling metric tracking.
- `RabbitMQConfig` — RabbitMQ configuration: durable topic exchanges (`payment-events-exchange`, `webhook-events-exchange`), queues, dead-letter exchanges/queues, `Jackson2JsonMessageConverter`.
- `RetryConfig` — Enables Spring Retry (`@EnableRetry`) for `@Retryable` annotations on gateway calls.
- `StartupValidator` — Validates critical configuration at startup (JWT secret strength, Authorize.Net credentials presence). Logs warnings for missing/weak config.
- `AuthorizeNetHealthIndicator` — Custom Spring Boot Actuator health indicator that checks Authorize.Net sandbox connectivity.

### com.example.payment.event
- `PaymentEvent` — Record representing an async payment event (type, orderId, txId, timestamp, payload).
- `PaymentEventQueue` — Hybrid event queue: publishes to RabbitMQ durable queues with in-memory `LinkedBlockingQueue` fallback when RabbitMQ is unavailable. Null-safe RabbitMQ template handling for graceful degradation. Dispatches to registered `PaymentEventListener` instances.
- `PaymentEventListener` — Functional interface for event listeners.
- `LoggingPaymentEventListener` — Default listener that logs all payment events.

## Resources

- `src/main/resources/application.properties` — Default development configuration (PostgreSQL, RabbitMQ, Authorize.Net, JWT, Actuator, Resilience4j, Tracing, Rate Limiting).
- `src/main/resources/application-prod.properties` — Production profile (JSON logging, TLS scaffolding, reduced trace sampling, disabled Swagger).
- `src/main/resources/application-vault.properties` — Vault profile (Spring Cloud Vault configuration for secrets management).
- `src/main/resources/logback-spring.xml` — Logback configuration: human-readable console for dev, JSON structured logging (`logstash-logback-encoder`) for production with traceId/spanId/correlationId fields.
- `src/main/resources/db/migration/V1__initial_schema.sql` — Flyway migration: orders, transactions, audit_logs, idempotency_keys, webhook_events, subscriptions tables with indexes.
- `src/main/resources/db/migration/V2__add_missing_columns_and_versioning.sql` — Adds state tracking, optimistic locking, webhook retry columns.
- `src/main/resources/db/migration/V3__add_billing_cycle_columns.sql` — Adds next_billing_date, total_billed, billing_failures to subscriptions.

## Testing

- `src/test/java/...` — **471 unit tests** across 44+ test suites. All pass with 0 failures. Key test suites:
  - **Service Tests**: PaymentServiceTest, PaymentServiceExtendedTest (16 tests), PaymentServiceGatewayFailureTest (14 tests — decline/timeout/error/circuit-breaker), PaymentStateMachineTest (39 tests), PaymentStateMachineExtendedTest (38 tests), AuditServiceTest (19 tests), IdempotencyServiceTest (7 tests), PendingTransactionRetryServiceTest (8 tests), WebhookServiceTest (9 tests), WebhookRetryServiceTest (5 tests), SubscriptionServiceTest (10 tests), SubscriptionSchedulerTest (6 tests), BillingCycleServiceTest (11 tests), AuthorizeNetClientSmokeTest (5 tests)
  - **Controller Tests**: PaymentControllerTest, PaymentControllerExtendedTest (14 tests), PaymentControllerValidationTest, PaymentControllerNotFoundTest, GlobalExceptionHandlerTest (16 tests), AuthControllerTest, WebhookControllerTest (5 tests), SubscriptionControllerTest (9 tests)
  - **Config Tests**: JwtFilterTest, AppPropertiesTest, OpenApiConfigTest, CorrelationIdFilterTest, MetricsConfigTest, SecurityConfigTest
  - **Model/DTO/Exception Tests**: PaymentStateTest (23 tests), GatewayResponseTypeTest (19 tests), OrderTest, TransactionTest, AuditLogTest, IdempotencyKeyTest, SubscriptionTest, WebhookEventTest, PaymentErrorCodeTest (41 tests), PaymentErrorResponseTest (17 tests), PaymentRequestsDtoTest (15 tests), ExceptionTest (17 tests), CardNumberValidatorTest, CardBrandCvvValidationTest
  - **Event Tests**: PaymentEventQueueTest (8 tests), LoggingPaymentEventListenerTest (3 tests)
- `src/test/resources/application.properties` — Test configuration using H2 in-memory database.
- **JaCoCo Coverage** (verified): Instructions 82.0%, Lines 81.9%, Branches 66.5%. Open `target/site/jacoco/index.html` for drill-down.

## Database

- **Production/Development**: PostgreSQL (configured via `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)
- **Testing**: H2 in-memory database (configured in `src/test/resources/application.properties`)
- **Schema Migration**: Flyway migrations at `src/main/resources/db/migration/`. For dev, `ddl-auto=update` is used; for production, enable Flyway.
- **Tables**: orders, transactions, audit_logs, idempotency_keys, webhook_events, subscriptions

## Load Testing

- `load-test.js` — k6 load test script with ramped stages (10→50→0 VUs over 60s), custom metrics, and thresholds. Run with: `k6 run load-test.js`

## How the core flows map to code

- **Purchase**: `POST /payments/purchase` → `PaymentController.purchase` → `PaymentService.purchase` → `AuthorizeNetClient.createTransaction(..., capture=true)` → `PaymentEventQueue.publish`
- **Authorize only**: `POST /payments/authorize` → `PaymentService.authorizeOnly` → `createTransaction(..., capture=false)`
- **Capture**: `POST /payments/capture` → `PaymentService.capture` → `AuthorizeNetClient.captureTransaction`
- **Cancel (void)**: `POST /payments/cancel` → `PaymentService.voidTransaction` → `AuthorizeNetClient.voidTransaction`
- **Refund**: `POST /payments/refund` → `PaymentService.refund` → `AuthorizeNetClient.refundTransaction` (requires last4 card digits)
- **Create subscription**: `POST /payments/subscriptions` → `SubscriptionController` → `SubscriptionService` → `AuthorizeNetClient.createSubscription` (ARB API)
- **Webhook**: `POST /webhooks/authorize-net` → `WebhookController` → `WebhookService` (validate signature → deduplicate → persist → queue)

## Requirements coverage

| Requirement | Status | Implementation |
|-------------|--------|----------------|
| JWT auth for endpoints | ✅ | AuthController + JwtTokenProvider + SecurityConfig + JwtFilter |
| Authorize.Net sandbox integration | ✅ | AuthorizeNetClient (official SDK, @Retryable, @CircuitBreaker) |
| Purchase, Authorize, Capture, Cancel, Refund | ✅ | PaymentController + PaymentService |
| Subscriptions / Recurring Billing | ✅ | SubscriptionController + SubscriptionService + ARB API |
| Billing Cycle Scheduler | ✅ | BillingCycleService (hourly, failure tracking, suspension) |
| Webhooks | ✅ | WebhookController + WebhookService (HMAC, idempotent, async queue) |
| Webhook Retry | ✅ | WebhookRetryService (exponential backoff, max 3 retries) |
| Idempotency & Retries | ✅ | Idempotency-Key header + IdempotencyService (pessimistic locking) |
| Rate Limiting | ✅ | Resilience4j @RateLimiter (100 req/s payments, 200 req/s webhooks) |
| Pending Transaction Retry | ✅ | PendingTransactionRetryService (PENDING + ERROR orders) |
| Distributed Tracing | ✅ | CorrelationIdFilter + MDC + OpenTelemetry + Jaeger |
| Observability | ✅ | Custom Micrometer metrics + Prometheus + Actuator health |
| Message Queue | ✅ | RabbitMQ-backed durable queues with in-memory fallback + DLQ |
| Audit Logging | ✅ | AuditService → audit_logs table |
| Persist orders & transactions | ✅ | JPA entities + repositories + Flyway migrations |
| Structured error responses | ✅ | GlobalExceptionHandler + PaymentErrorResponse + PaymentErrorCode |
| PCI Compliance | ✅ | Card masking, no CVV storage, secrets management (see COMPLIANCE.md) |
| Secrets Management | ✅ | HashiCorp Vault profile + StartupValidator warnings |
| Unit tests (≥80% coverage) | ✅ | 471 tests, 82% instruction coverage (verified JaCoCo) |

---

Generated on: 2026-03-09


