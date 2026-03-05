package com.test.financialtracker.account.repository;


import com.test.financialtracker.account.domain.models.Account;

import java.util.UUID;

public interface AccountPort {


    Account findByIdWithLock(UUID accountId);

    Account findById(UUID accountId);

    void save(Account account);
}