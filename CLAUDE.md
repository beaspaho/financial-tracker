# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build (skip tests)
./mvnw clean package -DskipTests

# Run locally
./mvnw spring-boot:run

# Run all tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TransactionServiceTest

# Run a single test method
./mvnw test -Dtest=TransactionServiceTest#deposit_success
```

## External Dependencies (must be running locally)

| Service    | Default URL                      | Purpose                                 |
|------------|----------------------------------|-----------------------------------------|
| PostgreSQL | `localhost:5432` / db `financedb`| Primary datastore                       |
| Keycloak   | `localhost:8180` / realm `finance`| Identity provider (JWT issuer)         |
| Redis      | `localhost:6379`                 | Backing store for Bucket4j rate limiter |

All defaults are overridable via environment variables defined in `application.yaml`.

## Architecture Overview

The app is a hexagonal-style Spring Boot 3.4.3 (Java 21) backend organized into three domain modules:

### Modules

- **`identity`** — Registration, login, Keycloak integration, security config
- **`account`** — Account CRUD and admin read operations
- **`transaction`** — Deposit, withdrawal, transfer, and history

Each module follows the same internal layering:
```
ports/       → REST controllers (inbound adapters)
service/     → Application/domain logic
repository/  → JPA repositories
domain/
  models/    → Pure domain objects (no JPA annotations)
  entity/    → JPA entities
  transformers/ → Mappers between domain ↔ entity
```

### Key Architectural Decisions

**Identity / Keycloak integration**
- `IdentityProviderPort` (interface) decouples auth logic from Keycloak specifics.
- `KeycloakAdapter` is the sole implementation — uses ROPC grant for login and admin `client_credentials` grant to create/delete users in Keycloak.
- On registration, Keycloak stores the local app UUID as a custom attribute (`app_user_id`). This claim is later extracted from JWTs via `SecurityContextHelper.getAuthenticatedUserId()`.
- `JwtConverter` reads `realm_access.roles` from the Keycloak JWT and maps them to Spring Security `ROLE_` authorities.
- Flyway auto-configuration is **disabled** (`spring.autoconfigure.exclude`). A manual `FlywayConfig` bean sets the schema to `app` and creates it if absent.

**Account → Transaction isolation**
- `AccountPort` (interface in `account.repository`) is the only API the transaction module uses to read/mutate accounts. `AccountService` implements it.
- This prevents the transaction module from importing `AccountRepository` directly, keeping the modules decoupled.

**Concurrency / correctness**
- Transfers acquire `PESSIMISTIC_WRITE` locks on both accounts, always in a consistent UUID-sort order to prevent deadlocks.
- Idempotency: every mutating transaction request carries a client-supplied UUID (`depositKey` / `idempotencyKey`) stored as `reference_id` with a `UNIQUE` constraint. Duplicate submissions return a `DuplicateTransactionException` carrying the original transaction.

**Security**
- Stateless JWT validation via Spring OAuth2 Resource Server.
- `/api/v1/auth/**` is public; `/api/v1/admin/**` requires `ROLE_ADMIN`; everything else requires authentication.
- `RateLimitingFilter` (Bucket4j): 10 requests/minute per IP on `/api/v1/auth/**`.

**Database schema**
- All tables live in the `app` schema: `fn_trn_users`, `fn_trn_accounts`, `fn_trn_transactions`.
- Accounts use soft-delete (`deleted_at`); `@SQLRestriction` hides deleted rows from all JPA queries.
- Transactions are append-only (immutable ledger). Balance on `fn_trn_accounts` is a denormalized cache updated atomically with each transaction insert.

### Configuration

Keycloak properties (`application.yaml`):
```yaml
keycloak:
  server-url:          # KEYCLOAK_SERVER_URL
  realm:               # KEYCLOAK_REALM
  admin-client-id:     # KEYCLOAK_ADMIN_CLIENT_ID   (client_credentials grant)
  admin-client-secret: # KEYCLOAK_ADMIN_CLIENT_SECRET
  app-client-id:       # KEYCLOAK_APP_CLIENT_ID     (ROPC grant for end-users)
```

Keycloak realm must have:
- Two clients: `finance-admin` (service account with user-management permissions) and `finance-app` (ROPC-enabled).
- Realm roles `USER` and `ADMIN`.
- A protocol mapper that adds the local `app_user_id` attribute as a JWT claim.

### Testing

Unit tests use Mockito only (no Spring context, no DB). Testcontainers + PostgreSQL and H2 are on the classpath for integration tests. To disable Flyway in tests set `spring.flyway.enabled=false`.
