# Compliance & Security Guide — Payment Processing System

This document outlines security considerations, PCI DSS awareness, and compliance guidance for the Payment Processing System.

---

## PCI DSS Compliance Awareness

### What is PCI DSS?

The Payment Card Industry Data Security Standard (PCI DSS) is a set of security requirements designed to ensure that all companies that process, store, or transmit credit card information maintain a secure environment.

### Our Compliance Posture

This application is designed with PCI DSS best practices in mind:

| Requirement | Status | Notes |
|------------|--------|-------|
| **Do not store CVV** | ✅ Compliant | CVV is only used in transit, never persisted |
| **Mask card numbers in logs** | ✅ Implemented | `sanitizeRawResponse()` masks card data; `Card.toString()` masks PAN |
| **Encrypt data in transit** | ⚠️ Application-level | TLS config scaffolded in `application-prod.properties`; terminate at reverse proxy in production |
| **Strong access controls** | ✅ JWT authentication | Role-based access recommended for production |
| **Audit logging** | ✅ Implemented | AuditService tracks all payment operations |
| **Tokenization** | ⚠️ Recommended | Consider Authorize.Net CIM for recurring |
| **Card data in stack traces** | ✅ Mitigated | `Card.toString()` returns masked representation; validation errors sanitized |

### ⚠️ PCI DSS Scope Reality

**Current architecture: SAQ-D scope.** Card data (PAN, CVV) flows through the application server memory on its way to the Authorize.Net SDK. This means:

- The server environment must be PCI DSS compliant (network segmentation, vulnerability scanning, access controls, etc.)
- SAQ-D is the most comprehensive (and expensive) self-assessment questionnaire

**To reduce PCI scope (recommended for production):**
- **Option A: Authorize.Net Accept.js** — Tokenizes card data in the browser. Your server only receives an opaque payment nonce, never raw card data. Reduces scope to **SAQ-A-EP**.
- **Option B: Authorize.Net Hosted Payment Page** — Redirects the user to Authorize.Net's hosted form. Reduces scope to **SAQ-A**.
- **Option C: Authorize.Net Customer Information Manager (CIM)** — Store card profiles at the gateway for recurring billing instead of passing card data each time.

Migrating to Accept.js would require frontend changes and replacing the card fields in the API with a `paymentNonce` field, but the backend architecture (state machine, idempotency, audit, etc.) would remain unchanged.

### Sensitive Data Handling

#### What We Do NOT Store
- Full credit card numbers (only last 4 for refunds)
- CVV/CVC codes
- Magnetic stripe data
- PIN numbers

#### What We Store (Masked)
- Last 4 digits of card (for refund operations)
- Transaction IDs (gateway references)
- Sanitized gateway responses (card numbers masked)

### Data Masking Implementation

The `sanitizeRawResponse()` method in PaymentService masks sensitive data:

```java
private String sanitizeRawResponse(Object raw) {
    if (raw == null) return null;
    String str = raw.toString();
    // Mask card numbers (13-16 digits)
    str = str.replaceAll("\\b\\d{13,16}\\b", "****");
    // Mask CVV
    str = str.replaceAll("(?i)(cardCode|cvv)[\":\\s]+\\d{3,4}", "$1\":\"***\"");
    return str;
}
```

---

## Secrets Management

### Current Implementation (Development)

For development convenience, secrets can be configured via:
- `application.properties` (NOT recommended for production)
- Environment variables (recommended)

```properties
# NEVER commit real credentials to source control
authnet.api.login.id=${AUTHNET_API_LOGIN_ID:}
authnet.transaction.key=${AUTHNET_TRANSACTION_KEY:}
jwt.secret=${JWT_SECRET:change-me-please}
```

### Production Recommendations

#### Option 1: HashiCorp Vault
```yaml
# docker-compose.yml addition
vault:
  image: vault:1.13
  cap_add:
    - IPC_LOCK
  environment:
    VAULT_DEV_ROOT_TOKEN_ID: root
  ports:
    - "8200:8200"
```

Spring Boot Vault integration:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

#### Vault Verification Runbook

To verify the Vault integration end-to-end:

