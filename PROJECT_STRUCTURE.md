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

com.example.payment.validation
- `ValidCardNumber`, `CardNumberValidator` — Luhn algorithm based card number validation annotation and implementation.
- `ValidCardExpiry`, `CardExpiryValidator` — Validates expiry month/year, ensures card not expired and applies CVV length rules for AMEX vs others.

com.example.payment.auth
- `JwtTokenProvider` — Simple JWT creation and validation. Uses `jwt.secret` and `jwt.expiration-seconds` settings. If `jwt.secret` is missing or default placeholder is present, a random key is generated for development convenience (not for production).

com.example.payment.config
- `SecurityConfig` — Spring Security configuration that enforces JWT bearer authentication on protected endpoints, exposes `/auth/**`, `/payments/health` and `/h2-console/**` as public.
- `AppProperties` — Small properties binding class for `jwt.*` properties.
- `OpenApiConfig` — (if present) OpenAPI / springdoc configuration to document endpoints and add bearer auth scheme.

Testing
- `src/test/java/...` — Unit tests and smoke/integration tests. Tests include:
  - `AuthorizeNetClientSmokeTest` — Verifies the `AuthorizeNetClient` fails gracefully when credentials are missing (helpful for local dev without sandbox creds).
  - Controller and service unit tests — tests that mock `PaymentService`/`AuthorizeNetClient` to validate controller behavior and service logic.
  - Integration tests (guarded) — some integration tests may be present and configured to run only when Authorize.Net sandbox credentials are provided via environment variables.

Runtime configuration
- `src/main/resources/application.properties` contains default settings used during local development:
  - H2 in-memory datasource (used for quick local runs and tests)
  - `authnet.environment` (default `sandbox`) and placeholders for `authnet.api.login.id` and `authnet.transaction.key` (set these via environment variables or override in local properties when running integration tests)
  - `jwt.secret` and `jwt.expiration-seconds`
  - H2 console enabled for convenience

How the core flows map to code
- Purchase: `POST /payments/purchase` -> `PaymentController.purchase` -> `PaymentService.purchase` -> `AuthorizeNetClient.createTransaction(..., capture=true)`
- Authorize only: `POST /payments/authorize` -> `PaymentService.authorizeOnly` -> `createTransaction(..., capture=false)`
- Capture: `POST /payments/capture` -> `PaymentService.capture` -> `AuthorizeNetClient.captureTransaction`
- Cancel (void): `POST /payments/cancel` -> `PaymentService.voidTransaction` -> `AuthorizeNetClient.voidTransaction`
- Refund: `POST /payments/refund` -> `PaymentService.refund` -> `AuthorizeNetClient.refundTransaction` (requires last4 card digits)

Configuration & environment notes
- To run real Authorize.Net sandbox interactions, set the following environment variables or override them in application properties:
  - `authnet.api.login.id` — your sandbox API Login ID
  - `authnet.transaction.key` — your sandbox Transaction Key
  - Optionally `authnet.environment=production` to switch environments (not recommended for tests)
- For local development you can use the H2 console (`/h2-console`) and the generated JWT token endpoint (`POST /auth/token`) with `developer_key` from config (property `developer.key`).

Next steps & recommended improvements
- Add database migrations (Flyway/Liquibase) to manage schema changes instead of relying on `spring.jpa.hibernate.ddl-auto=update`.
- Improve error mapping: map Authorize.Net error codes/messages into structured error responses and distinct HTTP statuses.
- Add more unit tests to push coverage above 60% if needed; add tests for edge cases (concurrent captures/refunds, partial refunds, invalid amounts).
- Add integration tests that use a sandbox account and run conditionally in CI with secrets configured.
- Add monitoring/observability: basic metrics and logs for external provider latency and error rates.

Requirements coverage (high level)
- JWT auth for endpoints: implemented (AuthController + JwtTokenProvider + SecurityConfig) — Done
- Authorize.Net sandbox integration: implemented via `AuthorizeNetClient` (uses official SDK) — Done
- Endpoints for Purchase, Authorize, Capture, Cancel, Refund: implemented in `PaymentController` — Done
- Persist orders & transactions: implemented (JPA entities + repositories) — Done
- Clear error responses for validation: implemented via `GlobalExceptionHandler` — Done
- Unit tests & smoke tests included: present in `src/test/java` — Done (coverage report produced by build)

If you'd like, I can:
- Generate a short `DEVELOPMENT.md` with run commands and examples for each API endpoint.
- Add a `curl` examples section and an OpenAPI JSON export.
- Run the project's tests and show the current JaCoCo coverage summary.

---

Generated on: 2026-01-05


