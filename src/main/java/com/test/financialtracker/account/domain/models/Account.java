package com.test.financialtracker.account.domain.models;

import com.test.financialtracker.common.exception.InsufficientFundsException;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Getter
@Builder
public class Account {

    private final UUID       id;
    private final UUID       userId;
    private  String    name;
    private BigDecimal       balance;   // mutable — updated atomically in @Transactional
    private final String     currency;
    private final boolean    deleted;
    private Instant deletedAt;  // null = active



    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(this.id, this.balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }


    public void credit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }




    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.userId.equals(userId);
    }

    /**
     * Marks the account as soft-deleted.
     * Business rule: only callable if balance is zero.
     */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    /**
     * Renames the account. Name is the only user-editable field.
     * Currency and balance are never directly editable via API.
     */
    public void rename(String newName) {
        this.name = newName;
    }


    public static Account open(UUID userId, String name, String currency) {
        return Account.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .name(name)
                .balance(BigDecimal.ZERO)
                .currency(currency.toUpperCase())
                .deletedAt(null)
                .build();
    }
}