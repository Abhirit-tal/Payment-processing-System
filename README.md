# Payment Processing System (Spring Boot)

A minimal Spring Boot backend that integrates with Authorize.Net sandbox (stubbed client for now).

Quick start

1. Build and run tests

```powershell
mvn clean test
```

2. Run the application

```powershell
mvn spring-boot:run
```

3. Configuration

Set environment variables or update `src/main/resources/application.properties`:

- `authnet.api.login.id` - Authorize.Net API Login ID (sandbox)
- `authnet.transaction.key` - Authorize.Net Transaction Key (sandbox)
- `jwt.secret` - secret used to sign JWTs (change from default)
- `developer.key` - developer key used by `/auth/token` endpoint for issuing tokens in dev

Endpoints

- `GET /payments/health` - health check
- `POST /auth/token` - request dev token: {"developer_key":"..."}
- `POST /payments/purchase` - purchase (auth+capture)
- `POST /payments/authorize` - authorize only
- `POST /payments/capture` - capture
- `POST /payments/cancel` - void
- `POST /payments/refund` - refund

Notes

- Authorize.Net client is currently a stub (simulate responses). Replace `AuthorizeNetClient` implementation with actual SDK usage.
- Tests: Basic unit tests are included under `src/test/java`.
- DB: H2 in-memory DB used for simplicity; change to another DB via Spring properties.

Next steps

- Implement real Authorize.Net SDK calls in `AuthorizeNetClient`.
- Add more unit and integration tests for higher coverage.
- Harden security: replace dev token flow with proper auth in production.

## Swagger UI

After starting the app (mvn spring-boot:run) open the interactive API docs at:

- http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Authorize the UI

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

# CI / Coverage

[![CI](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml/badge.svg)](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml)
[![Codecov](https://img.shields.io/codecov/c/github/lenovo/Payment-processing-System.svg)](https://codecov.io/gh/lenovo/Payment-processing-System)

To publish coverage to Codecov from CI, add a repository secret `CODECOV_TOKEN` containing your Codecov upload token.
