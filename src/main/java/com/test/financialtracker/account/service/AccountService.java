package com.test.financialtracker.account.service;

import com.test.financialtracker.account.domain.entity.Accounts;
import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.domain.models.AccountResponse;
import com.test.financialtracker.account.domain.models.CreateAccountRequest;
import com.test.financialtracker.account.domain.models.UpdateAccountRequest;
import com.test.financialtracker.account.domain.tranformers.AccountMapper;
import com.test.financialtracker.account.repository.AccountPort;
import com.test.financialtracker.account.repository.AccountRepository;
import com.test.financialtracker.common.exception.BusinessRuleException;
import com.test.financialtracker.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 *
 * Also implements AccountPort: the interface the transaction module uses
 * to read/update accounts without importing AccountRepository directly.
 * This is the key to module isolation.
 * <p>
 * RULES:
 * - CREATE:   authenticated user only, initial balance = 0
 * - READ:     owner only — a user can never see another user's accounts
 * - UPDATE:   owner only — only name is editable
 * - DELETE:   owner only — soft delete, rejected if balance > 0
 * - ADMIN:    separate AdminService handles admin reads (no writes)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService implements AccountPort {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;

    @Transactional
    public AccountResponse create(CreateAccountRequest request, UUID callerId) {
        Account account = Account.open(callerId, request.name(), request.currency());
        Accounts saved = accountRepository.save(accountMapper.toEntity(account));

        log.info("Account created accountId={} userId={} currency={}",
                saved.getId(), callerId, saved.getCurrency());

        return AccountResponse.from(accountMapper.toDomain(saved));
    }


    @Transactional(readOnly = true)
    public List<AccountResponse> listForUser(UUID callerId) {
        return accountRepository.findAllByUserId(callerId)
                .stream()
                .map(accountMapper::toDomain)
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId, UUID callerId) {
        Account account = loadAndAssertOwnership(accountId, callerId, "get");
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse update(UUID accountId, UpdateAccountRequest request, UUID callerId) {
        Account account = loadAndAssertOwnership(accountId, callerId, "update");
        account.rename(request.name());

        Accounts entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        accountMapper.updateEntity(entity, account);

        log.info("Account renamed accountId={} userId={} newName={}",
                accountId, callerId, request.name());

        return AccountResponse.from(accountMapper.toDomain(entity));
    }


    /**
     * Soft-deletes an account.
     * Business rules:
     * 1. Account must belong to the caller
     * 2. Balance must be exactly 0.00 — user must withdraw first
     * The account remains in the DB with deletedAt set.
     * Transaction history is preserved for audit/compliance.
     * {@link org.hibernate.annotations.SQLRestriction} on AccountEntity makes it invisible in all user queries.
     */
    @Transactional
    public void delete(UUID accountId, UUID callerId) {
        Account account = loadAndAssertOwnership(accountId, callerId, "delete");

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessRuleException(
                    "Cannot close an account with a non-zero balance. " +
                            "Please withdraw all funds before closing."
            );
        }

        account.softDelete();

        Accounts entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        accountMapper.updateEntity(entity, account);

        log.info("Account soft-deleted accountId={} userId={}", accountId, callerId);
    }


    /**
     * Loads account with a PESSIMISTIC_WRITE lock.
     * Called by TransactionService within its @Transactional boundary.
     * Lock is held until the transaction commits/rolls back.
     */
    @Override
    public Account findByIdWithLock(UUID accountId) {
        return accountRepository.findByIdWithLock(accountId)
                .map(accountMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    /**
     * Loads account without locking.
     * Used for read-only checks (e.g. ownership validation before acquiring locks).
     */
    @Override
    public Account findById(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(accountMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    /**
     * Persists updated account balance.
     * Called by TransactionService after debit/credit operations.
     * Must be within the same @Transactional as the Transaction INSERT.
     */
    @Override
    @Transactional
    public void save(Account account) {
        accountRepository.findById(account.getId())
                .ifPresent(entity -> {
            accountMapper.updateEntity(entity, account);
        });
    }


    private Account loadAndAssertOwnership(UUID accountId, UUID callerId, String context) {
        Account account = accountRepository.findById(accountId)
                .map(this.accountMapper::toDomain)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (!account.isOwnedBy(callerId)) {
            log.warn("Ownership violation callerId={} accountId={} context={}",
                    callerId, accountId, context);
            throw new AccessDeniedException("Account does not belong to the authenticated user");
        }

        return account;
    }
}