# Payment Processing System (Spring Boot)

A robust Spring Boot backend that integrates with Authorize.Net sandbox for payment processing with enterprise-grade features including state machine, idempotency, audit logging, and resilience patterns.

## Key Features

- **Payment Flows**: Purchase, Authorize, Capture, Cancel/Void, Refund (full & partial)
- **Payment State Machine**: Explicit state transitions with integrity enforcement
- **Idempotency**: Prevent duplicate payment processing via idempotency keys
- **Audit Logging**: Comprehensive tracking of all payment operations
- **Structured Error Handling**: Clear error codes, retry guidance, provider details
- **JWT Authentication**: Secure API access with token-based auth

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
| `authnet.api.login.id` | Authorize.Net API Login ID (sandbox) | - |
| `authnet.transaction.key` | Authorize.Net Transaction Key (sandbox) | - |
| `jwt.secret` | Secret for signing JWTs | `my-super-secret-key-for-dev-32bytes` |
| `developer.key` | Dev key for `/auth/token` endpoint | `dev-local-key` |

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
| [Architecture.md](Architecture.md) | API flows, state machine, DB schema |
| [API-SPECIFICATION.yml](API-SPECIFICATION.yml) | OpenAPI specification |
| [COMPLIANCE.md](COMPLIANCE.md) | PCI DSS awareness, security guidance |
| [TESTING_STRATEGY.md](TESTING_STRATEGY.md) | Test coverage strategy |
| [TEST_REPORT.md](TEST_REPORT.md) | Coverage report |
| [IMPROVEMENT_PLAN.md](IMPROVEMENT_PLAN.md) | Improvement roadmap |
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

# CI / Coverage

[![CI](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml/badge.svg)](https://github.com/lenovo/Payment-processing-System/actions/workflows/ci.yml)
[![Codecov](https://img.shields.io/codecov/c/github/lenovo/Payment-processing-System.svg)](https://codecov.io/gh/lenovo/Payment-processing-System)

To publish coverage to Codecov from CI, add a repository secret `CODECOV_TOKEN` containing your Codecov upload token.
