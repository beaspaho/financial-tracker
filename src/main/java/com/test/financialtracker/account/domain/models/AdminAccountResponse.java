package com.test.financialtracker.account.domain.models;



import java.math.BigDecimal;
import java.util.UUID;

public record AdminAccountResponse(
        UUID       accountId,
        UUID       userId,
        String     maskedUserId,
        String     name,
        BigDecimal balance,
        String     currency,
        boolean    active            // false = soft-deleted
) {
    public static AdminAccountResponse from(Account account) {
        String masked = account.getUserId().toString().substring(0, 8) + "-****";
        return new AdminAccountResponse(
                account.getId(),
                account.getUserId(),
                masked,
                account.getName(),
                account.getBalance(),
                account.getCurrency(),
                account.isActive()
        );
    }
}
