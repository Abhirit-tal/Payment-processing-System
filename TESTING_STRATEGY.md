# Testing Strategy — Payment Processing System

This document describes the testing approach, scope, and concrete test cases to validate the payment backend that integrates with Authorize.Net sandbox. It is written to help developers prepare, run, and extend tests in a consistent, reliable way.

Plan (what I will cover in this document)
- Test types and their purpose
- Tooling and configuration
- Unit test guidelines and examples
- Integration and contract tests (sandbox)
- End-to-end and smoke tests (docker-compose)
- Security and edge-case tests
- Test data management, seeding, and cleanup
- CI and coverage requirements

Quick checklist
- [ ] Unit tests for all service and validation logic (mock external SDK) — target 60%+ coverage
- [ ] Controller tests to validate request validation, auth, and response shapes
- [ ] Integration tests for optional Authorize.Net sandbox interactions (guarded by env vars)
- [ ] Smoke tests running in Docker Compose to assert basic app health
- [ ] Add CI job(s) to run unit tests and (optionally) integration tests when credentials are present

1) Test types and purpose

- Unit tests
  - Fast, deterministic, should run offline without credentials.
  - Mock `AuthorizeNetClient` and repositories to assert business logic in `PaymentService`.
  - Validate validators (`CardNumberValidator`, `CardExpiryValidator`) and DTO validation behavior.

- Controller (slice) tests
  - Use Spring's `@WebMvcTest` or `@SpringBootTest` with mocked beans to assert request/response mapping and HTTP status codes.
  - Include validation error cases (missing/invalid fields). Test JWT guard behavior.

- Integration tests (sandbox) — optional / gated
  - Tests that call `AuthorizeNetClient` against a real Authorize.Net sandbox account. These tests must be gated by environment variables and not run by default in CI unless credentials are provided as secure secrets.
  - Validate end-to-end flows: authorize-only, capture, purchase, refund, void. Use dedicated sandbox accounts and avoid reusing production credentials.

- Smoke tests (Docker)
  - Run the app under Docker Compose and run a few simple HTTP checks (health, token endpoint, a dummy purchase with mocked provider or using sandbox creds) to assert the container works.

- Contract tests / API tests (Postman / Newman / Pact)
  - Optionally provide a Postman collection or Pact contracts to guarantee API behavior across teams.

2) Tooling & configuration

- Test framework: JUnit 5 (already used in project)
- Mocking: Mockito
- Spring test utilities: spring-boot-starter-test
- Coverage: JaCoCo (report generation via Maven plugin)
- Integration gating: use a Maven profile `integration` or environment variables (`AUTHNET_API_LOGIN_ID`, `AUTHNET_TRANSACTION_KEY`) to enable sandbox tests.
- Local quick-run: `mvn -DskipTests=false test` (default runs unit tests)
- Integration run: `mvn -P integration -Dauthnet.api.login.id=${AUTHNET_API_LOGIN_ID} -Dauthnet.transaction.key=${AUTHNET_TRANSACTION_KEY} verify`

3) Unit test guidelines and important cases

Goal: keep unit tests fast and deterministic by mocking external calls.

- PaymentService
  - Happy path: `purchase` returns a success transaction and sets order status to `captured`. Mock `authorizeNetClient.createTransaction` to return success.
  - Authorize-only: `authorizeOnly` sets order status to `authorized` on success.
  - Capture: `capture` when auth transaction exists -> mock `captureTransaction` to return success -> assert a new capture transaction persisted and order status updated.
  - Void: `voidTransaction` updates transaction status and order to `cancelled` on success.
  - Refund: `refund` creates a refund transaction; test both full and partial refunds. Guard behavior if `last4` missing (service passes through to client; client will validate).
  - Error cases: provider returns failed or throws exceptions — service should set transaction status to `failed` and not throw unexpected exceptions to controller layer.

- Validators
  - CardNumberValidator: test valid PANs (Visa, Amex) and invalid strings; ensure Luhn check passes/fails appropriately.
  - CardExpiryValidator: test current month/year, past date, invalid months, and AMEX CVV rule (4 digits required).

- Controller tests
  - `PaymentController.purchase` success -> mock PaymentService.purchase and assert 201 + expected JSON fields.
  - Validation errors -> send invalid request body and assert 400 and structured validation errors.
  - Authentication: call protected endpoint without Bearer token -> expect 401 (or 403 depending on filter behavior). With valid token from `JwtTokenProvider.createToken`, expect success.

- Repository tests
  - Use `@DataJpaTest` with H2 to test `OrderRepository.findByExternalId` and `TransactionRepository.findByProviderTxId` behaviour.

