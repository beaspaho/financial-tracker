package com.test.financialtracker.transaction.domains.entity;


import com.test.financialtracker.common.BaseEntity;
import com.test.financialtracker.transaction.domains.models.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

//TODO:Reeval index
@Entity
@Table(
        name = "fn_trn_transactions",
        indexes = {
                @Index(name = "idx_tx_source_timestamp",  columnList = "source_account_id, timestamp DESC"),
                @Index(name = "idx_tx_dest_timestamp",    columnList = "destination_account_id, timestamp DESC"),
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_tx_reference_id", columnNames = "reference_id")
        }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transactions extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;


    @Column(name = "source_account_id", updatable = false)
    private UUID sourceAccountId;

    @Column(name = "destination_account_id", updatable = false)
    private UUID destinationAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private TransactionType type;


    @Column(nullable = false, updatable = false, precision = 19, scale = 4,
            columnDefinition = "DECIMAL(19,4)")
    private BigDecimal amount;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private UUID referenceId;
}
