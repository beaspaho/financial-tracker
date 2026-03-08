package com.test.financialtracker.account.domain.models;


import java.math.BigDecimal;

public record AdminAccountResponse(
        String accountId,
        String userId,
        String name,
        BigDecimal balance,
        String currency,
        boolean active            // false = soft-deleted
) {
    public static AdminAccountResponse from(Account account) {
        String maskedUserId = account.getUserId().toString().substring(0, 8) + "-****";
        String maskedAccountId = account.getId().toString().substring(0, 8) + "-****";
        return new AdminAccountResponse(
                maskedAccountId,
                maskedUserId,
                account.getName(),
                account.getBalance(),
                account.getCurrency(),
                account.isActive()
        );
    }
}
