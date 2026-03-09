# Financial Tracker — Business Flows & Use Case Document

## Table of Contents
1. [System Overview](#1-system-overview)
2. [Actors & Roles](#2-actors--roles)
3. [Cross-Cutting Concerns](#3-cross-cutting-concerns)
4. [Identity Module — Auth Flows](#4-identity-module--auth-flows)
5. [Account Module — Account Management Flows](#5-account-module--account-management-flows)
6. [Transaction Module — Transaction Flows](#6-transaction-module--transaction-flows)
7. [Admin Module — Read-Only Admin Flows](#7-admin-module--read-only-admin-flows)
8. [Error Reference](#8-error-reference)

---

## 1. System Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client (HTTP)                           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │  Filter Chain        │
                    │  ① RequestLogging   │  MDC: requestId, httpMethod,
                    │  ② RateLimiting     │       requestUri, clientIp
                    │  ③ TokenIntrospect  │  MDC: userId, username (after ②)
                    │  ④ Spring Security  │
                    │  ⑤ UserMdcFilter    │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          │                    │                    │
   ┌──────▼──────┐    ┌────────▼───────┐   ┌───────▼──────┐
   │   identity  │    │    account     │   │ transaction  │
   │  /api/v1/   │    │  /api/v1/      │   │  /api/v1/    │
   │   auth/**   │    │  accounts/**   │   │transactions/ │
   └──────┬──────┘    └────────┬───────┘   └───────┬──────┘
          │                    │                    │
   ┌──────▼──────┐    ┌────────▼───────┐   ┌───────▼──────┐
   │  Keycloak   │    │   PostgreSQL   │   │  PostgreSQL  │
   │  (IdP)      │    │   app schema   │   │  app schema  │
   └─────────────┘    └────────────────┘   └──────────────┘
```

---

## 2. Actors & Roles

| Actor      | Role token      | How granted                                      |
|------------|-----------------|--------------------------------------------------|
| Anonymous  | none            | No JWT required for `/register` and `/login`     |
| User       | `ROLE_USER`     | Default role assigned on self-registration       |
| Admin      | `ROLE_ADMIN`    | Assigned by an existing Admin via `/auth/admin/register` |

---

## 3. Cross-Cutting Concerns

### 3.1 Rate Limiting
- Applies to: **all `/api/v1/auth/**` endpoints**
- Limit: **10 requests per minute per IP address**
- IP resolution: respects `X-Forwarded-For` header (reverse-proxy aware)
- Rejection: `429 Too Many Requests` + `Retry-After: 60` header

### 3.2 Token Introspection
- Applies to: every request carrying a `Bearer` token
- Calls Keycloak's token introspection endpoint to confirm the session is still active
- Purpose: detects tokens that were valid at issue time but belong to a logged-out session
- Rejection: `401 Unauthorized`

### 3.3 JWT Validation
- Every protected endpoint requires a valid Keycloak-issued JWT
- Validated by Spring Security's OAuth2 Resource Server (signature + expiry)
- Roles are read from `realm_access.roles` and prefixed with `ROLE_`
- User identity is read from the custom claim `app_user_id` (local UUID stored in Keycloak)

### 3.4 Idempotency (Transactions only)
- All mutating transaction endpoints require the `X-Idempotency-Key: <UUID>` header
- The key is stored as `reference_id` (UNIQUE constraint) in `fn_trn_transactions`
- On duplicate key: the original transaction result is returned (no re-processing)
  - New transaction → `201 Created`
  - Replay → `200 OK` (same body)

### 3.5 MDC Structured Logging
Every log line carries these contextual fields:

| MDC Key      | Source                             | Available from       |
|--------------|------------------------------------|----------------------|
| `requestId`  | `X-Request-ID` header or generated UUID | First filter (order MIN_VALUE) |
| `httpMethod` | HTTP method of the request         | First filter         |
| `requestUri` | Request URI path                   | First filter         |
| `clientIp`   | `X-Forwarded-For` or remote addr   | First filter         |
| `userId`     | `app_user_id` JWT claim            | After Spring Security|
| `username`   | `preferred_username` JWT claim     | After Spring Security|
| `statusCode` | HTTP response status               | In finally (completion log) |
| `durationMs` | Request wall-clock time            | In finally (completion log) |

---

## 4. Identity Module — Auth Flows

### 4.1 User Registration

```
Client                    AuthController         DefaultAuthService       Keycloak         DB
  │                            │                       │                    │               │
  │  POST /api/v1/auth         │                       │                    │               │
  │  /register                 │                       │                    │               │
  │  {email, password,         │                       │                    │               │
  │   firstName, lastName}     │                       │                    │               │
  │───────────────────────────>│                       │                    │               │
  │                            │  @Valid passes?       │                    │               │
  │                            │──────────────────────>│                    │               │
  │                            │                       │  email exists?     │               │
  │                            │                       │────────────────────────────────────>
  │                            │                       │<─── yes/no ────────────────────────│
  │                            │             [if yes] throw ConflictException                │
  │                            │                       │                    │               │
  │                            │                       │  generate UUID     │               │
  │                            │                       │  registerUser()    │               │
  │                            │                       │───────────────────>│               │
  │                            │                       │  creates user +    │               │
  │                            │                       │  sets app_user_id  │               │
  │                            │                       │  + assigns role    │               │
  │                            │                       │<─── keycloakId ───│               │
  │                            │                       │                    │               │
  │                            │                       │  save local User   │               │
  │                            │                       │────────────────────────────────────>
  │                            │                       │<─── saved ─────────────────────────│
  │                            │                       │                    │               │
  │<── 201 Created ────────────│                       │                    │               │
  │    {userId, email}         │                       │                    │               │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `email` not blank, valid format | `@Valid` on `RegisterRequest` | `400 Bad Request` |
| `password` not blank | `@Valid` | `400 Bad Request` |
| `firstName`, `lastName` not blank | `@Valid` | `400 Bad Request` |
| Email not already registered (DB check) | `DefaultAuthService` | `409 Conflict` |
| Rate limit: 10 req/min per IP | `RateLimitingFilter` | `429 Too Many Requests` |
| Initial role is always `USER` | `AuthController` hard-codes `User.Role.USER` | — |

---

### 4.2 Admin Registration

```
Client (must be ADMIN)         AuthController          DefaultAuthService
  │                                 │                       │
  │  POST /api/v1/auth              │                       │
  │  /admin/register                │                       │
  │  Authorization: Bearer <JWT>    │                       │
  │────────────────────────────────>│                       │
  │                         [@PreAuthorize("hasRole('ADMIN')")]
  │                           [fails → 403 if not ADMIN]   │
  │                                 │  register(req, ADMIN) │
  │                                 │──────────────────────>│
  │                                 │       [same flow as User Registration, role = ADMIN]
  │<── 201 Created ─────────────────│                       │
```

**Restrictions:**
- Caller must have `ROLE_ADMIN` — enforced via `@PreAuthorize`
- Subject to rate limiting (same `/api/v1/auth/**` rule)

---

### 4.3 Login

```
Client                    AuthController         DefaultAuthService       Keycloak         DB
  │                            │                       │                    │               │
  │  POST /api/v1/auth/login   │                       │                    │               │
  │  {email, password}         │                       │                    │               │
  │───────────────────────────>│                       │                    │               │
  │                            │  @Valid passes?       │                    │               │
  │                            │──────────────────────>│                    │               │
  │                            │                       │  ROPC grant        │               │
  │                            │                       │───────────────────>│               │
  │                            │                       │  [wrong creds]     │               │
  │                            │                       │<── 401 ───────────│               │
  │                            │          [throws IdentityProviderException]│               │
  │<── 401 Unauthorized ───────│                       │                    │               │
  │                            │                       │  [ok]              │               │
  │                            │                       │<── {access_token,  │               │
  │                            │                       │    refresh_token,  │               │
  │                            │                       │    expires_in} ───│               │
  │                            │                       │  extract sub (keycloakId) from JWT │
  │                            │                       │  find local user by keycloakId     │
  │                            │                       │────────────────────────────────────>
  │                            │                       │<─── User ──────────────────────────│
  │<── 200 OK ─────────────────│                       │                    │               │
  │    {userId, email, role,   │                       │                    │               │
  │     accessToken,           │                       │                    │               │
  │     refreshToken,          │                       │                    │               │
  │     expiresIn}             │                       │                    │               │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `email`, `password` not blank | `@Valid` | `400 Bad Request` |
| Credentials valid | Keycloak ROPC | `401 Unauthorized` |
| Local user record synced | DB lookup by `keycloakId` | `500` (sync issue — support required) |
| Rate limit: 10 req/min per IP | `RateLimitingFilter` | `429 Too Many Requests` |

---

### 4.4 Logout

```
Client                    AuthController         DefaultAuthService       Keycloak
  │                            │                       │                    │
  │  POST /api/v1/auth/logout  │                       │                    │
  │  Authorization: Bearer JWT │                       │                    │
  │───────────────────────────>│                       │                    │
  │                     [Spring Security validates JWT]│                    │
  │                     [TokenIntrospectionFilter runs]│                    │
  │                            │  logout(userId)       │                    │
  │                            │──────────────────────>│                    │
  │                            │                       │  revoke session    │
  │                            │                       │───────────────────>│
  │                            │                       │<─── ok ───────────│
  │<── 200 OK ─────────────────│                       │                    │
  │    {userId, email, role}   │                       │                    │
```

**Restrictions:**
- Requires `ROLE_USER` or `ROLE_ADMIN`
- After logout: the token is revoked in Keycloak; subsequent requests with the same token are rejected by `TokenIntrospectionFilter` with `401`

---

## 5. Account Module — Account Management Flows

> All account endpoints require `ROLE_USER`. Ownership is always enforced — a user cannot read or modify another user's accounts.

### 5.1 Create Account

```
Client                   AccountController        AccountService            DB
  │                            │                       │                    │
  │  POST /api/v1/accounts     │                       │                    │
  │  {name, currency}          │                       │                    │
  │───────────────────────────>│                       │                    │
  │                      [@PreAuthorize("hasRole('USER')")]
  │                            │  create(req, userId)  │                    │
  │                            │──────────────────────>│                    │
  │                            │                       │  Account.open()    │
  │                            │                       │  balance = 0.00    │
  │                            │                       │  save to DB        │
  │                            │                       │───────────────────>│
  │                            │                       │<─── saved ────────│
  │<── 201 Created ────────────│                       │                    │
  │    {id, name, currency,    │                       │                    │
  │     balance: 0.00}         │                       │                    │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `name` not blank | `@Valid` on `CreateAccountRequest` | `400 Bad Request` |
| `currency` not blank | `@Valid` | `400 Bad Request` |
| Valid JWT / authenticated | Spring Security | `401 Unauthorized` |
| Caller has `ROLE_USER` | `@PreAuthorize` | `403 Forbidden` |
| Initial balance is always `0.00` | `Account.open()` domain method | — |

---

### 5.2 List Accounts

```
Client                   AccountController        AccountService            DB
  │                            │                       │                    │
  │  GET /api/v1/accounts      │                       │                    │
  │───────────────────────────>│                       │                    │
  │                            │  listForUser(userId)  │                    │
  │                            │──────────────────────>│                    │
  │                            │                       │  findAll where     │
  │                            │                       │  userId = callerId │
  │                            │                       │  AND deleted_at    │
  │                            │                       │  IS NULL           │
  │                            │                       │───────────────────>│
  │                            │                       │<─── [accounts] ───│
  │<── 200 OK ─────────────────│                       │                    │
  │    [{id, name, currency,   │                       │                    │
  │      balance}, ...]        │                       │                    │
```

**Restrictions:**
- Returns only the caller's accounts (userId filter applied in query)
- Soft-deleted accounts are invisible (`@SQLRestriction` on entity: `deleted_at IS NULL`)

---

### 5.3 Get Account by ID

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| Account exists | DB lookup | `404 Not Found` |
| Account not soft-deleted | `@SQLRestriction` | `404 Not Found` |
| Caller owns the account | `loadAndAssertOwnership()` | `403 Forbidden` |

---

### 5.4 Rename Account (Update)

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `name` not blank | `@Valid` on `UpdateAccountRequest` | `400 Bad Request` |
| Account exists and not deleted | DB lookup | `404 Not Found` |
| Caller owns the account | `loadAndAssertOwnership()` | `403 Forbidden` |
| Only `name` is editable | `account.rename()` domain method | — |

---

### 5.5 Delete Account (Soft Delete)

```
Client                   AccountController        AccountService            DB
  │                            │                       │                    │
  │  DELETE /api/v1/accounts   │                       │                    │
  │  /{id}                     │                       │                    │
  │───────────────────────────>│                       │                    │
  │                            │  delete(id, userId)   │                    │
  │                            │──────────────────────>│                    │
  │                            │           loadAndAssertOwnership()          │
  │                            │                       │  [404 if not found]│
  │                            │                       │  [403 if not owner]│
  │                            │                       │                    │
  │                            │              balance == 0?                  │
  │                            │           [400 if balance > 0]             │
  │                            │                       │                    │
  │                            │                       │  account.softDelete│
  │                            │                       │  sets deleted_at   │
  │                            │                       │───────────────────>│
  │<── 204 No Content ─────────│                       │                    │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| Account exists | DB lookup | `404 Not Found` |
| Caller owns the account | `loadAndAssertOwnership()` | `403 Forbidden` |
| Balance must be exactly `0.00` | `AccountService.delete()` | `422 Unprocessable Entity` |
| Transaction history is **preserved** | Immutable ledger design | — |
| Account hidden from all future queries | `@SQLRestriction` (`deleted_at IS NULL`) | — |

---

## 6. Transaction Module — Transaction Flows

> All transaction endpoints require `ROLE_USER` and the `X-Idempotency-Key: <UUID>` header.
> Both source and destination accounts must be owned by the caller.

### 6.1 Deposit

```
Client                TransactionController      TransactionService      AccountPort        DB
  │                          │                        │                     │               │
  │  POST /api/v1/           │                        │                     │               │
  │  transactions/deposit    │                        │                     │               │
  │  X-Idempotency-Key: UUID │                        │                     │               │
  │  {accountId, amount}     │                        │                     │               │
  │─────────────────────────>│                        │                     │               │
  │                          │  deposit(req, callerId,│                     │               │
  │                          │    idempotencyKey)     │                     │               │
  │                          │───────────────────────>│                     │               │
  │                          │                        │  findByReferenceId  │               │
  │                          │                        │────────────────────────────────────>│
  │                          │              [duplicate key → return 200 OK with existing tx]│
  │                          │                        │                     │               │
  │                          │                        │  findByIdWithLock   │               │
  │                          │                        │  (PESSIMISTIC_WRITE)│               │
  │                          │                        │────────────────────>│               │
  │                          │                        │  assertOwnership()  │               │
  │                          │                        │  [403 if not owner] │               │
  │                          │                        │  [404 if inactive]  │               │
  │                          │                        │                     │               │
  │                          │                        │  account.credit()   │               │
  │                          │                        │  balance += amount  │               │
  │                          │                        │                     │               │
  │                          │                        │  save account       │               │
  │                          │                        │  insert transaction │               │
  │                          │                        │  (atomic, same tx)  │               │
  │                          │                        │────────────────────────────────────>│
  │<── 201 Created ──────────│                        │                     │               │
  │    {txId, type:DEPOSIT,  │                        │                     │               │
  │     amount, timestamp}   │                        │                     │               │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `X-Idempotency-Key` header present | Spring MVC | `400 Bad Request` |
| `accountId` not null | `@Valid` on `DepositRequest` | `400 Bad Request` |
| `amount` > 0 | `@Valid` | `400 Bad Request` |
| Duplicate idempotency key | `txRepo.findByReferenceId()` | `200 OK` (replay, no re-processing) |
| Account exists and not soft-deleted | `findByIdWithLock()` | `404 Not Found` |
| Caller owns the account | `assertOwnership()` | `403 Forbidden` |
| Balance update + TX insert are atomic | `@Transactional` | Rolls back on any failure |

---

### 6.2 Withdrawal

```
Client                TransactionController      TransactionService      AccountPort        DB
  │  POST /api/v1/           │                        │                     │               │
  │  transactions/withdraw   │                        │                     │               │
  │  X-Idempotency-Key: UUID │                        │                     │               │
  │  {accountId, amount}     │                        │                     │               │
  │─────────────────────────>│                        │                     │               │
  │                          │───────────────────────>│                     │               │
  │                          │              [duplicate check — same as deposit]             │
  │                          │                        │  findByIdWithLock   │               │
  │                          │                        │────────────────────>│               │
  │                          │                        │  assertOwnership()  │               │
  │                          │                        │                     │               │
  │                          │                        │  account.debit()    │               │
  │                          │                        │  [InsufficientFunds │               │
  │                          │                        │   if balance < amt] │               │
  │                          │           [throws BusinessRuleException → 422]               │
  │                          │                        │                     │               │
  │                          │                        │  balance -= amount  │               │
  │                          │                        │  save + insert TX   │               │
  │<── 201 Created ──────────│                        │                     │               │
```

**Additional Restrictions (beyond deposit):**

| Check | Where | Error |
|-------|-------|-------|
| `balance >= amount` | `account.debit()` domain method | `422 Unprocessable Entity` — "Insufficient funds" |

---

### 6.3 Transfer

```
Client                TransactionController      TransactionService                         DB
  │  POST /api/v1/           │                        │                                     │
  │  transactions/transfer   │                        │                                     │
  │  X-Idempotency-Key: UUID │                        │                                     │
  │  {sourceId,              │                        │                                     │
  │   destinationId, amount} │                        │                                     │
  │─────────────────────────>│                        │                                     │
  │                          │───────────────────────>│                                     │
  │                          │              sourceId == destinationId?                      │
  │                          │              [400 if same account]                           │
  │                          │                        │                                     │
  │                          │              [duplicate idempotency check]                   │
  │                          │                        │                                     │
  │                          │              DEADLOCK PREVENTION:                            │
  │                          │              lock accounts in UUID sort order                │
  │                          │              (lower UUID first — always)                     │
  │                          │                        │  PESSIMISTIC_WRITE lock(lower UUID) │
  │                          │                        │────────────────────────────────────>│
  │                          │                        │  PESSIMISTIC_WRITE lock(higher UUID)│
  │                          │                        │────────────────────────────────────>│
  │                          │                        │                                     │
  │                          │              assertOwnership(source)                         │
  │                          │              assertOwnership(destination)                    │
  │                          │              [403 if either is not owned by caller]          │
  │                          │                        │                                     │
  │                          │                        │  source.debit(amount)               │
  │                          │                        │  [422 if insufficient funds]        │
  │                          │                        │  destination.credit(amount)         │
  │                          │                        │                                     │
  │                          │                        │  save source, save dest, insert TX  │
  │                          │                        │  (one @Transactional — all or none) │
  │<── 201 Created ──────────│                        │                                     │
```

**Validations & Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| `sourceId != destinationId` | `TransactionService.transfer()` | `400 Bad Request` |
| Duplicate idempotency key | `txRepo.findByReferenceId()` | `200 OK` (replay) |
| Both accounts exist and are active | `findByIdWithLock()` for each | `404 Not Found` |
| Caller owns **both** accounts | `assertOwnership()` twice | `403 Forbidden` |
| Source balance >= amount | `source.debit()` | `422 Unprocessable Entity` |
| Deadlock prevention | UUID-ordered lock acquisition | — |
| Entire operation is atomic | `@Transactional` | Rolls back both accounts + TX |

---

### 6.4 Transaction History

```
Client                TransactionController      TransactionService      AccountPort        DB
  │                          │                        │                     │               │
  │  GET /api/v1/            │                        │                     │               │
  │  transactions?           │                        │                     │               │
  │  accountId=UUID          │                        │                     │               │
  │  &type=DEPOSIT           │                        │                     │               │
  │  &from=2025-01-01Z       │                        │                     │               │
  │  &to=2025-01-31Z         │                        │                     │               │
  │  &cursor=2025-01-15Z     │                        │                     │               │
  │  &pageSize=20            │                        │                     │               │
  │─────────────────────────>│                        │                     │               │
  │                          │  getHistory(...)       │                     │               │
  │                          │───────────────────────>│                     │               │
  │                          │                        │  findById(accountId)│               │
  │                          │                        │────────────────────>│               │
  │                          │                        │  assertOwnership()  │               │
  │                          │                        │  [403 if not owner] │               │
  │                          │                        │                     │               │
  │                          │                        │  validate from < to │               │
  │                          │                        │  [400 if from > to] │               │
  │                          │                        │                     │               │
  │                          │                        │  [if no date range: │               │
  │                          │                        │   default last 30d] │               │
  │                          │                        │                     │               │
  │                          │                        │  keyset paginate    │               │
  │                          │                        │  (cursor-based)     │               │
  │                          │                        │────────────────────────────────────>│
  │<── 200 OK ───────────────│                        │                     │               │
  │    {items: [...],        │                        │                     │               │
  │     nextCursor,          │                        │                     │               │
  │     hasMore}             │                        │                     │               │
```

**Query Parameters:**

| Parameter  | Required | Default | Constraint |
|------------|----------|---------|------------|
| `accountId` | Yes | — | Must be a valid UUID |
| `type` | No | all | `DEPOSIT`, `WITHDRAWAL`, `TRANSFER` |
| `from` | No | `now - 30 days` (if no range given) | ISO-8601 instant |
| `to` | No | `null` | ISO-8601 instant; must be after `from` |
| `cursor` | No | `null` | ISO-8601 instant from previous `nextCursor` |
| `pageSize` | No | `20` | Max `100`; values above are clamped |

**Restrictions:**

| Check | Where | Error |
|-------|-------|-------|
| Account exists | `findById()` | `404 Not Found` |
| Caller owns account | `assertOwnership()` | `403 Forbidden` |
| `from` before `to` (if both set) | `TransactionService.getHistory()` | `422 Unprocessable Entity` |
| `pageSize` capped at 100 | `Math.min(pageSize, MAX_PAGE_SIZE)` | Silent cap (no error) |
| Default date window: last 30 days | Applied when no `from`/`to` provided | — |

---

## 7. Admin Module — Read-Only Admin Flows

> All `/api/v1/admin/**` endpoints require `ROLE_ADMIN`. No write operations exist.

### 7.1 List All Accounts (Paginated)

```
GET /api/v1/admin/accounts?page=0&pageSize=20&filterAccount=ALL
```

| Parameter | Options | Default |
|-----------|---------|---------|
| `page` | integer | `0` |
| `pageSize` | integer | `20` |
| `filterAccount` | `ALL`, `ACTIVE`, `DELETED` | `ALL` |

- Returns **all accounts including soft-deleted** ones (no `@SQLRestriction` in admin queries)
- PII is masked in `AdminAccountResponse`
- Offset-based pagination (supports jump-to-page for analysts)

### 7.2 Get Single Account (Admin)

```
GET /api/v1/admin/accounts/{id}
```

- Returns account even if soft-deleted
- `404` only if the account never existed

### 7.3 List Accounts by User (Admin)

```
GET /api/v1/admin/accounts/by-user/{userId}
```

- Returns only **active** accounts for the specified user
- Empty list if user has no accounts (not a 404)

---

## 8. Error Reference

| HTTP Status | Meaning | Typical Cause |
|-------------|---------|---------------|
| `400 Bad Request` | Validation failure | Missing or invalid request fields (`@Valid`) |
| `401 Unauthorized` | Authentication failure | Invalid/expired JWT, wrong Keycloak credentials, revoked session |
| `403 Forbidden` | Authorisation failure | Wrong role, accessing another user's resource |
| `404 Not Found` | Resource missing | Account/transaction doesn't exist or is soft-deleted |
| `409 Conflict` | Duplicate resource | Email already registered |
| `422 Unprocessable Entity` | Business rule violation | Insufficient funds, `from > to`, closing account with non-zero balance |
| `429 Too Many Requests` | Rate limit exceeded | > 10 auth requests/min from same IP |
| `500 Internal Server Error` | Unexpected error | Internal sync issue or unhandled exception |
| `502 Bad Gateway` | IDP unavailable | Keycloak unreachable during login |

---

## Appendix — Database Schema Summary

```
app.fn_trn_users
  id (UUID PK)          — local app user ID (stored as app_user_id in Keycloak JWT)
  keycloak_id (VARCHAR) — Keycloak subject (sub claim)
  email (VARCHAR)
  role (VARCHAR)        — USER | ADMIN

app.fn_trn_accounts
  id (UUID PK)
  user_id (UUID FK → fn_trn_users)
  name (VARCHAR)
  currency (VARCHAR)
  balance (DECIMAL)     — denormalized cache, updated atomically with each transaction
  deleted_at (TIMESTAMP)— NULL = active; non-NULL = soft-deleted

app.fn_trn_transactions  [append-only, immutable ledger]
  id (UUID PK)
  account_id (UUID FK → fn_trn_accounts)
  destination_id (UUID) — populated for TRANSFER type only
  type (VARCHAR)        — DEPOSIT | WITHDRAWAL | TRANSFER
  amount (DECIMAL)
  reference_id (UUID UNIQUE) — idempotency key
  created_at (TIMESTAMP)
```