4) Integration tests (Authorize.Net sandbox) — best practices

These tests are important but must be optional and gated.

- Gating: run only when both env vars `AUTHNET_API_LOGIN_ID` and `AUTHNET_TRANSACTION_KEY` are present. Use JUnit assumptions or a Maven profile `integration` to skip otherwise.
- Isolation: use unique external order ids (timestamp-based) to avoid collisions.
- Cleanup: Since sandbox transactions may be immutable, ensure tests assert statuses and do not assume irreversible cleanup.
- Test flows to include:
  - Purchase (AUTH_CAPTURE) with test card number `4111111111111111` (Authorize.Net sandbox test card) and assert that provider returns a transaction id and success.
  - Authorize-only then Capture: authorize, then capture with PRIOR_AUTH_CAPTURE.
  - Void: authorize and then void successfully before capture.
  - Refund: perform purchase/capture, then refund full and partial amounts on the captured tx id (provide last4 digits).

5) End-to-end smoke tests (Docker Compose)

- Run `docker compose up --build` to boot the app in a container.
- Run a minimal health and token check sequence using `curl` or an automated script:

  1) Health: GET /payments/health -> 200
  2) Token: POST /auth/token with developer_key -> receive access_token
  3) Purchase: POST /payments/purchase with Authorization header -> expect 201 (if sandbox credentials provided) or failure response with proper shape.

- Automate these checks in CI as a separate smoke job (non-blocking for PRs unless credentials provided securely).

6) Security & negative tests

- JWT validation
  - Expired token should be rejected.
  - Invalid signature should be rejected.

- Rate-limiting & brute force (optional)
  - Test repeated requests to ensure the service behaves under repeated calls; add circuit-breaker/limiter tests if implemented.

- Input fuzzing and invalid shapes
  - Malformed JSON, missing required fields, invalid data types — controller must respond with 400 and descriptive errors.

7) Test data management & DB

- Use H2 in-memory database for unit tests and small integration tests.
- For reproducible DB schema changes, adopt Flyway or Liquibase migrations and run them in integration tests to reproduce production-like schema.
- Use unique external IDs for tests involving real provider interactions.

8) Coverage and CI

- Coverage target: >= 60% across the project. Enforce coverage in CI with JaCoCo report parsing and a threshold check if desired.
- CI jobs:
  - `unit-tests` — runs `mvn test` and publishes JaCoCo summary and surefire reports.
  - `integration-tests` (optional) — runs `mvn -P integration verify` only if secrets are present.
  - `smoke-tests` — uses Docker to build the image and run basic health checks.

9) Example test cases (concise)

- Unit: Purchase success
  - Arrange: mock `authorizeNetClient.createTransaction` to return {status: "success", provider_tx_id: "prov-1", raw: {...}}
  - Act: call `paymentService.purchase(10.00, "USD", cardMap, "ext-1")`
  - Assert: returned Transaction.status == "success"; order.status == "captured"; providerTxId persisted

- Unit: Capture missing tx
  - Arrange: `transactionRepository.findByProviderTxId` returns empty
  - Act: paymentService.capture("nonexistent", 5.00)
  - Assert: Optional.empty()

- Controller: Purchase validation error
  - Send request with amount = 0 -> expect 400 and error details listing `amount`

- Integration (guarded): Authorize + Capture
  - Preconditions: sandbox creds set
  - Authorize a $1.00 transaction -> assert provider tx id
  - Capture that tx id -> assert capture success

10) Running tests locally (commands)

- Run unit tests and generate JaCoCo HTML report:

```bash
mvn test
mvn jacoco:report
# Open target/site/jacoco/index.html
```

- Run integration tests with profile (provide env vars):

```bash
# Windows PowerShell example
$env:AUTHNET_API_LOGIN_ID = "your_login"
$env:AUTHNET_TRANSACTION_KEY = "your_key"
mvn -P integration verify
```

- Run smoke tests with Docker Compose (build + health checks):

```bash
docker compose up --build
# In another shell: run the curl checks from Architecture.md
```

11) Test maintenance and ownership

- Each feature change must include tests covering new behavior and a short note in PR describing testing performed.
- Keep a central list of long-running sandbox tests (which are expensive / rate-limited) — only run them on demand or in an isolated environment.

12) Next steps to improve test quality

- Add contract tests (Pact) if multiple clients depend on API.
- Add property-based testing for validation logic (e.g., fuzz generate PANs and expiry combos).
- Introduce testcontainers or a local Postgres in CI to move towards production-like DB testing.


Generated: 2026-01-05