```bash
# 1. Start Vault (dev mode)
docker-compose --profile vault up -d vault

# 2. Set Vault environment
export VAULT_ADDR="http://localhost:8200"
export VAULT_TOKEN="root"

# 3. Seed secrets into Vault
docker exec payment-vault vault kv put secret/payment-processing-system \
    authnet.api.login.id="YOUR_SANDBOX_LOGIN_ID" \
    authnet.transaction.key="YOUR_SANDBOX_TX_KEY" \
    authnet.webhook.signature-key="YOUR_WEBHOOK_KEY" \
    jwt.secret="$(openssl rand -base64 32)" \
    spring.datasource.password="postgres"

# 4. Verify secrets are stored
docker exec payment-vault vault kv get secret/payment-processing-system

# 5. Start the application with Vault profile
SPRING_PROFILES_ACTIVE=prod,vault VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=root mvn spring-boot:run

# 6. Verify the app fetched secrets (check logs for "Located property source: vault")
# 7. Test a payment endpoint to confirm Authorize.Net credentials work
```

> **Note:** The `vault` profile uses `application-vault.properties` which configures
> `spring.config.import=vault://`. In production, use AppRole authentication instead of
> root token, and configure proper ACL policies.

#### Option 2: AWS Secrets Manager
```java
@Configuration
public class SecretsConfig {
    @Value("${aws.secretsmanager.secret-name}")
    private String secretName;
    
    // Fetch secrets on startup
}
```

#### Option 3: Kubernetes Secrets
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: payment-secrets
type: Opaque
data:
  authnet-api-login-id: <base64-encoded>
  authnet-transaction-key: <base64-encoded>
  jwt-secret: <base64-encoded>
