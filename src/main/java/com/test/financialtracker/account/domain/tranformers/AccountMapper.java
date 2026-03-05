package com.test.financialtracker.account.domain.tranformers;

import com.test.financialtracker.account.domain.entity.Accounts;
import com.test.financialtracker.account.domain.models.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public Account toDomain(Accounts entity) {
        return Account.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .balance(entity.getBalance())
                .currency(entity.getCurrency())
                .deletedAt(entity.getDeletedAt())
                .build();
    }

    public Accounts toEntity(Account domain) {
        return Accounts.builder()
                .id(domain.getId())
                .userId(domain.getUserId())
                .name(domain.getName())
                .balance(domain.getBalance())
                .currency(domain.getCurrency())
                .deletedAt(domain.getDeletedAt())
                .build();
    }


    public void updateEntity(Accounts entity, Account domain) {
        entity.setName(domain.getName());
        entity.setBalance(domain.getBalance());
        entity.setDeletedAt(domain.getDeletedAt());
    }
}