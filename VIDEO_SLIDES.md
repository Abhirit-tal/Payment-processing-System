---
title: Payment Processing System — 6 min walkthrough
subtitle: Code, design, demo, decisions, and test coverage
author: (Your Name)
---

# Slide 1 — Quick intro (20s)

- Title: Payment Processing System — 6 minute walkthrough
- One-sentence goal: explain architecture, code, demo, decisions, and tests

Notes:
- Show your name and repo link (or open IDE window briefly)
- Visual: small webcam inset (optional)

---

# Slide 2 — High-level architecture & flows (60s)

- Bullet: REST API protected by JWT
- Bullet: Core flows: Purchase (auth+capture), Authorize → Capture, Cancel (void), Refund (full/partial)
- Bullet: Persistence: Order and Transaction entities (H2 for dev)
- Visual: simple sequence diagram (Client → Controller → PaymentService → AuthorizeNetClient → Authorize.Net)

Speaker notes:
- Emphasize thin wrapper `AuthorizeNetClient` for SDK isolation and testability
- Mention `API-SPECIFICATION.yml` available in repo

---

# Slide 3 — Key components in code (60s)

- PaymentController: endpoints for each flow
- PaymentService: business logic + DB persistence
- AuthorizeNetClient: SDK wrapper (create, capture, void, refund)
- JwtTokenProvider + SecurityConfig: dev token & JWT validation
- Validation: Custom Luhn & expiry/CVV rules

Visual: show a quick file tree or IDE with highlighted files

Notes:
- Point to `PROJECT_STRUCTURE.md` for file locations

---

# Slide 4 — DB schema & entities (30s)

- `orders` table: id, external_id, amount, currency, status, timestamps
- `transactions` table: id, order_id, type, provider_tx_id, amount, status, raw_response
- Relationship: Order 1:N Transaction

Visual: ER diagram or schema snippet

Notes:
- Mention `spring.jpa.hibernate.ddl-auto=update` used for dev; recommend Flyway for prod

---

# Slide 5 — Design decisions & trade-offs (40s)

- Official SDK vs direct HTTP: used official Authorize.Net SDK for reliability and feature parity
- Wrapper pattern: isolates SDK, eases testing — but needs integration tests
- H2 DB for dev: fast and zero-config but switch to Postgres in prod
- JWT: development convenience with random key fallback — not for production

Notes:
- Mention where trade-offs impacted testing and CI

---

# Slide 6 — Conversation with coding assistant (30s)

- Short bullets:
  - Iterative development: validators, controllers, service, SDK wiring
  - Added unit tests, JaCoCo, integration gating
  - Created docs: Architecture.md, PROJECT_STRUCTURE.md, API-SPECIFICATION.yml, TEST_REPORT.md

Visual: open `chat.md` and highlight a few key assistant actions

Notes:
- Say: this guided the implementation and tests; useful for onboarding

---

# Slide 7 — Live demo (90s) — prep & commands

- Prep: app running (mvn spring-boot:run) or Docker compose
- Commands to show (in separate terminal / lightweight):

PowerShell examples (show on slide):

```powershell
# Get dev token
curl -s -X POST http://localhost:8080/auth/token -H "Content-Type: application/json" -d '{"developer_key":"dev-local-key"}' | jq

# Purchase (replace <JWT>)
$token = '<JWT>'
curl -s -X POST http://localhost:8080/payments/purchase -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d '{"amount":1.00,"currency":"USD","card":{"number":"4111111111111111","expMonth":12,"expYear":2030,"cvv":"123"},"orderId":"demo-1"}' | jq
```

Notes:
- Show success response and then open H2 console or query DB to show Order/Transaction
- If sandbox creds not available, show mocked response or explain gating

---

# Slide 8 — Tests & coverage (40s)

- Show `mvn test` quick result or pre-run test summary
- Open `target/site/jacoco/index.html` and highlight:
  - Overall lines coverage ~55%
  - Hotspot: `AuthorizeNetClient` low coverage (integration needed)

Visual: screenshot of JaCoCo top-level and the `AuthorizeNetClient` file

Notes:
- Refer to `TEST_REPORT.md` for actionable items and gating integration tests

---

# Slide 9 — Next steps & roadmap (25s)

- Add DB migrations (Flyway)
- Gate integration tests behind Maven profile and CI secrets
- Increase branch coverage by testing error branches and JwtFilter
- Deploy sample to staging with sandbox credentials for end-to-end tests

---

# Slide 10 — Wrap-up & links (15s)

- Repo files to reference: `Architecture.md`, `PROJECT_STRUCTURE.md`, `API-SPECIFICATION.yml`, `TEST_REPORT.md`
- Contact & credits

Notes:
- Offer to provide Postman collection or a short recorded screencast of the demo on request

