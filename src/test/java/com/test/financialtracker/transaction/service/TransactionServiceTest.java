package com.test.financialtracker.transaction.service;

import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.repository.AccountPort;
import com.test.financialtracker.common.exception.BusinessRuleException;
import com.test.financialtracker.common.exception.DuplicateTransactionException;
import com.test.financialtracker.common.exception.ResourceNotFoundException;
import com.test.financialtracker.transaction.domains.entity.Transactions;
import com.test.financialtracker.transaction.domains.models.*;
import com.test.financialtracker.transaction.domains.tranformers.TransactionMapper;
import com.test.financialtracker.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    AccountPort accountPort;
    @Mock
    TransactionRepository txRepo;

    // Real mapper — no external dependencies
    final TransactionMapper txMapper = new TransactionMapper();

    TransactionService service;

    UUID callerId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID refId = UUID.randomUUID();

    Account ownedAccount;
    Account foreignAccount;

    @BeforeEach
    void setUp() {
        service = new TransactionService(accountPort, txRepo, txMapper);

        ownedAccount = Account.builder()
                .id(accountId).userId(callerId)
                .balance(new BigDecimal("1000.00"))
                .currency("EUR").deletedAt(null).build();

        foreignAccount = Account.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .balance(new BigDecimal("500.00"))
                .currency("EUR").deletedAt(null).build();
    }

    // ════════════════════════════════════════════════════════════════
    // DEPOSIT
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deposit()")
    class Deposit {

        @Test
        @DisplayName("Happy path — credits account and persists a DEPOSIT ledger entry")
        void deposit_success() {
            DepositRequest req = new DepositRequest(accountId, new BigDecimal("200.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);
            when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransactionResponse result = service.deposit(req, callerId, refId);

            assertThat(result.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(result.amount()).isEqualByComparingTo("200.00");
            assertThat(result.sourceAccountId()).isNull();          // no source on DEPOSIT
            assertThat(result.destinationAccountId()).isEqualTo(accountId);
            assertThat(result.referenceId()).isEqualTo(refId);

            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("1200.00"); // 1000 + 200
            verify(accountPort).save(ownedAccount);
            verify(txRepo).save(any());
        }

        @Test
        @DisplayName("Duplicate idempotency key — throws DuplicateTransactionException with original tx")
        void deposit_duplicateKey_throwsDuplicate() {
            DepositRequest req = new DepositRequest(accountId, new BigDecimal("200.00"));

            Transactions entity = buildEntity(TransactionType.DEPOSIT, new BigDecimal("200.00"), null, accountId);
            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.of(entity));

            DuplicateTransactionException ex = catchThrowableOfType(
                    DuplicateTransactionException.class,
                    () -> service.deposit(req, callerId, refId)
            );

            assertThat(ex.getExisting()).isNotNull();
            assertThat(ex.getExisting().getReferenceId()).isEqualTo(refId);
            verifyNoInteractions(accountPort); // account must not be touched
            verify(txRepo, never()).save(any());
        }

        @Test
        @DisplayName("Account belongs to another user — throws AccessDeniedException")
        void deposit_foreignAccount_throws403() {
            DepositRequest req = new DepositRequest(foreignAccount.getId(), new BigDecimal("100.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(foreignAccount.getId())).thenReturn(foreignAccount);

            assertThatThrownBy(() -> service.deposit(req, callerId, refId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }

        @Test
        @DisplayName("Soft-deleted account — throws ResourceNotFoundException")
        void deposit_deletedAccount_throws404() {
            Account deletedAccount = Account.builder()
                    .id(accountId).userId(callerId)
                    .balance(BigDecimal.ZERO).currency("EUR")
                    .deletedAt(Instant.now()).build();

            DepositRequest req = new DepositRequest(accountId, new BigDecimal("100.00"));
            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(deletedAccount);

            assertThatThrownBy(() -> service.deposit(req, callerId, refId))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(accountPort, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WITHDRAW
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withdraw()")
    class Withdraw {

        @Test
        @DisplayName("Happy path — debits account and persists a WITHDRAWAL ledger entry")
        void withdraw_success() {
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("300.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);
            when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransactionResponse result = service.withdraw(req, callerId, refId);

            assertThat(result.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(result.amount()).isEqualByComparingTo("300.00");
            assertThat(result.sourceAccountId()).isEqualTo(accountId);
            assertThat(result.destinationAccountId()).isNull();     // no destination on WITHDRAWAL

            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("700.00"); // 1000 - 300
            verify(accountPort).save(ownedAccount);
        }

        @Test
        @DisplayName("Insufficient funds — throws BusinessRuleException, no state mutated")
        void withdraw_insufficientFunds_throws422() {
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("9999.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);

            assertThatThrownBy(() -> service.withdraw(req, callerId, refId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Insufficient funds");

            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("1000.00"); // unchanged
            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }

        @Test
        @DisplayName("Duplicate idempotency key — throws DuplicateTransactionException")
        void withdraw_duplicateKey_throwsDuplicate() {
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("100.00"));

            Transactions entity = buildEntity(TransactionType.WITHDRAWAL, new BigDecimal("100.00"), accountId, null);
            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.of(entity));

            DuplicateTransactionException ex = catchThrowableOfType(
                    DuplicateTransactionException.class,
                    () -> service.withdraw(req, callerId, refId)
            );

            assertThat(ex.getExisting().getType()).isEqualTo(TransactionType.WITHDRAWAL);
            verifyNoInteractions(accountPort);
        }

        @Test
        @DisplayName("Account belongs to another user — throws AccessDeniedException")
        void withdraw_foreignAccount_throws403() {
            WithdrawRequest req = new WithdrawRequest(foreignAccount.getId(), new BigDecimal("100.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(foreignAccount.getId())).thenReturn(foreignAccount);

            assertThatThrownBy(() -> service.withdraw(req, callerId, refId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
        }

        @Test
        @DisplayName("Exact-balance withdrawal succeeds (edge: balance goes to exactly zero)")
        void withdraw_exactBalance_succeeds() {
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("1000.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);
            when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.withdraw(req, callerId, refId);

            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("0.00");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TRANSFER
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("transfer()")
    class Transfer {

        UUID destId = UUID.randomUUID();
        Account destAccount;

        @BeforeEach
        void setUpDest() {
            destAccount = Account.builder()
                    .id(destId).userId(callerId)  // same owner
                    .balance(new BigDecimal("200.00"))
                    .currency("EUR").deletedAt(null).build();
        }

        @Test
        @DisplayName("Happy path — debits source, credits destination, one ledger entry")
        void transfer_success() {
            TransferRequest req = new TransferRequest(accountId, destId, new BigDecimal("400.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            stubSourceDestinationAccountLocks(accountId, destId, ownedAccount, destAccount);
            when(txRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

            TransactionResponse result = service.transfer(req, callerId, refId);

            assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
            assertThat(result.sourceAccountId()).isEqualTo(accountId);
            assertThat(result.destinationAccountId()).isEqualTo(destId);

            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("600.00");  // 1000 - 400
            assertThat(destAccount.getBalance()).isEqualByComparingTo("600.00");   // 200 + 400

            verify(accountPort, times(2)).save(any());
            verify(txRepo, times(1)).save(any());  // single ledger entry
        }

        @Test
        @DisplayName("Self-transfer — throws BusinessRuleException before any repo access")
        void transfer_sameAccount_throws() {
            TransferRequest req = new TransferRequest(accountId, accountId, new BigDecimal("100.00"));

            assertThatThrownBy(() -> service.transfer(req, callerId, refId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("different");

            verifyNoInteractions(txRepo, accountPort);
        }

        @Test
        @DisplayName("Duplicate idempotency key — throws DuplicateTransactionException")
        void transfer_duplicateKey_throwsDuplicate() {
            TransferRequest req = new TransferRequest(accountId, destId, new BigDecimal("100.00"));

            Transactions entity = buildEntity(TransactionType.TRANSFER, new BigDecimal("100.00"), accountId, destId);
            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.of(entity));

            DuplicateTransactionException ex = catchThrowableOfType(
                    DuplicateTransactionException.class,
                    () -> service.transfer(req, callerId, refId)
            );

            assertThat(ex.getExisting().getType()).isEqualTo(TransactionType.TRANSFER);
            verifyNoInteractions(accountPort);
        }

        @Test
        @DisplayName("Source account not owned by caller — throws AccessDeniedException")
        void transfer_sourceNotOwned_throws403() {
            TransferRequest req = new TransferRequest(foreignAccount.getId(), destId, new BigDecimal("100.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            stubSourceDestinationAccountLocks(foreignAccount.getId(), destId, foreignAccount, destAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId, refId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }

        @Test
        @DisplayName("Destination account not owned by caller — throws AccessDeniedException")
        void transfer_destinationNotOwned_throws403() {
            TransferRequest req = new TransferRequest(accountId, foreignAccount.getId(), new BigDecimal("100.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            stubSourceDestinationAccountLocks(accountId, foreignAccount.getId(), ownedAccount, foreignAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId, refId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
        }

        @Test
        @DisplayName("Insufficient funds in source — throws BusinessRuleException")
        void transfer_insufficientFunds_throws422() {
            TransferRequest req = new TransferRequest(accountId, destId, new BigDecimal("9999.00"));

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            stubSourceDestinationAccountLocks(accountId, destId, ownedAccount, destAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId, refId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Insufficient funds");

            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }

        private void stubSourceDestinationAccountLocks(UUID srcId, UUID dstId, Account srcAccount, Account dstAccount) {
            when(accountPort.findByIdWithLock(srcId)).thenReturn(srcAccount);
            when(accountPort.findByIdWithLock(dstId)).thenReturn(dstAccount);

        }
    }

    // ════════════════════════════════════════════════════════════════
    // GET HISTORY
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getHistory()")
    class GetHistory {

        @Test
        @DisplayName("Happy path — returns mapped history items for account owner")
        void getHistory_success() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");
            Instant to = Instant.parse("2024-12-31T23:59:59Z");

            Transactions entity = buildEntity(TransactionType.DEPOSIT, new BigDecimal("100.00"), null, accountId);
            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(accountId, null, from, to, null, 20)).thenReturn(List.of(entity));

            TransactionHistoryResponse result = service.getHistory(accountId, null, from, to, null, 20, callerId);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().getFirst().type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("Caller is not owner — throws AccessDeniedException")
        void getHistory_notOwner_throws403() {
            UUID strangerId = UUID.randomUUID();
            when(accountPort.findById(accountId)).thenReturn(ownedAccount);

            assertThatThrownBy(() -> service.getHistory(accountId, null, null, null, null, null, strangerId))
                    .isInstanceOf(AccessDeniedException.class);

            verifyNoInteractions(txRepo);
        }

        @Test
        @DisplayName("Soft-deleted account — throws ResourceNotFoundException")
        void getHistory_deletedAccount_throws404() {
            Account deletedAccount = Account.builder()
                    .id(accountId).userId(callerId)
                    .balance(BigDecimal.ZERO).currency("EUR")
                    .deletedAt(Instant.now()).build();

            when(accountPort.findById(accountId)).thenReturn(deletedAccount);

            assertThatThrownBy(() -> service.getHistory(accountId, null, null, null, null, null, callerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("from is after to — throws BusinessRuleException")
        void getHistory_invalidDateRange_throws() {
            Instant from = Instant.parse("2024-12-31T00:00:00Z");
            Instant to = Instant.parse("2024-01-01T00:00:00Z"); // before from

            when(accountPort.findById(accountId)).thenReturn(ownedAccount);

            assertThatThrownBy(() -> service.getHistory(accountId, null, from, to, null, null, callerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("from");
        }

        @Test
        @DisplayName("No date range provided — defaults to the last 30 days")
        void getHistory_noDateRange_defaultsTo30Days() {
            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(eq(accountId), isNull(), any(Instant.class), isNull(), isNull(), eq(20)))
                    .thenReturn(List.of());

            Instant before = Instant.now().minus(Duration.ofDays(30)).minusSeconds(2);

            service.getHistory(accountId, null, null, null, null, null, callerId);

            ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
            verify(txRepo).findHistory(eq(accountId), isNull(), fromCaptor.capture(), isNull(), isNull(), eq(20));

            Instant capturedFrom = fromCaptor.getValue();
            Instant after = Instant.now().minus(Duration.ofDays(30)).plusSeconds(2);
            assertThat(capturedFrom).isAfter(before).isBefore(after);
        }

        @Test
        @DisplayName("pageSize null — falls back to default of 20")
        void getHistory_pageSizeNull_defaultsTo20() {
            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(eq(accountId), isNull(), any(), isNull(), isNull(), eq(20)))
                    .thenReturn(List.of());

            service.getHistory(accountId, null, null, null, null, null, callerId);

            verify(txRepo).findHistory(eq(accountId), isNull(), any(), isNull(), isNull(), eq(20));
        }

        @Test
        @DisplayName("pageSize above 100 — clamped to MAX of 100")
        void getHistory_pageSizeTooLarge_clampedTo100() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");
            Instant to = Instant.parse("2024-12-31T23:59:59Z");

            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(eq(accountId), isNull(), eq(from), eq(to), isNull(), eq(100)))
                    .thenReturn(List.of());

            service.getHistory(accountId, null, from, to, null, 500, callerId);

            verify(txRepo).findHistory(eq(accountId), isNull(), eq(from), eq(to), isNull(), eq(100));
        }

        @Test
        @DisplayName("hasMore is true when result count equals pageSize; nextCursor is set")
        void getHistory_fullPage_hasMorAndCursor() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");
            Instant to = Instant.parse("2024-12-31T23:59:59Z");

            Transactions t1 = buildEntity(TransactionType.DEPOSIT, new BigDecimal("50.00"), null, accountId);
            Transactions t2 = buildEntity(TransactionType.WITHDRAWAL, new BigDecimal("30.00"), accountId, null);

            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(accountId, null, from, to, null, 2)).thenReturn(List.of(t1, t2));

            TransactionHistoryResponse result = service.getHistory(accountId, null, from, to, null, 2, callerId);

            assertThat(result.hasMore()).isTrue();
            assertThat(result.nextCursor()).isNotNull(); // timestamp of last item
        }

        @Test
        @DisplayName("Partial page — hasMore is false and nextCursor is null")
        void getHistory_partialPage_hasMoreFalse() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");

            Transactions t1 = buildEntity(TransactionType.DEPOSIT, new BigDecimal("50.00"), null, accountId);
            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(accountId, null, from, null, null, 5)).thenReturn(List.of(t1));

            TransactionHistoryResponse result = service.getHistory(accountId, null, from, null, null, 5, callerId);

            assertThat(result.hasMore()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("Type filter — passes enum name as string to the repository")
        void getHistory_typeFilter_passedAsString() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");

            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(accountId, "DEPOSIT", from, null, null, 20)).thenReturn(List.of());

            service.getHistory(accountId, TransactionType.DEPOSIT, from, null, null, null, callerId);

            verify(txRepo).findHistory(accountId, "DEPOSIT", from, null, null, 20);
        }

        @Test
        @DisplayName("Null type — passes null to repository (no type filter)")
        void getHistory_nullType_passesNullToRepo() {
            Instant from = Instant.parse("2024-01-01T00:00:00Z");

            when(accountPort.findById(accountId)).thenReturn(ownedAccount);
            when(txRepo.findHistory(accountId, null, from, null, null, 20)).thenReturn(List.of());

            service.getHistory(accountId, null, from, null, null, null, callerId);

            verify(txRepo).findHistory(accountId, null, from, null, null, 20);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════

    private Transactions buildEntity(TransactionType type, BigDecimal amount, UUID src, UUID dest) {
        return Transactions.builder()
                .id(UUID.randomUUID()).type(type).amount(amount)
                .sourceAccountId(src).destinationAccountId(dest)
                .timestamp(Instant.now()).referenceId(refId).build();
    }
}
