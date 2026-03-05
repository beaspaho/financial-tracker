package com.test.financialtracker.transaction.domains.models;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**

 * Nullable fields by type:
 *   DEPOSIT    → sourceAccountId = null,  destinationAccountId = populated
 *   WITHDRAWAL → sourceAccountId = populated, destinationAccountId = null
 *   TRANSFER   → both populated
 */
@Getter
@Builder
public class Transaction {

    private final UUID            id;
    private final UUID            sourceAccountId;       // nullable for DEPOSIT
    private final UUID            destinationAccountId;  // nullable for WITHDRAWAL
    private final TransactionType type;
    private final BigDecimal      amount;
    private final Instant         timestamp;
    private final UUID            referenceId;           //  key


    public static Transaction deposit(UUID destinationAccountId,
                                      BigDecimal amount,
                                      UUID referenceId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(null)
                .destinationAccountId(destinationAccountId)
                .type(TransactionType.DEPOSIT)
                .amount(amount)
                .timestamp(Instant.now())
                .referenceId(referenceId)
                .build();
    }

    public static Transaction withdrawal(UUID sourceAccountId,
                                         BigDecimal amount,
                                         UUID referenceId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(null)
                .type(TransactionType.WITHDRAWAL)
                .amount(amount)
                .timestamp(Instant.now())
                .referenceId(referenceId)
                .build();
    }

    public static Transaction transfer(UUID sourceAccountId,
                                       UUID destinationAccountId,
                                       BigDecimal amount,
                                       UUID referenceId) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .sourceAccountId(sourceAccountId)
                .destinationAccountId(destinationAccountId)
                .type(TransactionType.TRANSFER)
                .amount(amount)
                .timestamp(Instant.now())
                .referenceId(referenceId)
                .build();
    }
}