```

---

## Authentication & Authorization

### Current Implementation

- JWT-based authentication for API access
- Developer key exchange for token generation
- Token expiration: 1 hour

### ⚠️ Current Auth Limitations (Development Only)

The `/auth/token` endpoint exchanges a static `developer_key` for a JWT. This is a **development-only convenience** and is **not suitable for production** because:
- The developer key is a shared secret, not tied to individual users
- No user identity, roles, or permissions in the JWT claims
- No token refresh or revocation mechanism
- No multi-factor authentication support

### Production Auth Upgrade Path

For production, replace the developer key exchange with a proper identity provider:

1. **OAuth2 / OpenID Connect** (recommended) — Use Keycloak, Auth0, Okta, or AWS Cognito as the identity provider. Configure Spring Security OAuth2 Resource Server. Tokens would contain user identity and roles (e.g., `PAYMENT_ADMIN`, `PAYMENT_READER`). Supports token refresh, revocation, and SSO.

2. **Remove `/auth/token` endpoint** — In production, token issuance should be handled exclusively by the identity provider, not by the payment service itself.

### Production Recommendations

1. **Use strong JWT secrets** (256-bit minimum)
   ```properties
   jwt.secret=${JWT_SECRET}  # At least 32 characters, random
   ```

2. **Implement role-based access control (RBAC)**
   ```java
   @PreAuthorize("hasRole('PAYMENT_ADMIN')")
   public void refund(...) { }
   ```

3. **Add IP whitelisting for admin operations**
   ```java
   @Configuration
   public class SecurityConfig {
       // Whitelist specific IPs for sensitive operations
   }
   ```

4. **Enable rate limiting**
   ```java
   // ✅ Implemented — all payment and webhook endpoints are rate-limited
   @RateLimiter(name = "paymentApi")  // 100 req/s per instance
   public ResponseEntity<?> purchase(...) { }

   @RateLimiter(name = "webhookApi")  // 200 req/s per instance
   public ResponseEntity<?> handleWebhook(...) { }
   ```

   Rate limiter configuration in `application.properties`:
   ```properties
   resilience4j.ratelimiter.instances.paymentApi.limitForPeriod=100
   resilience4j.ratelimiter.instances.paymentApi.limitRefreshPeriod=1s
   resilience4j.ratelimiter.instances.webhookApi.limitForPeriod=200
   resilience4j.ratelimiter.instances.webhookApi.limitRefreshPeriod=1s
   ```

   When rate limit is exceeded, returns HTTP 429 with `RATE_LIMIT_EXCEEDED` error code and `retry_after` suggestion.

---

## Audit Logging

### What We Log

| Event Type | Details Captured |
|------------|------------------|
| Order Creation | Order ID, amount, currency, actor, IP |
| State Transitions | From/to states, timestamp, actor |
| Gateway Calls | Operation type, sanitized request |
| Gateway Responses | Success/failure, sanitized response |
| Errors | Error code, message, correlation ID |
| Refund Operations | Amount, original transaction, actor |
| Void Operations | Transaction ID, actor, timestamp |

### Log Retention

- Audit logs: 365 days minimum (configurable)
- Payment records: 7 years (regulatory requirement)
- Debug logs: 30 days

### Log Access

Audit logs should be:
- Protected from modification
- Accessible only to authorized personnel
- Backed up regularly
- Monitored for suspicious patterns

---

## Network Security

### Recommendations

1. **TLS/SSL**
   - Enforce TLS 1.2 or higher
   - Use strong cipher suites
   - Enable HSTS

2. **Firewall Rules**
   - Allow only necessary ports (443 for HTTPS)
   - Restrict database access to application servers
   - Use private subnets for internal services

3. **API Gateway**
   - Use an API gateway for rate limiting
   - Implement request validation at the edge
   - Enable DDoS protection

---

## Incident Response

### Payment Fraud Detection

1. **Monitor for anomalies:**
   - Unusual transaction volumes
   - High decline rates
   - Multiple transactions from same IP
   - Transactions from unusual geolocations

2. **Alert triggers:**
   - 5+ declines from same card in 1 hour
   - Transaction amount > $10,000
   - Velocity limit exceeded

3. **Response procedures:**
   - Automatic hold for review (HELD_FOR_REVIEW state)
   - Notify security team
   - Log all details for investigation

### Data Breach Response

1. Immediately isolate affected systems
2. Notify payment processor (Authorize.Net)
3. Preserve audit logs
4. Engage incident response team
5. Notify affected customers (if required by law)
6. Report to relevant authorities

---

## Compliance Checklist

### Before Going to Production

- [ ] Replace development JWT secret with strong random value (256-bit minimum)
- [ ] Configure secrets via HashiCorp Vault (activate `vault` profile: `SPRING_PROFILES_ACTIVE=prod,vault`)
- [ ] Enable TLS/SSL with valid certificate (TLS 1.2+)
- [ ] Review and test audit logging (verify all payment operations logged)
- [ ] Set up log monitoring and alerting (JSON logs to ELK/Loki)
- [ ] Configure rate limiting (Resilience4j — already active: 100 req/s payments, 200 req/s webhooks)
- [ ] Enable IP whitelisting for admin endpoints
- [ ] Remove or secure /auth/token endpoint (use OAuth2/OIDC in production)
- [ ] Review database access controls (use managed PostgreSQL with IAM)
- [ ] Conduct security scan/penetration test
- [ ] Document incident response procedures
- [ ] Train team on PCI DSS requirements
- [ ] Activate production profile for JSON logging and reduced trace sampling
- [ ] Configure Jaeger/OpenTelemetry collector for production trace ingestion

### API Key Rotation Procedure

1. **Authorize.Net API Keys**:
   - Generate new API credentials in Authorize.Net merchant portal
   - Update Vault secret at `secret/payment-processing-system` with new `authnet.api.login.id` and `authnet.transaction.key`
   - Application auto-refreshes from Vault after TTL expiry (or restart)
   - Verify transactions succeed with new credentials before revoking old ones
   - Recommended rotation: **quarterly** or immediately if compromised

2. **JWT Signing Secret**:
   - Generate new 256-bit random secret: `openssl rand -base64 32`
   - Update Vault secret with new `jwt.secret`
   - Note: existing tokens signed with old secret will be invalidated
   - Recommended rotation: **quarterly**

3. **Webhook Signature Key**:
   - Generate new key in Authorize.Net merchant portal → Webhooks settings
   - Update Vault secret with new `authnet.webhook.signature-key`
   - Update occurs immediately (no downtime needed)

4. **Database Credentials**:
   - Use Vault dynamic secrets with PostgreSQL backend for automatic rotation
   - Configure lease TTL (e.g., 24h) and max TTL (e.g., 72h)
   - HikariCP handles connection refresh transparently

### Regular Maintenance

- [ ] Rotate API credentials quarterly (or immediately if compromised)
- [ ] Review audit logs monthly (check for anomalies, unauthorized access)
- [ ] Update dependencies for security patches (monthly dependency scan)
- [ ] Conduct annual security review and penetration test
- [ ] Test backup and recovery procedures quarterly
- [ ] Review Vault access policies and audit logs
- [ ] Verify webhook signature validation is working correctly

---

## References

- [PCI DSS Quick Reference Guide](https://www.pcisecuritystandards.org/documents/PCI_DSS_QRG_v3-2-1.pdf)
- [Authorize.Net Security Best Practices](https://developer.authorize.net/api/reference/features/security.html)
- [OWASP Payment Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Payment_Gateway_Cheat_Sheet.html)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)

---

*This document should be reviewed and updated regularly to reflect changes in compliance requirements and security best practices.*

