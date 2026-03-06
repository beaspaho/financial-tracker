package com.test.financialtracker.account.domain.entity;

import com.test.financialtracker.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(
        name = "fn_trn_accounts",
        indexes = {
                @Index(name = "idx_accounts_user_id", columnList = "user_id")
        }
)
@SQLRestriction("deleted_at IS NULL")   // transparent soft-delete filter
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Accounts extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private String name;


    @Column(nullable = false, precision = 19, scale = 4,
            columnDefinition = "DECIMAL(19,4) CHECK (balance >= 0)")
    private BigDecimal balance;




    // After — tell Hibernate the column is CHAR not VARCHAR:
    @Column(name = "currency", columnDefinition = "CHAR(3)", nullable = false)
    private String currency;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
