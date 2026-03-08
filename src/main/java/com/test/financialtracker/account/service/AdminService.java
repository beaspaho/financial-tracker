package com.test.financialtracker.account.service;


import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.domain.models.AdminAccountResponse;
import com.test.financialtracker.account.domain.tranformers.AccountMapper;
import com.test.financialtracker.account.repository.AccountRepository;
import com.test.financialtracker.common.exception.ResourceNotFoundException;
import com.test.financialtracker.common.wrapper.PagedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    //TODO:NOT like this
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;


    /**
     * Returns all accounts (active + deleted) with masked PII, paginated.
     *
     * @param page     zero-based page number
     * @param pageSize number of records per page (max 100)
     */
    @Transactional(readOnly = true)
    public PagedResponse<AdminAccountResponse> listAllAccounts(int page, int pageSize, String filterAccount) {
        int effectiveSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int offset = page * effectiveSize;
        long total = accountRepository.countAllForAdmin();

        List<AdminAccountResponse> items = accountRepository
                .findAllForAdmin(effectiveSize, offset, filterAccount)
                .stream()
                .map(accountMapper::toDomain)
                .map(AdminAccountResponse::from)
                .toList();

        log.debug("Admin listed all accounts page={} size={} total={}", page, effectiveSize, total);
        return PagedResponse.of(items, page, effectiveSize, total);
    }


    @Transactional(readOnly = true)
    public AdminAccountResponse getAccountById(UUID accountId) {
        Account account = accountRepository.findById(accountId)
                .map(accountMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        return AdminAccountResponse.from(account);
    }


    /**
     * Returns all ACTIVE accounts for a specific user.
     * <p>
     * Soft-deleted accounts are excluded (soft-delete filter applies).
     */
    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAccountsByUser(UUID userId) {
        log.info("Admin querying accounts for userId={}", userId);

        List<AdminAccountResponse> accounts = accountRepository
                .findAllByUserIdOrderByCreatedAtAsc(userId)
                .stream()
                .map(accountMapper::toDomain)
                .map(AdminAccountResponse::from)
                .toList();

        if (accounts.isEmpty()) {
            log.warn("Admin found no accounts for userId={}", userId);
        }

        return accounts;
    }
}