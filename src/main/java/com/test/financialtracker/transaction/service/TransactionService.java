package com.test.financialtracker.transaction.service;


import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.repository.AccountPort;
import com.test.financialtracker.common.exception.*;
import com.test.financialtracker.transaction.domains.models.*;
import com.test.financialtracker.transaction.domains.tranformers.TransactionMapper;
import com.test.financialtracker.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountPort accountPort;
    private final TransactionRepository txRepo;
    private final TransactionMapper txMapper;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;


    @Transactional
    public TransactionResponse deposit(DepositRequest request, UUID callerId, UUID idempotencyKey) {

        var existing = txRepo.findByReferenceId(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent deposit callerId={} referenceId={}", callerId, idempotencyKey);
            throw new DuplicateTransactionException(txMapper.toDomain(existing.get()));
        }

        Account account = accountPort.findByIdWithLock(request.accountId());

        assertOwnership(account, callerId, "Deposit Account");

        account.credit(request.amount());

        accountPort.save(account);
        Transaction tx = Transaction.deposit(
                account.getId(), request.amount(), idempotencyKey
        );
        Transaction saved = txMapper.toDomain(txRepo.save(txMapper.toEntity(tx)));

        log.info("Deposit processed callerId={} accountId={} amount={} referenceId={}",
                callerId, account.getId(), request.amount(), idempotencyKey);

        return TransactionResponse.from(saved);
    }


    @Transactional
    public TransactionResponse withdraw(WithdrawRequest request, UUID callerId, UUID idempotencyKey) {

        // ── 1. Idempotency ──────────────────────────────────────────
        var existing = txRepo.findByReferenceId(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent withdrawal callerId={} referenceId={}", callerId, idempotencyKey);
            throw new DuplicateTransactionException(txMapper.toDomain(existing.get()));
        }

        Account account = accountPort.findByIdWithLock(request.accountId());

        assertOwnership(account, callerId, "Withdrawal Account");

        try {
            account.debit(request.amount());
        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds callerId={} accountId={} available={} requested={}",
                    callerId, e.getAccountId(), e.getAvailable(), e.getRequested());
            throw new BusinessRuleException("Insufficient funds");
        }

        accountPort.save(account);
        Transaction tx = Transaction.withdrawal(
                account.getId(), request.amount(), idempotencyKey
        );
        Transaction saved = txMapper.toDomain(txRepo.save(txMapper.toEntity(tx)));

        log.info("Withdrawal processed callerId={} accountId={} amount={} referenceId={}",
                callerId, account.getId(), request.amount(), idempotencyKey);

        return TransactionResponse.from(saved);
    }


    @Transactional
    public TransactionResponse transfer(TransferRequest request, UUID callerId, UUID idempotencyKey) {

        if (request.sourceId().equals(request.destinationId())) {
            throw new BusinessRuleException("Source and destination accounts must be different");
        }

        var existing = txRepo.findByReferenceId(idempotencyKey);
        if (existing.isPresent()) {
            log.info("Idempotent transfer callerId={} referenceId={}", callerId, idempotencyKey);
            throw new DuplicateTransactionException(txMapper.toDomain(existing.get()));
        }

        /*
         * DEADLOCK PREVENTION:
         *   If User A transfers to B while User B transfers to A simultaneously,
         *   both threads try to lock both rows. Without ordering, each thread
         *   holds one lock and waits for the other → deadlock.
         *   By always locking the lower UUID first, both threads acquire
         *   locks in the same order, so one waits while the other completes.
         */

        // ID A: source
        // ID B: destination
        // B < A
        // srcFirst = false
        // firstLockId = B
        //
        Account source;
        Account destination;

        if (request.sourceId().compareTo(request.destinationId()) < 0) {
            source = accountPort.findByIdWithLock(request.sourceId());
            destination = accountPort.findByIdWithLock(request.destinationId());
        } else {
            destination = accountPort.findByIdWithLock(request.destinationId());
            source = accountPort.findByIdWithLock(request.sourceId());
        }

        assertOwnership(source, callerId, "Source Account");
        assertOwnership(destination, callerId, "Destination Account");

        try {
            source.debit(request.amount());
        } catch (InsufficientFundsException e) {
            log.warn("Insufficient funds for transfer callerId={} sourceId={} available={} requested={}",
                    callerId, e.getAccountId(), e.getAvailable(), e.getRequested());
            throw new BusinessRuleException("Insufficient funds in source account");
        }
        destination.credit(request.amount());

        accountPort.save(source);
        accountPort.save(destination);

        Transaction tx = Transaction.transfer(
                source.getId(), destination.getId(),
                request.amount(), idempotencyKey
        );
        Transaction saved = txMapper.toDomain(txRepo.save(txMapper.toEntity(tx)));

        log.info("Transfer processed callerId={} srcId={} destId={} amount={} referenceId={}",
                callerId, source.getId(), destination.getId(),
                request.amount(), idempotencyKey);

        return TransactionResponse.from(saved);
    }

    /**
     * Validation:
     * - Account must belong to the caller
     * - If both from and to are provided, from must be before to
     * - pageSize capped at MAX_PAGE_SIZE (100) to prevent memory issues
     *
     * @param accountId account to query
     * @param type      optional filter (DEPOSIT/WITHDRAWAL/TRANSFER)
     * @param from      optional start of date range (inclusive)
     * @param to        optional end of date range (inclusive)
     * @param cursor    keyset cursor (timestamp of last record from prev page)
     * @param pageSize  number of records per page (default 20, max 100)
     * @param callerId  authenticated user ID
     */
    @Transactional(readOnly = true)
    public TransactionHistoryResponse getHistory(
            UUID accountId,
            TransactionType type,
            Instant from,
            Instant to,
            Instant cursor,
            Integer pageSize,
            UUID callerId
    ) {
        Account account = accountPort.findById(accountId);
        assertOwnership(account, callerId, "History Account");

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessRuleException("'from' date must be before 'to' date");
        }

        Instant effectiveTo = to;
        Instant effectiveFrom = from;
        Instant effectiveCursor = cursor;
        if (effectiveFrom == null && effectiveTo == null) {
            effectiveFrom = Instant.now().minus(java.time.Duration.ofDays(30));
        }

        int effectivePageSize = (pageSize != null && pageSize > 0)
                ? Math.min(pageSize, MAX_PAGE_SIZE)
                : DEFAULT_PAGE_SIZE;

        List<TransactionResponse> items = txRepo
                .findHistory(accountId, type != null ? type.name() : null, effectiveFrom, effectiveTo, cursor, effectivePageSize)
                .stream()
                .map(txMapper::toDomain)
                .map(TransactionResponse::from)
                .toList();

        boolean hasMore = items.size() == effectivePageSize;
        Instant nextCursor = hasMore
                ? items.get(items.size() - 1).timestamp()  // timestamp of last item
                : null;

        return new TransactionHistoryResponse(items, nextCursor, hasMore);
    }


    /**
     * Returns 403 instead of 404 for existing accounts belonging to another user.
     */
    private void assertOwnership(Account account, UUID callerId, String context) {
        if (!account.isOwnedBy(callerId)) {
            log.warn("Ownership violation callerId={} accountId={} context={}",
                    callerId, account.getId(), context);
            throw new AccessDeniedException(
                   context + " does not belong to the authenticated user"
            );
        }
        if (!account.isActive()) {
            throw new ResourceNotFoundException("Account", account.getId());
        }
    }
}
