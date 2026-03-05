package com.test.financialtracker.transaction.service;

import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.repository.AccountPort;
import com.test.financialtracker.common.exception.BusinessRuleException;

import com.test.financialtracker.common.exception.DuplicateTransactionException;
import com.test.financialtracker.transaction.domains.entity.Transactions;
import com.test.financialtracker.transaction.domains.models.*;
import com.test.financialtracker.transaction.domains.tranformers.TransactionMapper;
import com.test.financialtracker.transaction.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
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
    @Mock
    TransactionMapper txMapper;

    @InjectMocks
    TransactionService service;

    // shared test fixtures
    UUID callerId  = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();
    UUID refId     = UUID.randomUUID();

    Account ownedAccount;
    Account foreignAccount;

    @BeforeEach
    void setUp() {
        ownedAccount = Account.builder()
                .id(accountId).userId(callerId)
                .balance(new BigDecimal("1000.00"))
                .currency("EUR").deleted(false).build();

        foreignAccount = Account.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID()) // different owner
                .balance(new BigDecimal("500.00"))
                .currency("EUR").deleted(false).build();
    }

    // ════════════════════════════════════════════════════════════════
    // DEPOSIT TESTS
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("deposit()")
    class Deposit {

        @Test
        @DisplayName("Happy path — credits account and creates ledger entry")
        void deposit_success() {
            DepositRequest req = new DepositRequest(accountId, new BigDecimal("200.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);

            Transactions entity = buildEntity(TransactionType.DEPOSIT, new BigDecimal("200.00"), null, accountId);
            Transaction domain = buildDomain(TransactionType.DEPOSIT, new BigDecimal("200.00"), null, accountId);
            when(txMapper.toEntity(any())).thenReturn(entity);
            when(txRepo.save(entity)).thenReturn(entity);
            when(txMapper.toDomain(entity)).thenReturn(domain);

            TransactionResponse result = service.deposit(req, callerId);

            assertThat(result.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(result.amount()).isEqualByComparingTo("200.00");
            assertThat(result.sourceAccountId()).isNull();           // DEPOSIT has no source
            assertThat(result.destinationAccountId()).isEqualTo(accountId);

            verify(accountPort).save(ownedAccount);                  // balance persisted
            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("1200.00"); // 1000 + 200
        }

        @Test
        @DisplayName("Duplicate key — throws DuplicateTransactionException with existing tx")
        void deposit_duplicateKey_throwsDuplicate() {
            DepositRequest req = new DepositRequest(accountId, new BigDecimal("200.00"), refId);

            Transactions entity = buildEntity(TransactionType.DEPOSIT, new BigDecimal("200.00"), null, accountId);
            Transaction domain = buildDomain(TransactionType.DEPOSIT, new BigDecimal("200.00"), null, accountId);
            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.of(entity));
            when(txMapper.toDomain(entity)).thenReturn(domain);

            DuplicateTransactionException ex = catchThrowableOfType(
                    () -> service.deposit(req, callerId),
                    DuplicateTransactionException.class
            );

            assertThat(ex.getExisting()).isNotNull();
            verifyNoInteractions(accountPort); // no account touched
        }

        @Test
        @DisplayName("Cross-user account — throws AccessDeniedException")
        void deposit_foreignAccount_throws403() {
            DepositRequest req = new DepositRequest(foreignAccount.getId(), new BigDecimal("100.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(foreignAccount.getId())).thenReturn(foreignAccount);

            assertThatThrownBy(() -> service.deposit(req, callerId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any()); // balance must NOT be changed
        }
    }

    // ════════════════════════════════════════════════════════════════
    // WITHDRAWAL TESTS
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("withdraw()")
    class Withdraw {

        @Test
        @DisplayName("Happy path — debits account and creates ledger entry")
        void withdraw_success() {
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("300.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);

            Transactions entity = buildEntity(TransactionType.WITHDRAWAL, new BigDecimal("300.00"), accountId, null);
            Transaction domain = buildDomain(TransactionType.WITHDRAWAL, new BigDecimal("300.00"), accountId, null);
            when(txMapper.toEntity(any())).thenReturn(entity);
            when(txRepo.save(entity)).thenReturn(entity);
            when(txMapper.toDomain(entity)).thenReturn(domain);

            TransactionResponse result = service.withdraw(req, callerId);

            assertThat(result.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(result.destinationAccountId()).isNull();     // WITHDRAWAL has no destination
            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("700.00"); // 1000 - 300
        }

        @Test
        @DisplayName("Insufficient funds — throws BusinessRuleException with 422")
        void withdraw_insufficientFunds_throws422() {
            // Try to withdraw more than balance
            WithdrawRequest req = new WithdrawRequest(accountId, new BigDecimal("9999.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(accountId)).thenReturn(ownedAccount);

            assertThatThrownBy(() -> service.withdraw(req, callerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Insufficient funds");

            verify(accountPort, never()).save(any()); // balance must NOT be persisted
            verify(txRepo, never()).save(any());       // ledger must NOT have entry
        }

        @Test
        @DisplayName("Cross-user account — throws AccessDeniedException")
        void withdraw_foreignAccount_throws403() {
            WithdrawRequest req = new WithdrawRequest(foreignAccount.getId(), new BigDecimal("100.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());
            when(accountPort.findByIdWithLock(foreignAccount.getId())).thenReturn(foreignAccount);

            assertThatThrownBy(() -> service.withdraw(req, callerId))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TRANSFER TESTS
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("transfer()")
    class Transfer {

        UUID destId = UUID.randomUUID();
        Account destAccount;

        @BeforeEach
        void setUpDest() {
            destAccount = Account.builder()
                    .id(destId).userId(callerId) // same owner
                    .balance(new BigDecimal("200.00"))
                    .currency("EUR").deleted(false).build();
        }

        @Test
        @DisplayName("Happy path — debits source, credits destination, single ledger entry")
        void transfer_success() {
            TransferRequest req = new TransferRequest(accountId, destId, new BigDecimal("400.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());

            // Mock consistent lock ordering
            UUID firstId  = accountId.compareTo(destId) < 0 ? accountId : destId;
            UUID secondId = firstId.equals(accountId) ? destId : accountId;
            when(accountPort.findByIdWithLock(firstId)).thenReturn(firstId.equals(accountId) ? ownedAccount : destAccount);
            when(accountPort.findByIdWithLock(secondId)).thenReturn(secondId.equals(accountId) ? ownedAccount : destAccount);

            Transactions entity = buildEntity(TransactionType.TRANSFER, new BigDecimal("400.00"), accountId, destId);
            Transaction domain = buildDomain(TransactionType.TRANSFER, new BigDecimal("400.00"), accountId, destId);
            when(txMapper.toEntity(any())).thenReturn(entity);
            when(txRepo.save(entity)).thenReturn(entity);
            when(txMapper.toDomain(entity)).thenReturn(domain);

            TransactionResponse result = service.transfer(req, callerId);

            assertThat(result.type()).isEqualTo(TransactionType.TRANSFER);
            assertThat(ownedAccount.getBalance()).isEqualByComparingTo("600.00");  // 1000 - 400
            assertThat(destAccount.getBalance()).isEqualByComparingTo("600.00");   // 200 + 400

            verify(accountPort, times(2)).save(any()); // both accounts saved
            verify(txRepo).save(any());                 // ONE ledger entry only
        }

        @Test
        @DisplayName("Self-transfer — throws BusinessRuleException")
        void transfer_sameAccount_throws() {
            TransferRequest req = new TransferRequest(accountId, accountId, new BigDecimal("100.00"), refId);

            assertThatThrownBy(() -> service.transfer(req, callerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("different");

            verifyNoInteractions(txRepo, accountPort);
        }

        @Test
        @DisplayName("Source not owned by caller — throws AccessDeniedException")
        void transfer_sourceNotOwned_throws403() {
            UUID foreignSrcId = foreignAccount.getId();
            TransferRequest req = new TransferRequest(foreignSrcId, destId, new BigDecimal("100.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());

            UUID firstId  = foreignSrcId.compareTo(destId) < 0 ? foreignSrcId : destId;
            UUID secondId = firstId.equals(foreignSrcId) ? destId : foreignSrcId;
            when(accountPort.findByIdWithLock(firstId))
                    .thenReturn(firstId.equals(foreignSrcId) ? foreignAccount : destAccount);
            when(accountPort.findByIdWithLock(secondId))
                    .thenReturn(secondId.equals(foreignSrcId) ? foreignAccount : destAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }

        @Test
        @DisplayName("Destination not owned by caller — throws AccessDeniedException")
        void transfer_destinationNotOwned_throws403() {
            UUID foreignDestId = foreignAccount.getId();
            TransferRequest req = new TransferRequest(accountId, foreignDestId, new BigDecimal("100.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());

            UUID firstId  = accountId.compareTo(foreignDestId) < 0 ? accountId : foreignDestId;
            UUID secondId = firstId.equals(accountId) ? foreignDestId : accountId;
            when(accountPort.findByIdWithLock(firstId))
                    .thenReturn(firstId.equals(accountId) ? ownedAccount : foreignAccount);
            when(accountPort.findByIdWithLock(secondId))
                    .thenReturn(secondId.equals(accountId) ? ownedAccount : foreignAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId))
                    .isInstanceOf(AccessDeniedException.class);

            verify(accountPort, never()).save(any());
        }

        @Test
        @DisplayName("Insufficient funds in source — throws BusinessRuleException")
        void transfer_insufficientFunds_throws422() {
            TransferRequest req = new TransferRequest(accountId, destId, new BigDecimal("9999.00"), refId);

            when(txRepo.findByReferenceId(refId)).thenReturn(Optional.empty());

            UUID firstId  = accountId.compareTo(destId) < 0 ? accountId : destId;
            UUID secondId = firstId.equals(accountId) ? destId : accountId;
            when(accountPort.findByIdWithLock(firstId)).thenReturn(firstId.equals(accountId) ? ownedAccount : destAccount);
            when(accountPort.findByIdWithLock(secondId)).thenReturn(secondId.equals(accountId) ? ownedAccount : destAccount);

            assertThatThrownBy(() -> service.transfer(req, callerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Insufficient funds");

            verify(accountPort, never()).save(any());
            verify(txRepo, never()).save(any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    // TEST HELPERS
    // ════════════════════════════════════════════════════════════════

    private Transactions buildEntity(TransactionType type, BigDecimal amount, UUID src, UUID dest) {
        return Transactions.builder()
                .id(UUID.randomUUID()).type(type).amount(amount)
                .sourceAccountId(src).destinationAccountId(dest)
                .timestamp(Instant.now()).referenceId(refId).build();
    }

    private Transaction buildDomain(TransactionType type, BigDecimal amount, UUID src, UUID dest) {
        return Transaction.builder()
                .id(UUID.randomUUID()).type(type).amount(amount)
                .sourceAccountId(src).destinationAccountId(dest)
                .timestamp(Instant.now()).referenceId(refId).build();
    }
}
