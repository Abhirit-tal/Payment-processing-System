# Architecture — Payment Processing System

This document describes the API surface, implemented payment flows, and the database schema / entity relationships for the Payment Processing System (Authorize.Net sandbox integration).

Checklist
- [x] API endpoints (method, path, auth, request/response examples)
- [x] High-level flow descriptions (Purchase, Authorize+Capture, Cancel, Refund)
- [x] DB schema: tables, columns, constraints and example CREATE TABLE SQL
- [x] JPA entity relationship summary
- [x] Payment state machine diagram
- [x] Error handling and response schemas

1. Overview

This service exposes a small REST API for common payment flows backed by Authorize.Net (sandbox). The service secures API endpoints with JWTs issued by a development exchange endpoint.

**Key Architectural Components:**
- **Payment State Machine**: Enforces valid state transitions with integrity guards
- **Idempotency Support**: Prevents duplicate payment processing via idempotency keys
- **Audit Logging**: Comprehensive tracking of all payment operations
- **Error Handling**: Structured error responses with retry guidance

## Payment State Machine

The system uses an explicit state machine to manage payment lifecycle with enforced transitions:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PAYMENT STATE TRANSITIONS                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│    ┌─────────┐                                                              │
│    │ CREATED │──────────────────────────────────┐                          │
│    └────┬────┘                                  │                          │
│         │ initiate_payment                      │                          │
│         ▼                                       │                          │
│    ┌─────────┐                                  │                          │
│    │ PENDING │──────────┬───────────────────────┼────────────────┐         │
│    └────┬────┘          │                       │                │         │
│         │               │                       │                │         │
│    ┌────┴────┐     ┌────┴────┐            ┌────┴────┐      ┌────┴─────┐   │
│    │AUTHORIZED│     │CAPTURED │            │DECLINED │      │  ERROR   │   │
│    └────┬────┘     └────┬────┘            └─────────┘      └────┬─────┘   │
│         │               │                   (terminal)          │         │
│    ┌────┴────┐     ┌────┴────────────────┐                      │         │
│    │         │     │                     │              ┌───────┘         │
│    ▼         ▼     ▼                     ▼              ▼                 │
│ ┌──────┐ ┌──────┐ ┌─────────────────┐ ┌────────┐  ┌─────────┐            │
│ │VOIDED│ │CAPTURE│ │PARTIALLY_REFUNDED│ │REFUNDED│  │ (retry) │            │
│ └──────┘ └──────┘ └────────┬────────┘ └────────┘  └────┬────┘            │
│ (terminal)               │          (terminal)        │                  │
│                          └──────────────────────────────┘                 │
│                                                                             │
│  Also: PENDING → HELD_FOR_REVIEW → AUTHORIZED or DECLINED                  │
└─────────────────────────────────────────────────────────────────────────────┘
```

### State Definitions

| State | Description | Terminal? |
|-------|-------------|-----------|
| CREATED | Order created, payment not yet initiated | No |
| PENDING | Payment request sent, awaiting gateway response | No |
| AUTHORIZED | Auth-only approved, funds held | No |
| CAPTURED | Payment captured, funds will settle | No |
| VOIDED | Auth cancelled before capture | Yes |
| REFUNDED | Full refund processed | Yes |
| PARTIALLY_REFUNDED | Partial refund processed | No |
| DECLINED | Gateway/issuer declined | Yes |
| ERROR | Gateway error (may be retriable) | No |
| HELD_FOR_REVIEW | Flagged for fraud review | No |

### Valid State Transitions

| From State | Allowed Transitions |
|------------|---------------------|
| CREATED | PENDING |
| PENDING | AUTHORIZED, CAPTURED, DECLINED, ERROR, HELD_FOR_REVIEW |
| AUTHORIZED | CAPTURED, VOIDED, ERROR |
| CAPTURED | REFUNDED, PARTIALLY_REFUNDED |
| PARTIALLY_REFUNDED | REFUNDED, PARTIALLY_REFUNDED |
| ERROR | PENDING (retry) |
| HELD_FOR_REVIEW | AUTHORIZED, DECLINED |

2. Authentication

- /auth/token (POST) — Exchanging a `developer_key` for a JWT used as `Authorization: Bearer <token>` on protected endpoints. The `developer_key` is configured via `developer.key` property (default `dev-local-key`).
- Protected endpoints require the Bearer JWT in the `Authorization` header.

3. API Endpoints

3.1. Get a JWT (development)
- POST /auth/token
- Public (no bearer required)
- Request body: { "developer_key": "dev-local-key" }
- Response 200: { "access_token": "<jwt>", "token_type":"bearer", "expires_in":3600 }

3.2. Health
- GET /payments/health
- Public
- Response 200: { "status": "ok" }

3.3. Purchase (auth + capture)
- POST /payments/purchase
- Auth: Bearer
- Request JSON (example):
  {
    "amount": 12.34,
    "currency": "USD",
    "card": { "number":"4111111111111111", "expMonth":12, "expYear":2030, "cvv":"123" },
    "orderId": "ext-100"
  }
- Validation: amount >= 0.01, currency non-empty, card validated (Luhn + expiry + CVV rules).
- Response 201 (success example):
  { "order_id": 100, "transaction_id": "1234567890", "status": "success" }
- Errors:
  - 400: validation errors (structured { "errors": { field: message } })
  - 500/failed: provider failure reflected in transaction status = "failed"

3.4. Authorize only
- POST /payments/authorize
- Auth: Bearer
- Request body: same as purchase (card required) but the service performs an authorization only (no immediate capture).
- Response 201: { "order_id": <id>, "transaction_id": "<provider_auth_tx_id>", "status":"success" }

3.5. Capture
- POST /payments/capture
- Auth: Bearer
- Request JSON: { "transactionId": "<provider_auth_tx_id>", "amount": 10.00 }
- If amount omitted, full capture of authorized amount is attempted.
- Response 200 (success example): { "transaction_id":"<capture_tx_id>", "status":"success" }
- Errors:
  - 404: transaction not found
  - 400: validation errors

3.6. Cancel (void)
- POST /payments/cancel
- Auth: Bearer
- Request JSON: { "transactionId": "<provider_auth_tx_id>" }
- Operation voids/ cancels the prior authorization before capture.
- Response 200: { "transaction_id":"<provider_tx_id>", "status":"success" }
- Errors: 404 if transaction not found, provider failure on unsuccessful void.

3.7. Refund
- POST /payments/refund
- Auth: Bearer
- Request JSON: { "transactionId": "<provider_captured_tx_id>", "amount": 5.00, "last4": "1111" }
- `last4` is required by Authorize.Net refund flow (last 4 digits of card). If omitted tests may fail for provider refund.
- If `amount` is omitted, the full captured amount is used.
- Response 200: { "refund_transaction_id":"<refund_tx_id>", "status":"success" }
- Errors: 404 if original transaction not found, provider errors reflected in response.

4. Flow descriptions

4.1. Purchase (single-step)
- Client -> POST /payments/purchase with card and amount
- Service creates an Order (status=processing) and a Transaction (type=purchase, status=pending)
- Service calls Authorize.Net createTransaction with transactionType=AUTH_CAPTURE
- If provider returns success: Transaction.status=success, Order.status=captured
- If provider fails: Transaction.status=failed, Order.status=failed

4.2. Authorize + Capture (two-step)
- Authorize step: POST /payments/authorize -> create order & transaction (type=authorize) -> call createTransaction with AUTH_ONLY
  - On success: Transaction.status=success, Order.status=authorized
- Capture step: POST /payments/capture with provider auth transaction id -> service calls PRIOR_AUTH_CAPTURE
  - On success: new capture Transaction(type=capture) status=success; Order.status=captured

4.3. Cancel (void)
- POST /payments/cancel with provider auth transaction id -> service calls VOID on provider
- If provider voids successfully: Transaction status updated (voided/success) and Order.status=cancelled

4.4. Refund (full & partial)
- POST /payments/refund with captured provider tx id, amount, last4
- Service calls REFUND transaction on provider (requires last4 card digits and usually an expiration date; the client must supply last4)
- On success: new Transaction(type=refund) saved; Order.status=refunded (or partially refunded depending on logic — current implementation sets status=refunded on success)

5. Database schema and entity relationships

The project uses JPA entities `Order` and `Transaction`. An `Order` may have many `Transaction` rows (one-to-many). Key columns are below.

5.1. orders table (mapped from `Order` entity)
- id BIGINT PRIMARY KEY (auto-increment)
- external_id VARCHAR UNIQUE NULLABLE — optional external order id
- currency VARCHAR NOT NULL DEFAULT 'USD'
- amount DECIMAL NOT NULL
- status VARCHAR NOT NULL — values: processing, authorized, captured, refunded, cancelled, failed
- created_at TIMESTAMP NOT NULL
- updated_at TIMESTAMP NOT NULL

Example SQL (PostgreSQL):

```sql
CREATE TABLE orders (
  id BIGSERIAL PRIMARY KEY,
  external_id VARCHAR(255) UNIQUE,
  currency VARCHAR(10) NOT NULL DEFAULT 'USD',
  amount DECIMAL(19,4) NOT NULL,
  status VARCHAR(50) NOT NULL,
  state VARCHAR(50) NOT NULL DEFAULT 'created',
  idempotency_key VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_orders_external_id ON orders(external_id);
CREATE INDEX idx_orders_state ON orders(state);
```

5.2. transactions table (mapped from `Transaction` entity)
- id BIGINT PRIMARY KEY (auto-increment)
- order_id BIGINT NOT NULL -> FOREIGN KEY orders(id)
- type VARCHAR NOT NULL — authorize, capture, purchase, refund, void
- provider_tx_id VARCHAR NULL — provider transaction id (e.g., Authorize.Net transaction id)
- amount DECIMAL NOT NULL
- status VARCHAR NOT NULL — pending, success, failed, voided, refunded
- raw_response CLOB / TEXT — provider raw response (serialized)
- created_at TIMESTAMP NOT NULL

Example SQL:

```sql
CREATE TABLE transactions (
  id BIGSERIAL PRIMARY KEY,
  order_id BIGINT NOT NULL REFERENCES orders(id),
  type VARCHAR(50) NOT NULL,
  provider_tx_id VARCHAR(255),
  amount DECIMAL(19,4) NOT NULL,
  status VARCHAR(50) NOT NULL,
  raw_response TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_transactions_order_id ON transactions(order_id);
CREATE INDEX idx_transactions_provider_tx_id ON transactions(provider_tx_id);
```

5.3. Relationship
- One `orders` row can have many `transactions` rows (1:N). The `transactions.order_id` column references `orders.id`.

6. JPA entity mapping notes
- `Order` entity uses `@Entity @Table(name = "orders")` and fields for externalId, amount, currency, status, createdAt, updatedAt.
- `Transaction` entity uses `@ManyToOne(fetch = FetchType.LAZY)` to reference `Order` and `@JoinColumn(name = "order_id")`.

7. Important implementation details and caveats
- Authorize.Net integration:
  - The project uses the official Authorize.Net Java SDK (anet-java-sdk) via `AuthorizeNetClient`.
  - Sandbox credentials must be supplied via `authnet.api.login.id` and `authnet.transaction.key` (environment or properties).
  - Refunds require the last 4 digits of card and often an expiration date. The `refund` endpoint currently requires `last4` in the request.
  - Some provider operations require the referenced transaction to be in a particular state (e.g., capture only after a successful auth).

- JWT secret handling: if `jwt.secret` is not set or left as default `change-me-please`, the application generates a random signing key for development convenience — do not use that in production.

- DB migrations: the project uses **Flyway** for database migrations with `spring.jpa.hibernate.ddl-auto=validate` to ensure schema integrity. Migration files are located in `src/main/resources/db/migration/`.

8. Postman collection (quick guidance)

If you prefer a Postman collection, create a collection with the following requests:
- `Auth - Token` (POST) -> URL: `{{base_url}}/auth/token` with JSON body { "developer_key": "dev-local-key" }
- `Payments - Health` (GET) -> `{{base_url}}/payments/health`
- `Payments - Purchase` (POST) -> `{{base_url}}/payments/purchase` (Add Bearer token in Authorization using token from Auth)
- `Payments - Authorize` (POST)
- `Payments - Capture` (POST)
- `Payments - Cancel` (POST)
- `Payments - Refund` (POST)

Add a collection-level environment variable `base_url` (e.g., http://localhost:8080) and a bearer token variable populated from the Auth response.

9. Useful example curl flow

1) Obtain token (dev):

curl -s -X POST "http://localhost:8080/auth/token" -H "Content-Type: application/json" -d '{"developer_key":"dev-local-key"}'

2) Purchase (example):

curl -s -X POST "http://localhost:8080/payments/purchase" \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{"amount": 10.50, "currency":"USD", "card": {"number":"4111111111111111","expMonth":12,"expYear":2030,"cvv":"123"}, "orderId":"ext-123"}'

## 10. Error Handling

### Structured Error Response Format

All errors return a consistent JSON structure:

```json
{
  "error": {
    "code": "CARD_DECLINED",
    "message": "The card was declined by the issuing bank",
    "category": "DECLINE_ERROR",
    "retryable": false,
    "provider_error": {
      "code": "2",
      "message": "This transaction has been declined",
      "avs_result": "N",
      "cvv_result": "N"
    },
    "suggestions": [
      "Try a different payment method",
      "Contact your card issuer"
    ],
    "request_id": "req_abc123def456",
    "timestamp": "2026-02-20T10:30:00Z"
  }
}
```

### Error Categories

| Category | HTTP Status | Description | Retryable |
|----------|-------------|-------------|-----------|
| VALIDATION_ERROR | 400 | Invalid request data | No |
| DECLINE_ERROR | 400 | Card declined by issuer | No* |
| STATE_ERROR | 409 | Invalid state transition | No |
| GATEWAY_ERROR | 502/503/504 | Payment gateway issue | Yes |
| INTERNAL_ERROR | 500 | Unexpected server error | Maybe |

*Different card may succeed

### Common Error Codes

| Code | Description |
|------|-------------|
| INVALID_CARD_NUMBER | Card number failed Luhn validation |
| INVALID_EXPIRY | Card expiration date is invalid or past |
| INVALID_CVV | CVV format is incorrect |
| CARD_DECLINED | Gateway declined the transaction |
| INSUFFICIENT_FUNDS | Not enough funds on card |
| INVALID_STATE_TRANSITION | Operation not allowed in current state |
| GATEWAY_TIMEOUT | Gateway did not respond in time |
| TRANSACTION_NOT_FOUND | Referenced transaction does not exist |
| IDEMPOTENCY_CONFLICT | Duplicate request with different body |

## 11. Sequence Diagrams

### Purchase Flow (Happy Path)

```
Client              API              PaymentService        Gateway
  │                  │                     │                  │
  │ POST /purchase   │                     │                  │
  │ ─────────────────>                     │                  │
  │                  │ validate request    │                  │
  │                  │ create Order        │                  │
  │                  │ ────────────────────>                  │
  │                  │                     │ state: CREATED   │
  │                  │                     │ state: PENDING   │
  │                  │                     │                  │
  │                  │                     │ AUTH_CAPTURE     │
  │                  │                     │ ─────────────────>
  │                  │                     │                  │
  │                  │                     │     APPROVED     │
  │                  │                     │ <─────────────────
  │                  │                     │                  │
  │                  │                     │ state: CAPTURED  │
  │                  │ audit: PURCHASE     │                  │
  │                  │ <────────────────────                  │
  │                  │                     │                  │
  │ 201 Created      │                     │                  │
  │ <─────────────────                     │                  │
```

### Authorize + Capture Flow (Two-Step)

```
Client              API              PaymentService        Gateway
  │                  │                     │                  │
  │ POST /authorize  │                     │                  │
  │ ─────────────────>                     │                  │
  │                  │                     │ AUTH_ONLY        │
  │                  │                     │ ─────────────────>
  │                  │                     │     APPROVED     │
  │                  │                     │ <─────────────────
  │                  │                     │ state: AUTHORIZED│
  │ 201 Created      │                     │                  │
  │ <─────────────────                     │                  │
  │                  │                     │                  │
  │    ... later ... │                     │                  │
  │                  │                     │                  │
  │ POST /capture    │                     │                  │
  │ ─────────────────>                     │                  │
  │                  │ check state         │                  │
  │                  │ (must be AUTHORIZED)│                  │
  │                  │                     │ PRIOR_AUTH_CAPTURE│
  │                  │                     │ ─────────────────>
  │                  │                     │     APPROVED     │
  │                  │                     │ <─────────────────
  │                  │                     │ state: CAPTURED  │
  │ 200 OK           │                     │                  │
  │ <─────────────────                     │                  │
```

### Idempotency Flow

```
Client              IdempotencyFilter    IdempotencyService    PaymentService
  │                      │                     │                    │
  │ POST /purchase       │                     │                    │
  │ Idempotency-Key: xyz │                     │                    │
  │ ─────────────────────>                     │                    │
  │                      │ check key           │                    │
  │                      │ ────────────────────>                    │
  │                      │    not found        │                    │
  │                      │ <────────────────────                    │
  │                      │                     │                    │
  │                      │ create & lock key   │                    │
  │                      │ ────────────────────>                    │
  │                      │                     │                    │
  │                      │ process payment     │                    │
  │                      │ ─────────────────────────────────────────>
  │                      │                     │     result         │
  │                      │ <─────────────────────────────────────────
  │                      │                     │                    │
  │                      │ complete key (cache)│                    │
  │                      │ ────────────────────>                    │
  │ 201 Created          │                     │                    │
  │ <─────────────────────                     │                    │
  │                      │                     │                    │
  │ POST /purchase       │                     │                    │
  │ Idempotency-Key: xyz │ (retry)             │                    │
  │ ─────────────────────>                     │                    │
  │                      │ check key           │                    │
  │                      │ ────────────────────>                    │
  │                      │  found & completed  │                    │
  │                      │ <────────────────────                    │
  │ 201 Created (cached) │                     │                    │
  │ <─────────────────────                     │                    │
```

## 12. Additional Tables (New)

### 5.4. idempotency_keys table
```sql
CREATE TABLE idempotency_keys (
    id BIGSERIAL PRIMARY KEY,
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    request_path VARCHAR(255),
    request_method VARCHAR(10),
    response_body TEXT,
    response_status INT,
    order_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    locked_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX idx_idempotency_expires_at ON idempotency_keys(expires_at);
```

### 5.5. audit_logs table
```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor VARCHAR(255),
    actor_ip VARCHAR(45),
    old_value TEXT,
    new_value TEXT,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
```

### 5.6. subscriptions table
```sql
CREATE TABLE subscriptions (
    id BIGSERIAL PRIMARY KEY,
    gateway_subscription_id VARCHAR(255) UNIQUE,
    name VARCHAR(100) NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    interval_length INT NOT NULL,
    interval_unit VARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    card_last4 VARCHAR(4),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TIMESTAMP
);

CREATE INDEX idx_subscription_gateway_id ON subscriptions(gateway_subscription_id);
CREATE INDEX idx_subscription_status ON subscriptions(status);
```

### 5.7. webhook_events table
```sql
CREATE TABLE webhook_events (
    id BIGSERIAL PRIMARY KEY,
    notification_id VARCHAR(255) UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'received',
    processed_at TIMESTAMP,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_notification_id ON webhook_events(notification_id);
CREATE INDEX idx_webhook_event_type ON webhook_events(event_type);
CREATE INDEX idx_webhook_created_at ON webhook_events(created_at);
```

## 14. Recurring Billing Flow

```
┌─────────┐     POST /payments/subscriptions      ┌──────────────────┐
│ Client  │ ──────────────────────────────────────►│ SubscriptionCtrl │
└─────────┘                                        └────────┬─────────┘
                                                            │
                                                   ┌────────▼──────────┐
                                                   │SubscriptionService│
                                                   └────────┬──────────┘
                                                            │ ARBCreateSubscription
                                                   ┌────────▼──────────┐
                                                   │ AuthorizeNetClient│── Authorize.Net ARB API
                                                   └────────┬──────────┘
                                                            │
                                                   ┌────────▼──────────┐
                                                   │SubscriptionRepo   │── persist to DB
                                                   └────────┬──────────┘
                                                            │
                                                   ┌────────▼──────────┐
                                                   │ PaymentEventQueue │── async event
                                                   └───────────────────┘
```

## 15. Webhook Processing Flow

```
┌──────────────┐  POST /webhooks/authorize-net  ┌─────────────────┐
│ Authorize.Net│ ──────────────────────────────►│ WebhookController│
└──────────────┘  (X-ANET-Signature header)     └────────┬────────┘
                                                         │
                                                ┌────────▼────────┐
                                                │ WebhookService  │
                                                │  1. Validate    │ ── SHA-512 HMAC
                                                │     Signature   │
                                                │  2. Deduplicate │ ── check notification_id
                                                │  3. Persist     │ ── webhook_events table
                                                │  4. Publish     │ ── PaymentEventQueue
                                                └────────┬────────┘
                                                         │ async
                                                ┌────────▼────────┐
                                                │PaymentEventQueue│ ── consumer thread
                                                │  → Listeners    │ ── LoggingPaymentEventListener
                                                └─────────────────┘
```

## 16. Design Trade-offs

### Sync vs Async Processing
- **Payment operations**: Synchronous. Client needs immediate response.
- **Webhook handling**: Async via RabbitMQ durable queue. Fast 200 OK to Authorize.Net, then process via consumer.
- **Audit logging**: Critical events (state transitions) sync; non-critical (gateway calls) async via @Async.

### Retry Strategy
- **@Retryable**: 3 attempts, exponential backoff (500ms → 1s → 2s) for `TransientPaymentException`.
- **Circuit Breaker**: Opens after 50% failure rate in sliding window of 10 calls. Half-open after 30s.
  - Config: `resilience4j.circuitbreaker.instances.authorizeNet.*` in `application.properties`
  - Health indicator at `/actuator/health` shows circuit breaker state
- **Rate Limiter**: Resilience4j `@RateLimiter` on all payment endpoints (100 req/s) and webhook endpoint (200 req/s).
  - Config: `resilience4j.ratelimiter.instances.paymentApi.*` and `resilience4j.ratelimiter.instances.webhookApi.*`
  - Returns HTTP 429 with `RATE_LIMIT_EXCEEDED` error code when exceeded
- **Idempotency**: Prevents double-charging on client retries via `Idempotency-Key` header.
- **Pending Transaction Retry**: Background `@Scheduled` job (`PendingTransactionRetryService`) scans for stale PENDING orders every 5 minutes. Retries up to 3 times, then marks as ERROR with audit trail.
- **Error Transaction Retry**: Separate scheduled job retries ERROR-state orders that haven't exhausted max attempts (every 10 minutes).

### Queue Architecture
- **Current**: RabbitMQ-backed durable queues with in-memory `LinkedBlockingQueue` fallback.
  - **Payment Events**: `payment-events-exchange` (topic) → `payment-events-queue` (durable)
  - **Webhook Events**: `webhook-events-exchange` (topic) → `webhook-events-queue` (durable)
  - **Dead Letter Queues**: `payment-events-dlq` and `webhook-events-dlq` for failed messages
  - **Fallback**: If RabbitMQ is unavailable, events dispatched locally via in-memory queue
- **Trade-off**: RabbitMQ provides event durability and multi-instance scaling; fallback ensures graceful degradation. Critical state is persisted to DB before queuing.

### Secrets Management
- **Development**: Environment variables / application.properties.
- **Production**: HashiCorp Vault via `spring-cloud-starter-vault-config` — activate with `SPRING_PROFILES_ACTIVE=prod,vault`.
  - **Vault Profile**: `application-vault.properties` configures Vault URI, Token/AppRole authentication, KV backend
  - **Docker**: `docker-compose --profile vault up` starts Vault for local testing
  - **API Key Rotation**: Configure TTL on Vault secrets; app re-fetches after expiry
- **API Keys**: Never logged, masked in responses, stored only in Vault/env vars at runtime.

### Observability Integration
- **Custom Micrometer Metrics**: `MetricsConfig` registers payment event counters (`payment_events_total`), webhook counters (`webhook_events_total`), subscription counters (`subscription_events_total`), retry metrics (`payment_retry_attempts_total`, `payment_retry_success_total`, `payment_retry_exhausted_total`), and queue depth gauge (`payment_queue_size`) via the `PaymentEventListener` interface — zero coupling to business logic.
- **Distributed Tracing**: `CorrelationIdFilter` injects `X-Correlation-ID` into MDC. OpenTelemetry bridge (`micrometer-tracing-bridge-otel`) exports traces to Jaeger via OTLP HTTP. `traceId`/`spanId` included in all structured log entries.
- **JSON Structured Logging**: `logback-spring.xml` with `logstash-logback-encoder` for production; human-readable console for dev.
- **Jaeger UI**: Accessible at `http://localhost:16686` when using docker-compose.
- **Prometheus Endpoint**: `/actuator/prometheus` exposes all JVM, HTTP, circuit breaker, and custom payment metrics.

## 20. High-Level Component Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                            PAYMENT PROCESSING SYSTEM                            │
│                                                                                 │
│  ┌──────────┐                                                                   │
│  │  Client   │                                                                   │
│  │(Browser/  │                                                                   │
│  │  Mobile)  │                                                                   │
│  └────┬─────┘                                                                   │
│       │ HTTPS + JWT Bearer                                                      │
│       ▼                                                                         │
│  ┌──────────────────────────────────────────────┐                               │
│  │            Spring Boot Application           │                               │
│  │  ┌──────────┐ ┌───────────┐ ┌─────────────┐ │                               │
│  │  │Controllers│ │ Security  │ │ Correlation │ │                               │
│  │  │(REST API)│ │(JWT+Rate) │ │  ID Filter  │ │                               │
│  │  └────┬─────┘ └───────────┘ └─────────────┘ │                               │
│  │       │                                       │                               │
│  │  ┌────▼─────┐ ┌───────────┐ ┌─────────────┐ │                               │
│  │  │ Payment  │ │Subscription│ │  Webhook    │ │                               │
│  │  │ Service  │ │  Service   │ │  Service    │ │                               │
│  │  └────┬─────┘ └─────┬─────┘ └──────┬──────┘ │                               │
│  │       │              │              │         │                               │
│  │  ┌────▼──────────────▼──────────────▼───────┐ │                               │
│  │  │        AuthorizeNetClient (SDK)          │ │                               │
│  │  │   @Retryable + @CircuitBreaker           │ │                               │
│  │  └────────────────┬─────────────────────────┘ │                               │
│  └───────────────────┼───────────────────────────┘                               │
│                      │                                                           │
│       ┌──────────────┼──────────────┬─────────────────┬──────────────┐          │
│       ▼              ▼              ▼                 ▼              ▼          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐     ┌──────────┐  ┌──────────┐      │
│  │PostgreSQL│  │ RabbitMQ │  │Authorize │     │  Jaeger  │  │  Vault   │      │
│  │(Orders,  │  │(Durable  │  │  .Net    │     │(Tracing) │  │(Secrets) │      │
│  │ Audit,   │  │ Queues,  │  │ Sandbox  │     │          │  │          │      │
│  │ Webhooks)│  │  DLQ)    │  │  API     │     │          │  │          │      │
│  └──────────┘  └──────────┘  └──────────┘     └──────────┘  └──────────┘      │
│                                                                                 │
│  ┌──────────────────────────────────────────────────────────────────────┐       │
│  │                    Background Schedulers                              │       │
│  │  • PendingTransactionRetryService (every 5 min)                      │       │
│  │  • WebhookRetryService (every 2 min)                                 │       │
│  │  • BillingCycleService (every 1 hour)                                │       │
│  │  • SubscriptionScheduler (every 6 hours)                             │       │
│  │  • IdempotencyService cleanup (hourly)                               │       │
│  └──────────────────────────────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────────────────┘
```

## 21. Webhook Retry Flow

```
┌─────────────────────────────────┐
│    WebhookRetryService          │
│    @Scheduled(every 2 min)      │
└──────────┬──────────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ Query: webhook_events WHERE  │
│ status='failed'              │
│ AND retry_count < 3          │
│ AND next_retry_at <= now     │
└──────────┬───────────────────┘
           │
     ┌─────▼─────┐
     │ For each   │
     │ event      │
     └──┬──────┬──┘
        │      │
        ▼      ▼ (on exception)
   Reprocess   Increment retry_count
   via queue   Set next_retry_at
        │      (exponential backoff:
        ▼       2^retry * 2 minutes)
   Mark as        │
   "processed"    ▼
                ┌─────────────────┐
                │ retry_count >= 3│
                │ → stays "failed"│
                │ → manual review │
                └─────────────────┘
```

## 22. Billing Cycle Flow

```
┌─────────────────────────────────┐
│    BillingCycleService          │
│    @Scheduled(every 1 hour)     │
└──────────┬──────────────────────┘
           │
           ▼
┌──────────────────────────────┐
│ Query: subscriptions WHERE   │
│ status='active'              │
│ AND next_billing_date <= now │
└──────────┬───────────────────┘
           │
     ┌─────▼──────────────┐
     │ billing_failures   │
     │ >= 3?              │
     └──┬────────────┬────┘
        │ Yes        │ No
        ▼            ▼
   Suspend      Sync with gateway
   subscription  (ARB status check)
                    │
              ┌─────▼──────┐
              │ Gateway OK? │
              └──┬──────┬──┘
                 │ Yes  │ No
                 ▼      ▼
           Advance   Increment
           cycle     failure count
           (set next_billing_date)
```

## 18. Pending Transaction Retry Flow

```
┌─────────────────────────────────┐
│   PendingTransactionRetryService │
│   @Scheduled(every 5 min)       │
└──────────┬──────────────────────┘
           │
           ▼
┌─────────────────────────────┐
│ Query: PENDING orders older  │
│ than 2 minutes               │
└──────────┬──────────────────┘
           │
     ┌─────▼─────┐
     │ retryCount │
     │ >= 3?      │
     └─┬───────┬──┘
       │ Yes   │ No
       ▼       ▼
  Mark ERROR  Increment retryCount
  + Audit     + Check provider_tx_id
              │
        ┌─────▼─────┐
        │ Has txId?  │
        └─┬───────┬──┘
          │ Yes   │ No
          ▼       ▼
     Query      Mark ERROR
     gateway    (no card data
     status     to retry with)
```

## 19. Metrics Architecture

```
PaymentService ──publish──▶ PaymentEventQueue ──dispatch──▶ MetricsConfig (listener)
     │                           │                              │
     │                           │                              ▼
     │                           │                     Micrometer Registry
     │                           │                              │
     │                           ▼                              ▼
     │                   LoggingPaymentEventListener    /actuator/prometheus
     │                   (structured log output)
     │
     ▼
  AuditService ──▶ audit_logs table (persistent record)
```

## 17. Related Documents

- [COMPLIANCE.md](COMPLIANCE.md) - PCI DSS awareness and security guidance
- [OBSERVABILITY.md](OBSERVABILITY.md) - Metrics, tracing, logging strategy
- [TESTING_STRATEGY.md](TESTING_STRATEGY.md) - Test coverage strategy
- [API-SPECIFICATION.yml](API-SPECIFICATION.yml) - OpenAPI specification

---

Generated: 2026-03-09 (Updated)
