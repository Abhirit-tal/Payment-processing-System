# Testing Strategy — Payment Processing System

This document describes the testing approach, scope, and concrete test cases to validate the payment backend that integrates with Authorize.Net sandbox. It is written to help developers prepare, run, and extend tests in a consistent, reliable way.

Last Updated: 2026-02-24

---

## Quick Status ✅

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Test Coverage | ≥80% | **87%** | ✅ Achieved |
| Unit Tests | Comprehensive | 180+ tests | ✅ |
| Test Suites | All layers | 26 suites | ✅ |
| All Tests Passing | Yes | Yes | ✅ |

---

## Quick checklist
- [x] Unit tests for all service and validation logic (mock external SDK) — **87% coverage achieved**
- [x] Controller tests to validate request validation, auth, and response shapes
- [x] Integration tests for optional Authorize.Net sandbox interactions (guarded by env vars)
- [x] Model and DTO tests for all entities and data transfer objects
- [x] Exception tests for all custom exception classes
- [x] Config tests for JWT filter, properties, and OpenAPI configuration
- [x] Smoke tests running in Docker Compose to assert basic app health
- [ ] Add CI job(s) to run unit tests and (optionally) integration tests when credentials are present

---

## 1) Test types and purpose

### Unit tests
- Fast, deterministic, should run offline without credentials.
- Mock `AuthorizeNetClient` and repositories to assert business logic in `PaymentService`.
- Validate validators (`CardNumberValidator`, `CardExpiryValidator`) and DTO validation behavior.
- **Coverage: 87% overall**

### Controller (slice) tests
- Use Spring's `@WebMvcTest` or `@SpringBootTest` with mocked beans to assert request/response mapping and HTTP status codes.
- Include validation error cases (missing/invalid fields). Test JWT guard behavior.
- Test `GlobalExceptionHandler` for all exception types.

### Model and Entity tests
- Test all entity classes (`Order`, `Transaction`, `AuditLog`, `IdempotencyKey`)
- Test state enums (`PaymentState`, `GatewayResponseType`) with all methods
- Test builder patterns and helper methods

### DTO tests
- Test all request/response DTOs
- Test error code enums with category logic
- Test builder patterns for error responses

### Exception tests
- Test all custom exception classes
- Test exception hierarchies and inheritance
- Test error codes and retryability flags

### Integration tests (sandbox) — optional / gated
- Tests that call `AuthorizeNetClient` against a real Authorize.Net sandbox account.
- Gated by environment variables and not run by default in CI.

### Smoke tests (Docker)
- Run the app under Docker Compose and run HTTP checks.

---

## 2) Tooling & configuration

- **Test framework**: JUnit 5 (with nested test classes)
- **Mocking**: Mockito
- **Spring test utilities**: spring-boot-starter-test
- **Coverage**: JaCoCo (report generation via Maven plugin)
- **Validation testing**: jakarta.validation with ValidatorFactory
- **Integration gating**: Maven profile `integration` or environment variables

### Commands
```bash
# Run unit tests
mvn test

# Generate coverage report
mvn jacoco:report
# Open target/site/jacoco/index.html

# Run integration tests with profile
$env:AUTHNET_API_LOGIN_ID = "your_login"
$env:AUTHNET_TRANSACTION_KEY = "your_key"
mvn -P integration verify
```

---

## 3) Test organization

### Test class naming convention
- `*Test.java` - Standard unit tests
- `*ExtendedTest.java` - Additional edge case tests
- `*IntegrationIT.java` - Integration tests (gated)

### Test class structure (using nested classes)
```java
public class PaymentServiceTest {
    @Nested
    class PurchaseTests { ... }
    
    @Nested
    class CaptureTests { ... }
    
    @Nested
    class RefundTests { ... }
}
```

---

## 4) Coverage by layer

### Model Layer (100% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| PaymentState | 100% | PaymentStateTest.java |
| GatewayResponseType | 100% | GatewayResponseTypeTest.java |
| Order | 100% | OrderTest.java |
| Transaction | 100% | TransactionTest.java |
| AuditLog | 100% | AuditLogTest.java |
| IdempotencyKey | 100% | IdempotencyKeyTest.java |

### Exception Layer (100% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| All exceptions | 100% | ExceptionTest.java |

