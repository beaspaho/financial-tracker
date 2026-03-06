-- ============================================================
-- V1__baseline_schema.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS fn_trn_users (
                                            id              UUID            NOT NULL,
                                            keycloak_id     VARCHAR(255)    NOT NULL,
    email           VARCHAR(255)    NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),

    CONSTRAINT pk_users             PRIMARY KEY (id),
    CONSTRAINT uq_users_keycloak_id UNIQUE      (keycloak_id),
    CONSTRAINT uq_users_email       UNIQUE      (email),
    CONSTRAINT chk_users_role       CHECK       (role IN ('USER', 'ADMIN'))
    );

COMMENT ON TABLE  fn_trn_users                IS 'Local application identity — links to Keycloak via keycloak_id';
COMMENT ON COLUMN fn_trn_users.keycloak_id    IS 'Keycloak subject UUID (sub claim). Immutable — never changes even if user updates email in Keycloak.';
COMMENT ON COLUMN fn_trn_users.role           IS 'Application role: USER for standard accounts, ADMIN for oversight. Matches Keycloak realm role.';

-- ============================================================

CREATE TABLE IF NOT EXISTS fn_trn_accounts (
                                               id              UUID            NOT NULL,
                                               user_id         UUID            NOT NULL,
                                               name            VARCHAR(50)     NOT NULL,
    balance         DECIMAL(19,4)   NOT NULL DEFAULT 0.0000,
    currency        VARCHAR(3)         NOT NULL,
    deleted_at      TIMESTAMPTZ     NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by      VARCHAR(255),

    CONSTRAINT pk_accounts           PRIMARY KEY (id),
    CONSTRAINT fk_accounts_user      FOREIGN KEY (user_id) REFERENCES fn_trn_users (id), -- fixed
    CONSTRAINT chk_accounts_balance  CHECK       (balance >= 0),
    CONSTRAINT chk_accounts_currency CHECK       (currency ~ '^[A-Z]{3}$')
    );

COMMENT ON TABLE  fn_trn_accounts              IS 'User financial accounts (wallets). Soft-deleted on close — history preserved.';
COMMENT ON COLUMN fn_trn_accounts.balance      IS 'Denormalised balance cache. Updated atomically with every transaction INSERT. Source of truth = SUM of transactions table.';
COMMENT ON COLUMN fn_trn_accounts.currency     IS 'ISO 4217 3-letter code. Immutable after creation — changing currency would corrupt transaction history.';
COMMENT ON COLUMN fn_trn_accounts.deleted_at   IS 'Set on soft-delete. NULL = active. @SQLRestriction in JPA hides non-null rows from user queries automatically.';

CREATE INDEX idx_accounts_user_id     ON fn_trn_accounts (user_id);
CREATE INDEX idx_accounts_active_user ON fn_trn_accounts (user_id) WHERE deleted_at IS NULL;

-- ============================================================

CREATE TABLE IF NOT EXISTS fn_trn_transactions (
                                                   id                      UUID            NOT NULL,
                                                   source_account_id       UUID            NULL,
                                                   destination_account_id  UUID            NULL,
                                                   type                    VARCHAR(20)     NOT NULL,
    amount                  DECIMAL(19,4)   NOT NULL,
    timestamp               TIMESTAMPTZ     NOT NULL,
    reference_id            UUID            NOT NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_by              VARCHAR(255),

    CONSTRAINT pk_transactions              PRIMARY KEY (id),
    CONSTRAINT uq_tx_reference_id           UNIQUE      (reference_id),
    CONSTRAINT fk_tx_source_account         FOREIGN KEY (source_account_id)       REFERENCES fn_trn_accounts (id), -- fixed
    CONSTRAINT fk_tx_destination_account    FOREIGN KEY (destination_account_id)  REFERENCES fn_trn_accounts (id), -- fixed
    CONSTRAINT chk_tx_type                  CHECK       (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT chk_tx_amount_positive       CHECK       (amount > 0),
    CONSTRAINT chk_tx_deposit_has_dest      CHECK       (type != 'DEPOSIT'    OR destination_account_id IS NOT NULL),
    CONSTRAINT chk_tx_withdrawal_has_src    CHECK       (type != 'WITHDRAWAL' OR source_account_id       IS NOT NULL),
    CONSTRAINT chk_tx_transfer_has_both     CHECK       (type != 'TRANSFER'   OR (source_account_id IS NOT NULL AND destination_account_id IS NOT NULL)),
    CONSTRAINT chk_tx_transfer_no_self      CHECK       (source_account_id IS NULL OR destination_account_id IS NULL OR source_account_id != destination_account_id)
    );

COMMENT ON TABLE  fn_trn_transactions                 IS 'Immutable financial ledger. Append-only — never UPDATE or DELETE rows.';
COMMENT ON COLUMN fn_trn_transactions.reference_id    IS 'Client-supplied idempotency key (X-Idempotency-Key header). UNIQUE constraint prevents double-processing even in race conditions.';
COMMENT ON COLUMN fn_trn_transactions.amount          IS 'Always positive. Direction is implied by type and which account FK is populated.';

CREATE INDEX idx_tx_source_timestamp ON fn_trn_transactions (source_account_id,      timestamp DESC) WHERE source_account_id      IS NOT NULL;
CREATE INDEX idx_tx_dest_timestamp   ON fn_trn_transactions (destination_account_id, timestamp DESC) WHERE destination_account_id IS NOT NULL;
CREATE INDEX idx_tx_reference_id     ON fn_trn_transactions (reference_id);
CREATE INDEX idx_tx_timestamp        ON fn_trn_transactions (timestamp DESC);