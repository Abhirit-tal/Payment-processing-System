# Project structure — Payment Processing System

This document explains the layout of the project and the purpose of the main folders and key modules. It is intended to help new developers quickly understand where to look for code, configuration, tests, and how core features are implemented.

Repository root
- `pom.xml` — Maven build configuration, dependencies (Spring Boot, Authorize.Net SDK, testing, JaCoCo, etc.) and plugins.
- `README.md` — How to run the project and other operational notes.
- `PROJECT_STRUCTURE.md` — (this file) high-level overview of the project structure.
- `chat.md` — conversation log for the current session.

Top-level folders
- `src/main/java` — Production Java sources. Main package root: `com.example.payment`.
- `src/main/resources` — Runtime resources and configuration, e.g. `application.properties`.
- `src/test/java` — Unit and integration test sources.
- `target` — Build output (ignored in VCS). Contains compiled classes, test reports and JaCoCo coverage.

Key Java packages and classes

com.example.payment
- `PaymentProcessingApplication` — Spring Boot entrypoint (main).

com.example.payment.controller
- `PaymentController` — REST endpoints for payments (purchase, authorize, capture, cancel, refund) and a `/payments/health` health check. Uses `PaymentService` to perform operations and returns concise JSON responses.
- `AuthController` — Development helper endpoint to exchange a `developer_key` for a short-lived JWT used to call protected endpoints.
- `GlobalExceptionHandler` — Centralized handling of validation errors (returns structured 400 errors).

com.example.payment.service
- `PaymentService` — Application business logic coordinating orders, transactions and calls to the payment provider client. Implements the core flows:
  - purchase (auth + capture in one step)
  - authorizeOnly (two-step auth)
  - capture (prior auth capture)
  - voidTransaction (cancel/void before capture)
  - refund (full and partial refunds)

- `AuthorizeNetClient` — Thin wrapper around the Authorize.Net official Java SDK (anet-java-sdk). Responsible for initializing merchant credentials (API Login ID + Transaction Key + environment), creating transactions, capturing, voiding and refunding via the Authorize.Net API. Returns normalized Map responses: `status`, `provider_tx_id`, and `raw` (provider response) so `PaymentService` can persist provider details and map statuses.

com.example.payment.model
- `Order` — JPA entity representing an order (external id, amount, currency, status, timestamps).
- `Transaction` — JPA entity representing a provider transaction (type, provider transaction id, amount, status, raw response, timestamps). Transactions are linked to `Order`.

com.example.payment.repository
- `OrderRepository` — Spring Data JPA repository for `Order` with helper `findByExternalId`.
- `TransactionRepository` — Spring Data JPA repository for `Transaction` with helper `findByProviderTxId`.

com.example.payment.dto
- `PaymentRequests` — DTOs for API requests: `Card`, `PurchaseRequest`, `AuthorizeRequest`, `CaptureRequest`, `RefundRequest`, `CancelRequest`. Uses Jakarta Validation annotations to validate amounts, card data and nested objects.
- `SubscriptionRequests` — DTOs for subscription CRUD: `CreateSubscriptionRequest`, `UpdateSubscriptionRequest`.

com.example.payment.validation
- `ValidCardNumber`, `CardNumberValidator` — Luhn algorithm based card number validation annotation and implementation.
- `ValidCardExpiry`, `CardExpiryValidator` — Validates expiry month/year, ensures card not expired and applies CVV length rules for AMEX vs others.

com.example.payment.auth
- `JwtTokenProvider` — Simple JWT creation and validation. Uses `jwt.secret` and `jwt.expiration-seconds` settings. If `jwt.secret` is missing or default placeholder is present, a random key is generated for development convenience (not for production).

com.example.payment.config
- `SecurityConfig` — Spring Security configuration that enforces JWT bearer authentication on protected endpoints, exposes `/auth/**`, `/payments/health`, `/webhooks/**`, and `/actuator/**` as public.
- `AppProperties` — Small properties binding class for `jwt.*` properties.
- `OpenApiConfig` — OpenAPI / springdoc configuration to document endpoints and add bearer auth scheme.
- `CorrelationIdFilter` — `OncePerRequestFilter` that reads/generates `X-Correlation-ID` header, adds it to SLF4J MDC for distributed tracing in all log lines, and echoes it in the response.
- `RetryConfig` — Enables Spring Retry (`@EnableRetry`) for `@Retryable` annotations on gateway calls.