### DTO Layer (99% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| PaymentRequests | 99% | PaymentRequestsDtoTest.java |
| PaymentErrorCode | 100% | PaymentErrorCodeTest.java |
| PaymentErrorResponse | 100% | PaymentErrorResponseTest.java |

### Controller Layer (96% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| PaymentController | 96% | PaymentControllerTest.java, PaymentControllerExtendedTest.java |
| GlobalExceptionHandler | 96% | GlobalExceptionHandlerTest.java |
| AuthController | 95% | AuthControllerTest.java, AuthControllerSuccessTest.java |

### Service Layer (77% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| PaymentService | 80% | PaymentServiceTest.java, PaymentServiceExtendedTest.java |
| PaymentStateMachine | 95% | PaymentStateMachineTest.java, PaymentStateMachineExtendedTest.java |
| AuditService | 70% | AuditServiceTest.java |
| AuthorizeNetClient | 60% | AuthorizeNetClientSmokeTest.java |

### Config Layer (63% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| JwtFilter | 90% | JwtFilterTest.java |
| AppProperties | 100% | AppPropertiesTest.java |
| OpenApiConfig | 100% | OpenApiConfigTest.java |
| SecurityConfig | 30% | (Spring config - partial) |

### Validation Layer (95% coverage)
| Class | Coverage | Test File |
|-------|----------|-----------|
| CardNumberValidator | 95% | CardNumberValidatorTest.java |
| CardExpiryValidator | 95% | CardBrandCvvValidationTest.java |

---

## 5) Key test scenarios

### PaymentService tests
- ✅ Purchase success creates order and transaction
- ✅ Authorize-only sets status to authorized
- ✅ Capture with valid auth transaction
- ✅ Capture with missing transaction returns empty
- ✅ Capture with invalid state throws exception
- ✅ Void transaction success
- ✅ Void with invalid state throws exception
- ✅ Full refund updates to REFUNDED
- ✅ Partial refund updates to PARTIALLY_REFUNDED
- ✅ Refund with invalid state throws exception

### PaymentStateMachine tests
- ✅ All valid state transitions
- ✅ All invalid state transitions rejected
- ✅ Terminal states have no transitions
- ✅ Null state handling
- ✅ Error message generation
- ✅ Transaction type to state mapping

### Controller tests
- ✅ All endpoints (purchase, authorize, capture, cancel, refund, health)
- ✅ Validation errors return 400
- ✅ Not found returns 404
- ✅ Provider errors return 502

### Exception handler tests
- ✅ Validation exceptions → 400
- ✅ State transition exceptions → 409
- ✅ Gateway declined → 400
- ✅ Gateway timeout → 504
- ✅ Transient errors → 502
- ✅ Generic exceptions → 500

---

## 6) Running tests

### Unit tests (default)
```bash
mvn test
```

### With coverage report
```bash
mvn test jacoco:report
# Open target/site/jacoco/index.html
```

### Integration tests (requires credentials)
```powershell
$env:AUTHNET_API_LOGIN_ID = "your_login"
$env:AUTHNET_TRANSACTION_KEY = "your_key"
$env:AUTHNET_ENV = "sandbox"
mvn -P integration verify
```

### Docker smoke tests
```bash
docker compose up --build
# In another shell: run curl checks
```

---

## 7) CI/CD recommendations

### Unit test job (required)
```yaml
- name: Run unit tests
  run: mvn test jacoco:report
  
- name: Check coverage threshold
  run: |
    # Parse JaCoCo report and verify >= 80% coverage
```

### Integration test job (optional, when credentials available)
```yaml
- name: Run integration tests
  if: env.AUTHNET_API_LOGIN_ID != ''
  run: mvn -P integration verify
  env:
    AUTHNET_API_LOGIN_ID: ${{ secrets.AUTHNET_API_LOGIN_ID }}
    AUTHNET_TRANSACTION_KEY: ${{ secrets.AUTHNET_TRANSACTION_KEY }}
```

---

## 8) Future improvements

- [ ] Add testcontainers for PostgreSQL integration tests
- [ ] Add contract tests (Pact) for API consumers
- [ ] Add property-based testing for validation logic
- [ ] Add mutation testing to verify test quality
- [ ] Add performance/load tests

---

Generated: 2026-02-24
