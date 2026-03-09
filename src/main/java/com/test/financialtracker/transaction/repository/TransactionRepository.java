package com.test.financialtracker.transaction.repository;


import com.test.financialtracker.transaction.domains.entity.Transactions;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transactions, UUID> {


    Optional<Transactions> findByReferenceId(UUID referenceId);

    @Query(value = """
        SELECT * FROM app.fn_trn_transactions t
        WHERE (
            t.source_account_id      = :accountId
            OR t.destination_account_id = :accountId
        )
        AND (CAST(:type   AS text)        IS NULL OR t.type      = CAST(:type   AS text))
        AND (CAST(:from   AS timestamptz) IS NULL OR t.timestamp >= CAST(:from   AS timestamptz))
        AND (CAST(:to     AS timestamptz) IS NULL OR t.timestamp <= CAST(:to     AS timestamptz))
        AND (CAST(:cursor AS timestamptz) IS NULL OR t.timestamp  < CAST(:cursor AS timestamptz))
        ORDER BY t.timestamp DESC
        LIMIT :pageSize
    """, nativeQuery = true)
    List<Transactions> findHistory(
            @Param("accountId") UUID    accountId,
            @Param("type")      String  type,
            @Param("from")      Instant from,
            @Param("to")        Instant to,
            @Param("cursor")    Instant cursor,
            @Param("pageSize")  int     pageSize
    );

}