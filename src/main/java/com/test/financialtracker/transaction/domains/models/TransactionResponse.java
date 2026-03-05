package com.test.financialtracker.transaction.domains.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionResponse(
        UUID            id,
        UUID            sourceAccountId,       // null for DEPOSIT
        UUID            destinationAccountId,  // null for WITHDRAWAL
        TransactionType type,
        BigDecimal      amount,
        Instant         timestamp,
        UUID            referenceId
) {
    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getSourceAccountId(),
                tx.getDestinationAccountId(),
                tx.getType(),
                tx.getAmount(),
                tx.getTimestamp(),
                tx.getReferenceId()
        );
    }
}