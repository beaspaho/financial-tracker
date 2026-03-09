# Financial Tracker

A personal finance tracking REST API built with Spring Boot 3.4.3 and Java 21. Supports account management, deposits, withdrawals, transfers, and transaction history with idempotent writes and role-based access control.

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Running Locally](#running-locally)
- [Running with Docker](#running-with-docker)
- [Design Decisions](#design-decisions)
- [API Reference](#api-reference)
- [Known Limitations & Future Improvements](#known-limitations--future-improvements)

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | Bundled via `./mvnw` |
| Docker + Docker Compose | For containerized setup |

**External services (required for local run):**

| Service | Default | Purpose |
|---------|---------|---------|
| PostgreSQL | `localhost:5432`, db `financedb` | Primary datastore |
| Keycloak | `localhost:8180`, realm `finance` | JWT issuer / Identity provider |

---

## Running Locally

### 1. Start external services

The easiest way is to use the provided Docker Compose file which starts PostgreSQL, Keycloak, and Redis:

```bash
cd .devcontainers
docker compose up -d postgres keycloak 
```

Wait for Keycloak to finish importing the realm (this can take up to 2 minutes). Check readiness:

```bash
docker compose logs -f keycloak
# Ready when you see: "Listening on: http://0.0.0.0:8080"
```

### 2. Configure environment (optional)

All settings have sensible defaults for local development. Override via environment variables if needed:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/financedb
export DB_USERNAME=app
export DB_PASSWORD=secret
export KEYCLOAK_SERVER_URL=http://localhost:8180
export KEYCLOAK_REALM=finance
export KEYCLOAK_ADMIN_CLIENT_ID=finance-admin
export KEYCLOAK_ADMIN_CLIENT_SECRET=change-me-in-production
export KEYCLOAK_APP_CLIENT_ID=finance-app
export KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/finance
```

### 3. Build and run

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run
./mvnw spring-boot:run
```

The application starts on `http://localhost:8080`.

### Running tests

```bash
# All tests
./mvnw test

# Single test class
./mvnw test -Dtest=TransactionServiceTest

# Single test method
./mvnw test -Dtest=TransactionServiceTest#deposit_success
```

---

## Running with Docker

The Docker Compose setup in `.devcontainers/` starts the full stack (PostgreSQL, Keycloak, and the Spring Boot app):

```bash
cd .devcontainers
docker compose up --build
```

The app will be available at `http://localhost:8080` once the health check passes. Keycloak admin console is at `http://localhost:8180` (admin / admin).

---

## Design Decisions

### Hexagonal Architecture

The application is split into three independent domain modules — `identity`, `account`, and `transaction` — each following the same internal layering:

```
ports/         → REST controllers (inbound adapters)
service/       → Business / application logic
repository/    → JPA repositories
domain/
  models/      → Pure domain objects (no JPA annotations)
  entity/      → JPA entities
  transformers/ → Mappers between domain ↔ entity
```

**Module decoupling via ports:** The `transaction` module never imports `AccountRepository` directly. Instead it depends on `AccountPort` — an interface declared in the `account` module and implemented by `AccountService`. This prevents circular dependencies and keeps the modules independently testable.

### Security

- **Stateless JWT authentication** via Spring OAuth2 Resource Server. All tokens are issued and validated against Keycloak.
- **RBAC:** Two roles — `USER` (standard operations) and `ADMIN` (read-only visibility across all accounts). `/api/v1/admin/**` is ADMIN-only. Auth endpoints are public.
- **Rate limiting:** Bucket4j enforces 10 requests/minute per IP on `/api/v1/auth/**` to mitigate brute-force attacks.
- **Keycloak integration** is isolated behind an `IdentityProviderPort` interface. `KeycloakAdapter` is the sole implementation, using ROPC grant for end-user login and `client_credentials` grant for admin operations (user creation / deletion). The local application UUID is stored as a Keycloak custom attribute (`app_user_id`) and surfaced in JWT claims.

### Data Integrity

- **Idempotent writes:** Every mutating transaction request requires a client-supplied `X-Idempotency-Key` header. The key is stored as `reference_id` with a `UNIQUE` database constraint.
- **Pessimistic locking on transfers:** Both source and destination accounts are locked with `PESSIMISTIC_WRITE` within the same transaction. Locks are always acquired in ascending UUID order to prevent deadlocks regardless of request ordering.
- **Soft delete:** Accounts are never physically deleted — a `deleted_at` timestamp is set instead. `@SQLRestriction` transparently filters deleted accounts from all JPA queries, while the admin API can still see them.
- **Append-only ledger:** Transaction rows are never updated or deleted. The `balance` column on `fn_trn_accounts` is a denormalized cache updated atomically alongside each transaction insert.

### Database

- All tables live in the `app` schema: `fn_trn_users`, `fn_trn_accounts`, `fn_trn_transactions`.
- Schema is managed by Flyway. Auto-configuration is disabled in favor of a manual `FlywayConfig` bean that creates the `app` schema if absent.

### Pagination

- Admin account listing uses **offset-based pagination** (`page` + `pageSize`).
- Transaction history uses **keyset (cursor) pagination** on the `timestamp` column, which stays efficient as the ledger grows and avoids the N-offset problem.

---


## Known Limitations & Future Improvements

### What's incomplete

- **No refresh token endpoint** — The login response returns a `refreshToken` but there is no `/api/v1/auth/refresh` endpoint to exchange it for a new access token without re-logging in.
- **No user profile endpoint** — Users cannot update their email, password, or name after registration through the API; changes would require going directly to Keycloak.
- **Transfer cross-currency not supported** — Transfers between accounts with different currencies are not validated or converted. The amount is applied as-is.
- **No account balance history** — Only individual transactions are stored; there is no endpoint to query the running balance over time.
- **Reconciliation Logic** — In order to make sure there is consistency with account current/running balance a reconciliation job should be present.

### What I would improve with more time

- **Refresh token flow** — Add `POST /api/v1/auth/refresh` to allow token renewal without credential re-entry.
- **Structured error responses** — Currently error shapes are inconsistent across modules. A global `ErrorResponse` DTO with `code`, `message`, and `details` would improve client-side error handling.
- **Integration tests** — Unit tests use Mockito only. Full integration tests with Testcontainers covering end-to-end flows (register → login → deposit → transfer) would significantly increase confidence.
- **Observability** — Add Micrometer metrics, distributed tracing (e.g. OpenTelemetry), and structured JSON logging to make production debugging tractable.
- **Audit log** — Account mutations (create, rename, delete) have no audit trail. An append-only `account_events` table would provide full history.
- **Currency validation** — Enforce that `currency` is a valid ISO 4217 code rather than just checking it's 3 uppercase letters.
- **Admin write operations** — The admin API is currently read-only. Allowing admins to freeze/unfreeze accounts or reverse transactions would be needed in a real system.
- **OpenAPI / Swagger UI** — Adding `springdoc-openapi` would generate interactive API docs automatically from the existing annotations.
