package com.test.financialtracker.common.exception;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when a withdrawal or transfer is requested but the source
 * account does not have enough balance to cover the amount.
 *
 * Mapped to HTTP 422 Unprocessable Entity by GlobalExceptionHandler
 * via BusinessRuleException inheritance.
 *

 */
public class InsufficientFundsException extends RuntimeException {

    private final UUID       accountId;
    private final BigDecimal available;
    private final BigDecimal requested;

    public InsufficientFundsException(UUID accountId, BigDecimal available, BigDecimal requested) {
        super("Insufficient funds in account " + accountId);
        this.accountId = accountId;
        this.available  = available;
        this.requested  = requested;
    }

    public UUID       getAccountId() { return accountId; }
    public BigDecimal getAvailable() { return available; }
    public BigDecimal getRequested() { return requested; }
}
