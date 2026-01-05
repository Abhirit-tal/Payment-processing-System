# Architecture — Payment Processing System

This document describes the API surface, implemented payment flows, and the database schema / entity relationships for the Payment Processing System (Authorize.Net sandbox integration).

Checklist
- [x] API endpoints (method, path, auth, request/response examples)
- [x] High-level flow descriptions (Purchase, Authorize+Capture, Cancel, Refund)
- [x] DB schema: tables, columns, constraints and example CREATE TABLE SQL
- [x] JPA entity relationship summary

1. Overview

This service exposes a small REST API for common payment flows backed by Authorize.Net (sandbox). The service secures API endpoints with JWTs issued by a development exchange endpoint.

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

Example SQL (H2 / generic):

CREATE TABLE orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  external_id VARCHAR(255),
  currency VARCHAR(10) NOT NULL DEFAULT 'USD',
  amount DECIMAL(19,4) NOT NULL,
  status VARCHAR(50) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  UNIQUE (external_id)
);

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

CREATE TABLE transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  type VARCHAR(50) NOT NULL,
  provider_tx_id VARCHAR(255),
  amount DECIMAL(19,4) NOT NULL,
  status VARCHAR(50) NOT NULL,
  raw_response CLOB,
  created_at TIMESTAMP NOT NULL,
  FOREIGN KEY (order_id) REFERENCES orders(id)
);

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

- DB migrations: the current project relies on `spring.jpa.hibernate.ddl-auto=update` for quick local runs. For production use, add Flyway/Liquibase migrations.

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

10. Next steps (recommended)
- Add database migrations and adjust production-ready configuration.
- Improve error mapping with structured error codes and provider error translation.
- Add more tests for partial refunds, concurrency, and edge cases.
- Add metrics, logging correlation ids and request tracing for provider calls.


---

Generated: 2026-01-05