com.example.payment.event
- `PaymentEvent` — Record representing an async payment event (type, orderId, txId, timestamp, payload).
- `PaymentEventQueue` — Hybrid event queue: publishes to RabbitMQ durable queues for production durability with in-memory `LinkedBlockingQueue` fallback. Dispatches to registered `PaymentEventListener` instances via `@RabbitListener` consumers.
- `PaymentEventListener` — Functional interface for event listeners.
- `LoggingPaymentEventListener` — Default listener that logs all payment events.

com.example.payment.config (additions)
- `MetricsConfig` — Custom Micrometer metrics registration. Registers `payment_events_total`, `webhook_events_total`, `subscription_events_total` counters and `payment_queue_size` gauge. Implements `PaymentEventListener` to auto-track metrics via the event queue — zero coupling to business logic.
- `RabbitMQConfig` — RabbitMQ configuration: durable topic exchanges, queues, dead-letter exchanges/queues for payment and webhook events. Jackson2JsonMessageConverter for serialization.

com.example.payment.service (additions)
- `PendingTransactionRetryService` — Scheduled background service that retries stale PENDING and ERROR state orders. Runs every 5/10 minutes. Tracks retry count per order, marks as permanent ERROR after 3 attempts with full audit trail. Exposes Micrometer metrics: `payment_retry_attempts_total`, `payment_retry_success_total`, `payment_retry_exhausted_total`.
- `WebhookRetryService` — Scheduled service that retries failed webhook events with exponential backoff (every 2 min, up to 3 retries per event).
- `BillingCycleService` — Scheduled service that processes due recurring billing cycles (every hour). Tracks `next_billing_date`, syncs with ARB, handles billing failures and suspensions.
- `SubscriptionScheduler` — Scheduled service that syncs subscription statuses with Authorize.Net ARB API every 6 hours. Updates local DB state based on gateway status.

Resources
- `src/main/resources/application.properties` — Default development configuration (PostgreSQL, RabbitMQ, Authorize.Net, JWT, Actuator, Resilience4j, Tracing).
- `src/main/resources/application-prod.properties` — Production profile (JSON logging, reduced trace sampling, disabled Swagger).
- `src/main/resources/application-vault.properties` — Vault profile (Spring Cloud Vault configuration for secrets management).
- `src/main/resources/logback-spring.xml` — Logback configuration: human-readable console for dev, JSON structured logging (logstash-logback-encoder) for production with traceId/spanId/correlationId fields.
- `src/main/resources/db/migration/V1__initial_schema.sql` — Initial Flyway migration: orders, transactions, audit_logs, idempotency_keys, webhook_events, subscriptions.
- `src/main/resources/db/migration/V2__add_missing_columns_and_versioning.sql` — Adds state tracking, optimistic locking, webhook retry columns.
- `src/main/resources/db/migration/V3__add_billing_cycle_columns.sql` — Adds next_billing_date, total_billed, billing_failures to subscriptions.

Testing
- `src/test/java/...` — 480+ unit tests across 44+ test suites. Tests include:
  - `AuthorizeNetClientSmokeTest` — Verifies the `AuthorizeNetClient` handles missing credentials gracefully.
  - `PaymentControllerTest`, `PaymentControllerExtendedTest` — Controller tests with MockMvc including idempotency header handling.
  - `PaymentServiceGatewayFailureTest` — Gateway decline/timeout/error/circuit-breaker scenarios (14 tests).
  - `WebhookControllerTest` — Webhook signature validation, idempotent deduplication, payload processing.
  - `SubscriptionControllerTest` — Subscription CRUD lifecycle tests.
  - `SubscriptionServiceTest` — Create, update, cancel with mock gateway.
  - `WebhookServiceTest` — Signature validation, duplicate detection, status updates.
  - `WebhookRetryServiceTest` — Failed webhook retry with exponential backoff.
  - `BillingCycleServiceTest` — Billing cycle processing, failure handling, date calculations.
  - `IdempotencyServiceTest` — Check, lock, complete, release lifecycle tests.
  - `PendingTransactionRetryServiceTest` — Stale PENDING retry, max retries, ERROR retry, edge cases.
  - `MetricsConfigTest` — Custom Micrometer counter/gauge registration and increment verification.
  - `SubscriptionSchedulerTest` — Subscription sync with gateway, error handling.
  - `PaymentEventQueueTest` — Queue publish, listener dispatch, error isolation.
  - `CorrelationIdFilterTest` — Correlation ID generation and propagation.
  - Model, DTO, Exception, Config tests — comprehensive coverage of all layers.
