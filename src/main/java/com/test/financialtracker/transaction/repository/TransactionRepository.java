package com.test.financialtracker.transaction.repository;


import com.test.financialtracker.transaction.domains.entity.Transactions;
import com.test.financialtracker.transaction.domains.models.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transactions, UUID> {


    Optional<Transactions> findByReferenceId(UUID referenceId);

    @Query("""
        SELECT t FROM Transactions t
        WHERE (
            t.sourceAccountId      = :accountId
            OR t.destinationAccountId = :accountId
        )
        AND (:type      IS NULL OR t.type      = :type)
        AND (:from      IS NULL OR t.timestamp >= :from)
        AND (:to        IS NULL OR t.timestamp <= :to)
        AND (:cursor    IS NULL OR t.timestamp  < :cursor)
        ORDER BY t.timestamp DESC
        LIMIT :pageSize
    """)
    List<Transactions> findHistory(
            @Param("accountId") UUID           accountId,
            @Param("type") TransactionType type,
            @Param("from")      Instant         from,
            @Param("to")        Instant         to,
            @Param("cursor")    Instant         cursor,
            @Param("pageSize")  int             pageSize
    );

}