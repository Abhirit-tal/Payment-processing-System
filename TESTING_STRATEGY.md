# Testing Strategy — Payment Processing System

This document describes the testing approach, scope, and concrete test cases to validate the payment backend that integrates with Authorize.Net sandbox. It is written to help developers prepare, run, and extend tests in a consistent, reliable way.

Last Updated: 2026-02-24

---

## Quick Status ✅

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| Test Coverage | ≥80% | **87%+** | ✅ Achieved |
| Unit Tests | Comprehensive | 397 tests | ✅ |
| Test Suites | All layers | 30+ suites | ✅ |
| All Tests Passing | Yes | Yes | ✅ |

---

## Quick checklist
- [x] Unit tests for all service and validation logic (mock external SDK)
- [x] Controller tests to validate request validation, auth, and response shapes
- [x] Integration tests for optional Authorize.Net sandbox interactions (guarded by env vars)
- [x] Model and DTO tests for all entities and data transfer objects
- [x] Exception tests for all custom exception classes
- [x] Config tests for JWT filter, properties, OpenAPI configuration, and CorrelationIdFilter
- [x] Smoke tests running in Docker Compose to assert basic app health
- [x] Webhook controller tests (signature validation, idempotency, payload processing)
- [x] Subscription controller tests (CRUD lifecycle)
- [x] Subscription service tests (create, update, cancel, gateway failures)
- [x] Webhook service tests (signature, dedup, process, status updates)
- [x] Event queue tests (publish, listener dispatch, error isolation)
- [x] Load testing instructions (see section 9 below)

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

## 8) Advanced Payment Scenarios Tested

### Webhook Tests (`WebhookControllerTest`, `WebhookServiceTest`)
- Invalid HMAC signature → 401 Unauthorized
- Valid signature → 200 OK with event persisted
- Duplicate notification ID → 200 OK without re-processing (idempotent)
- Payload without notificationId → auto-generated ID, still processed
- Webhook status lifecycle: received → processed / failed

### Subscription Lifecycle Tests (`SubscriptionControllerTest`, `SubscriptionServiceTest`)
- Create subscription → 201 with gateway subscription ID
- Create subscription with gateway failure → 502
- Get subscription by ID → 200 / 404
- Update subscription (name/amount) → 200 / 404 / gateway failure
- Cancel subscription → 200 with cancelled status / 404

### Resilience & Error Scenarios
- `AuthorizeNetClient` with no credentials → graceful failure or TransientPaymentException
- Circuit breaker fallback methods return structured error responses
- @Retryable annotated methods (verified via unit test + configuration)
- Event queue listener exception isolation (one failed listener doesn't stop others)

### Idempotency Tests
- PaymentController reads `Idempotency-Key` header
- Cached response returned for duplicate key + same body
- IdempotencyService lock/release lifecycle

### Idempotency Service Tests (`IdempotencyServiceTest`)
- `checkIdempotency` returns empty for new key
- `checkIdempotency` returns cached response for completed key with matching hash
- `checkIdempotency` handles locked-but-not-completed keys
- `createAndLock` creates key with lock timestamp and request hash
- `complete` sets response body, status, order ID, and completed timestamp
- `release` deletes the key from DB (allowing retry)
- `CachedResponse` record holds body and status code

### Pending Transaction Retry Tests (`PendingTransactionRetryServiceTest`)
- No stale PENDING orders → no retry attempted
- Stale PENDING order → increments retry count, logs RETRY audit
- Order exceeding max retries (3) → marked as ERROR with MAX_RETRIES_EXCEEDED audit
- Order with no transactions → increments retry count, does not crash
- Order with no provider_tx_id → marked as ERROR (can't query gateway without card data)
- ERROR orders under max retries → retried
- ERROR orders at max retries → skipped
- No ERROR orders → no action

### Custom Metrics Tests (`MetricsConfigTest`)
- Purchase success/failed events increment `payment_events_total` counter
- Webhook received events increment `webhook_events_total` counter
- Subscription created events increment `subscription_events_total` counter
- Queue size gauge registered and returns 0 when empty
- All event types handled without exceptions (authorize, capture, void, refund, subscription cancelled)

### Gateway Failure & Error Scenarios (`PaymentServiceGatewayFailureTest`)
- Card declined (response_code=2) → order in DECLINED state, failed transaction
- Insufficient funds → failed transaction with proper status
- Gateway error (response_code=3) → ERROR state order
- Held for review (response_code=4) → HELD_FOR_REVIEW state
- TransientPaymentException thrown on gateway timeout → propagated to caller
- Communication failure → TransientPaymentException
- Circuit breaker fallback → structured error with "gateway unavailable" message
- Capture of non-existent transaction → empty Optional
- Capture gateway failure → order stays AUTHORIZED (no state regression)
- Refund of non-existent transaction → empty Optional
- Refund gateway failure → order stays CAPTURED
- Null provider_tx_id in gateway response → transaction still created
- Null response_code with failed status → handled gracefully

### Billing Cycle Tests (`BillingCycleServiceTest`)
- No due subscriptions → no gateway calls
- Due subscription with gateway success → billing cycle advanced, totalBilled incremented
- Due subscription with gateway failure → failure count incremented
- Max billing failures exceeded → subscription suspended
- Subscription without gateway ID → billing cycle advanced locally
- Gateway exception during billing → failure count incremented
- Billing date initialization for subscriptions without nextBillingDate
- Skip initialization for subscriptions with existing billing date
- Monthly interval calculation
- Daily interval calculation
- Null fromDate defaults to today

### Webhook Retry Tests (`WebhookRetryServiceTest`)
- No failed events → no retries
- Failed event retried successfully → status set to "processed"
- Retry throws exception → retry count incremented, error message set
- Exponential backoff → nextRetryAt calculated correctly
- Multiple failed events processed in sequence

### Correlation ID Tests (`CorrelationIdFilterTest`)
- Auto-generates UUID when no X-Correlation-ID header provided
- Echoes provided correlation ID in response
- Blank/empty header → generates new ID
- Filter chain is properly invoked

### Event Queue Tests (`PaymentEventQueueTest`)
- Publish returns true, listener receives event
- Multiple listeners all receive same event
- Listener exceptions don't stop queue consumer
- PaymentEvent record creation with/without payload

## 9) Load Testing Instructions

For load testing the payment system, use Apache JMeter or k6:

### k6 Example
```javascript
import http from 'k6/http';
import { check } from 'k6';

export let options = {
  vus: 50,
  duration: '60s',
};

export default function () {
  // Get token
  let tokenRes = http.post('http://localhost:8080/auth/token',
    JSON.stringify({ developer_key: 'dev-local-key' }),
    { headers: { 'Content-Type': 'application/json' } });
  let token = tokenRes.json('access_token');

  // Purchase
  let res = http.post('http://localhost:8080/payments/purchase',
    JSON.stringify({
      amount: 10.00, currency: 'USD',
      card: { number: '4111111111111111', expMonth: 12, expYear: 2030, cvv: '123' }
    }),
    { headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
        'Idempotency-Key': `load-test-${__VU}-${__ITER}`
    }});
  check(res, { 'status 201': (r) => r.status === 201 });
}
```

Run: `k6 run load-test.js`

## 10) Future improvements

- [ ] Add testcontainers for PostgreSQL integration tests
- [ ] Add contract tests (Pact) for API consumers
- [ ] Add property-based testing for validation logic
- [ ] Add mutation testing to verify test quality
- [ ] Add performance/load tests with k6 in CI pipeline
- [ ] Add webhook replay testing (re-process failed webhooks)

---

Generated: 2026-02-24
