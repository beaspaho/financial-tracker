package com.test.financialtracker.account.domain.models;



import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound DTO for account data returned to the owning USER.
 *
 * Full data is visible to the owner:
 *   - complete account ID
 *   - full balance
 *   - currency
 *
 * userId is intentionally EXCLUDED — the user already knows their own ID

 */
public record AccountResponse(
        UUID       id,
        String     name,
        BigDecimal balance,
        String     currency,
        boolean    active
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getBalance(),
                account.getCurrency(),
                account.isActive()
        );
    }
}