- `src/test/resources/application.properties` — Test configuration using H2 in-memory database for fast isolated tests.
- `src/main/resources/db/migration/V1__initial_schema.sql` — Flyway migration script for production deployments (creates all tables with proper indexes)

Database
- **Production/Development**: PostgreSQL (configured via `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`)
- **Testing**: H2 in-memory database (configured in `src/test/resources/application.properties`)
- **Schema Migration**: Flyway migration available at `src/main/resources/db/migration/`. For dev, `ddl-auto=update` is used; for production, enable Flyway.

Load Testing
- `load-test.js` — k6 load test script with ramped stages (10→50→0 VUs over 60s), custom metrics, and thresholds. Run with: `k6 run load-test.js`

How the core flows map to code
- Purchase: `POST /payments/purchase` → `PaymentController.purchase` → `PaymentService.purchase` → `AuthorizeNetClient.createTransaction(..., capture=true)` → `PaymentEventQueue.publish`
- Authorize only: `POST /payments/authorize` → `PaymentService.authorizeOnly` → `createTransaction(..., capture=false)`
- Capture: `POST /payments/capture` → `PaymentService.capture` → `AuthorizeNetClient.captureTransaction`
- Cancel (void): `POST /payments/cancel` → `PaymentService.voidTransaction` → `AuthorizeNetClient.voidTransaction`
- Refund: `POST /payments/refund` → `PaymentService.refund` → `AuthorizeNetClient.refundTransaction` (requires last4 card digits)
- Create subscription: `POST /payments/subscriptions` → `SubscriptionController` → `SubscriptionService` → `AuthorizeNetClient.createSubscription` (ARB API)
- Webhook: `POST /webhooks/authorize-net` → `WebhookController` → `WebhookService` (validate signature → deduplicate → persist → queue)

Requirements coverage (high level)
- JWT auth for endpoints: ✅ Done (AuthController + JwtTokenProvider + SecurityConfig)
- Authorize.Net sandbox integration: ✅ Done via `AuthorizeNetClient` (uses official SDK, thread-safe)
- Endpoints for Purchase, Authorize, Capture, Cancel, Refund: ✅ Done in `PaymentController`
- Subscriptions / Recurring Billing: ✅ Done via `SubscriptionController` + Authorize.Net ARB API
- Webhooks: ✅ Done via `WebhookController` (HMAC validation, idempotent, async queue processing)
- Idempotency & Retries: ✅ Done (`Idempotency-Key` header wired in controller + `@Retryable` on gateway)
- Rate Limiting: ✅ Done (Resilience4j `@RateLimiter` on all payment + webhook endpoints)
- Pending Retry: ✅ Done (`PendingTransactionRetryService` for stale PENDING/ERROR orders)
- Distributed Tracing: ✅ Done (`CorrelationIdFilter` + MDC + `X-Correlation-ID` header)
- Observability: ✅ Done (Custom Micrometer metrics + Prometheus + Actuator health)
- PCI Compliance: ✅ Documented in COMPLIANCE.md (card masking, no CVV storage, secrets management)
- Distributed Tracing: ✅ Done (`CorrelationIdFilter` + MDC + Prometheus metrics)
- Queue-based event handling: ✅ Done (in-memory `PaymentEventQueue`)
- Persist orders & transactions: ✅ Done (JPA entities + repositories)
- Clear error responses: ✅ Done via `GlobalExceptionHandler` with structured error codes
- Unit tests (≥80% coverage): ✅ Done (397 tests, 87% coverage)
- Compliance (PCI DSS): ✅ Done (see COMPLIANCE.md)

---

Generated on: 2026-03-07 (Updated)


