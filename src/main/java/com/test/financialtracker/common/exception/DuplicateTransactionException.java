package com.test.financialtracker.common.exception;


import com.test.financialtracker.transaction.domains.models.Transaction;

/**
 * Thrown when a request arrives with an X-Idempotency-Key that was
 * already used for a previously processed transaction.

 * Mapped to HTTP 200 OK (not an error — idempotency is a success path).

 */
public class DuplicateTransactionException extends RuntimeException {

    private final Transaction existing;

    public DuplicateTransactionException(Transaction existing) {
        super("Transaction already processed for idempotency key: " + existing.getReferenceId());
        this.existing = existing;
    }

    public Transaction getExisting() {
        return existing;
    }
}