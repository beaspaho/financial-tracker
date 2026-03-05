package com.test.financialtracker.transaction.domains.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record TransactionHistoryResponse(
        List<TransactionResponse> items,
        Instant                   nextCursor,
        boolean                   hasMore
) {}