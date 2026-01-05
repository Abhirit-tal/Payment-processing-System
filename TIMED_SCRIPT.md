TIMED SCRIPT — 6:00 (approx) video

Total target: 6 minutes (360 seconds). Read steadily at a calm pace. Use slide deck `VIDEO_SLIDES.md` and switch scenes as indicated.

0:00–0:20 — Intro (Slide 1)
"Hi, I’m [Your Name]. In the next six minutes I’ll walk through the Payment Processing System — its code and architecture, the development journey including the assistant conversation, key design trade-offs, a short live demo, and the current test coverage. Let’s jump in."

0:20–1:20 — Architecture & flows (Slide 2)
"At a high level, this is a small REST API protected by JWT. It supports core payment flows: Purchase, which performs authorize and capture in one step; Authorize-only followed by Capture; Cancel, which voids an authorization before capture; and Refunds, both full and partial. Orders and transactions are persisted in the database—an Order can have many Transactions. The service communicates with Authorize.Net via a thin SDK wrapper so we can isolate provider logic and write tests. You can find the API spec in `API-SPECIFICATION.yml` in the repo."

1:20–2:30 — Code walkthrough (Slide 3)
"Quick tour of the code: `PaymentController` exposes the endpoints for each flow. `PaymentService` contains the business logic; it creates Orders and Transactions, calls the `AuthorizeNetClient`, and persists results. The `AuthorizeNetClient` is a thin wrapper over the official Authorize.Net Java SDK — it builds requests, initializes merchant auth from `application.properties` or environment variables and returns normalized maps with `status`, `provider_tx_id` and `raw` provider response. For authentication we use a simple `JwtTokenProvider` and a security filter in `SecurityConfig` that accepts a developer JWT issued by `/auth/token`. Validation includes a custom Luhn validator and an expiry/CVV validator to enforce realistic card rules. The file locations are explained in `PROJECT_STRUCTURE.md`."

2:30–4:00 — Live demo (Slide 7)
"I’ll demo a purchase flow. I already started the app locally using Maven (or Docker Compose). First, obtain a dev token from `/auth/token` by posting the developer_key `dev-local-key`. Then call `/payments/purchase` with card details — I’ll paste the request now. You’ll see the JSON response containing the order id, provider transaction id and status. Next I open the H2 console to show the `orders` and `transactions` rows created by that request. If you don’t have Authorize.Net sandbox credentials, this demo shows a mocked or recorded response instead — the smoke tests that exercise the real SDK are gated and will fail if credentials are not provided."

Demo commands to read while showing terminal (Slide 7):
- `mvn spring-boot:run` (or `docker compose up --build`)
- `curl -s -X POST http://localhost:8080/auth/token -H "Content-Type: application/json" -d '{"developer_key":"dev-local-key"}' | jq`
- Use the JWT returned, then:
- `curl -s -X POST http://localhost:8080/payments/purchase -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" -d '{"amount":1.00,"currency":"USD","card":{"number":"4111111111111111","expMonth":12,"expYear":2030,"cvv":"123"},"orderId":"demo-1"}' | jq`

4:00–4:40 — Tests & coverage (Slide 8)
"Next, tests and coverage. I run `mvn test` and generate JaCoCo reports with `mvn jacoco:report`. The project’s current overall line coverage is around 55%. The validation and DTOs are well covered; the `PaymentService` has good coverage for the main happy paths. `AuthorizeNetClient` is a hotspot with low coverage because it wraps an external SDK and is exercised only by smoke/integration tests. These smoke tests currently produce errors when sandbox credentials aren’t supplied — they should be gated by a Maven profile or environment variables. See `TEST_REPORT.md` for details and recommendations."

4:40–5:20 — Key decisions & trade-offs (Slide 5)
"A few quick design decisions: using the official Authorize.Net SDK ensures feature parity but necessitates careful testing; wrapping it in `AuthorizeNetClient` isolates provider logic and makes unit testing easier. We used H2 for rapid local development but recommend migrating to Postgres with Flyway for production. For JWT, the code generates a random signing key if none is configured for convenience — that's only for development and should be replaced in production."

5:20–5:40 — Assistant conversation & process (Slide 6)
"We iterated with an AI coding assistant: validators and DTOs were added, controller and service code evolved, tests and JaCoCo coverage were added, and documentation files like `Architecture.md` and `PROJECT_STRUCTURE.md` were generated. The entire conversation is recorded in `chat.md` for transparency and onboarding."

5:40–6:00 — Wrap-up & next steps (Slide 9 & 10)
"To continue: gate integration tests with a Maven profile, add DB migrations, and write tests to improve branch coverage, especially for error paths and JWT filter behavior. All relevant artifacts are in the repo. Thanks for watching — reach out if you’d like the Postman collection or a recorded screencast of the demo."


Recording cues & tips:
- Keep each slide visible during its section; switch to the terminal for demo commands.
- Pause briefly between major sections and breathe.
- If a live command takes long, either pre-run and show output or edit out the wait in post-production.


