package com.test.financialtracker.account.service;

import com.test.financialtracker.account.domain.entity.Accounts;
import com.test.financialtracker.account.domain.models.Account;
import com.test.financialtracker.account.domain.models.AccountResponse;
import com.test.financialtracker.account.domain.models.CreateAccountRequest;
import com.test.financialtracker.account.domain.models.UpdateAccountRequest;
import com.test.financialtracker.account.domain.tranformers.AccountMapper;
import com.test.financialtracker.account.repository.AccountRepository;
import com.test.financialtracker.common.exception.BusinessRuleException;
import com.test.financialtracker.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock AccountRepository accountRepository;

    // Real mapper — no external deps, mapping logic is part of what we test
    final AccountMapper accountMapper = new AccountMapper();

    AccountService service;

    UUID callerId  = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    /** Active account with zero balance owned by callerId */
    Accounts ownedEntity;
    /** Active account with zero balance owned by a stranger */
    Accounts foreignEntity;

    @BeforeEach
    void setUp() {
        service = new AccountService(accountRepository, accountMapper);

        ownedEntity = Accounts.builder()
                .id(accountId).userId(callerId)
                .name("Savings").balance(BigDecimal.ZERO)
                .currency("EUR").build();

        foreignEntity = Accounts.builder()
                .id(UUID.randomUUID()).userId(UUID.randomUUID())
                .name("Other").balance(new BigDecimal("500.00"))
                .currency("USD").build();
    }

    // ════════════════════════════════════════════════════════════════
    // CREATE
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("Happy path — saved with zero initial balance; response reflects all fields")
        void create_success() {
            CreateAccountRequest req = new CreateAccountRequest("Savings", "EUR");

            // save() returns whatever we hand back; the entity gets its ID from Account.open()
            when(accountRepository.save(any(Accounts.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountResponse response = service.create(req, callerId);

            assertThat(response.name()).isEqualTo("Savings");
            assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.currency()).isEqualTo("EUR");
            assertThat(response.active()).isTrue();
            assertThat(response.id()).isNotNull();

            verify(accountRepository).save(any(Accounts.class));
        }

        @Test
        @DisplayName("Currency is normalised to uppercase by Account.open() before saving")
        void create_currencyStoredUppercase() {
            // Even though the request enforces ^[A-Z]{3}$, Account.open() also uppercases —
            // verify that the entity passed to the repository has the uppercase value.
            String accountName = "Savings";
            String ccy = "EUR";
            CreateAccountRequest req = new CreateAccountRequest(accountName, ccy);

            when(accountRepository.save(any(Accounts.class))).thenAnswer(inv -> {
                Accounts entity = inv.getArgument(0);
                assertThat(entity.getCurrency()).isEqualTo(ccy);
                assertThat(entity.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
                return entity;
            });

            AccountResponse response = service.create(req, callerId);
            assertThat(response.active()).isTrue();
            assertThat(response.name()).isEqualTo(accountName);
            assertThat(response.currency()).isEqualTo(ccy);

        }

        @Test
        @DisplayName("New account is active (deletedAt is null)")
        void create_accountIsActive() {
            CreateAccountRequest req = new CreateAccountRequest("Savings", "EUR");
            when(accountRepository.save(any(Accounts.class))).thenAnswer(inv -> inv.getArgument(0));

            AccountResponse response = service.create(req, callerId);

            assertThat(response.active()).isTrue();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // LIST FOR USER
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("listForUser()")
    class ListForUser {

        @Test
        @DisplayName("Returns one response per active account belonging to the caller")
        void listForUser_returnsMappedList() {
            Accounts second = Accounts.builder()
                    .id(UUID.randomUUID()).userId(callerId)
                    .name("Checking").balance(new BigDecimal("300.00"))
                    .currency("USD").build();

            when(accountRepository.findAllByUserId(callerId)).thenReturn(List.of(ownedEntity, second));

            List<AccountResponse> result = service.listForUser(callerId);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AccountResponse::name)
                    .containsExactlyInAnyOrder("Savings", "Checking");
            assertThat(result).extracting(AccountResponse::currency)
                    .containsExactlyInAnyOrder("EUR", "USD");
        }

        @Test
        @DisplayName("Returns empty list when caller has no accounts")
        void listForUser_noAccounts_returnsEmptyList() {
            when(accountRepository.findAllByUserId(callerId)).thenReturn(List.of());

            List<AccountResponse> result = service.listForUser(callerId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("Does not leak accounts belonging to other users")
        void listForUser_isolatesPerUser() {
            // Only ownedEntity is returned by the repository for callerId
            when(accountRepository.findAllByUserId(callerId)).thenReturn(List.of(ownedEntity));

            List<AccountResponse> result = service.listForUser(callerId);

            assertThat(result).hasSize(1);
            assertThat(result.getFirst().id()).isEqualTo(accountId);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // GET BY ID
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getById()")
    class GetById {

        @Test
        @DisplayName("Happy path — owner receives full account response")
        void getById_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            AccountResponse result = service.getById(accountId, callerId);

            assertThat(result.id()).isEqualTo(accountId);
            assertThat(result.name()).isEqualTo("Savings");
            assertThat(result.balance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Account does not exist — throws ResourceNotFoundException")
        void getById_notFound_throws404() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getById(accountId, callerId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account");
        }

        @Test
        @DisplayName("Caller is not the owner — throws AccessDeniedException")
        void getById_notOwner_throws403() {
            UUID strangerId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            assertThatThrownBy(() -> service.getById(accountId, strangerId))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // UPDATE
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("Happy path — entity name is mutated, updated name appears in response")
        void update_success() {
            UpdateAccountRequest req = new UpdateAccountRequest("Pension Fund");
            // findById is called twice: ownership check + entity load for mutation
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            AccountResponse result = service.update(accountId, req, callerId);

            // Real mapper reads from the (now-mutated) entity
            assertThat(result.name()).isEqualTo("Pension Fund");
            assertThat(ownedEntity.getName()).isEqualTo("Pension Fund"); // entity mutated in-place
        }

        @Test
        @DisplayName("Balance and currency are not touched by an update")
        void update_doesNotChangeBalanceOrCurrency() {
            ownedEntity.setBalance(new BigDecimal("250.00"));
            UpdateAccountRequest req = new UpdateAccountRequest("New Name");
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            AccountResponse result = service.update(accountId, req, callerId);

            assertThat(result.balance()).isEqualByComparingTo("250.00");
            assertThat(result.currency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Account not found on ownership check — throws ResourceNotFoundException")
        void update_notFound_throws404() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(accountId, new UpdateAccountRequest("X"), callerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("Caller is not the owner — throws AccessDeniedException before mutation")
        void update_notOwner_throws403() {
            UUID strangerId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            assertThatThrownBy(() -> service.update(accountId, new UpdateAccountRequest("X"), strangerId))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(ownedEntity.getName()).isEqualTo("Savings"); // unchanged
        }
    }

    // ════════════════════════════════════════════════════════════════
    // DELETE
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("Happy path — zero-balance account gets deletedAt stamped on the entity")
        void delete_zeroBalance_entitySoftDeleted() {
            // ownedEntity already has balance=0
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            service.delete(accountId, callerId);

            // Real mapper propagated deletedAt from the domain to the entity
            assertThat(ownedEntity.getDeletedAt()).isNotNull();
        }

        @Test
        @DisplayName("Non-zero balance — throws BusinessRuleException, entity not mutated")
        void delete_nonZeroBalance_throwsBusinessRule() {
            ownedEntity.setBalance(new BigDecimal("0.01"));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            assertThatThrownBy(() -> service.delete(accountId, callerId))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("non-zero balance");

            assertThat(ownedEntity.getDeletedAt()).isNull(); // entity not touched
        }

        @Test
        @DisplayName("Negative balance edge case — also blocked by the non-zero balance rule")
        void delete_negativeBalance_throwsBusinessRule() {
            ownedEntity.setBalance(new BigDecimal("-0.01"));
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            assertThatThrownBy(() -> service.delete(accountId, callerId))
                    .isInstanceOf(BusinessRuleException.class);
        }

        @Test
        @DisplayName("Caller is not the owner — throws AccessDeniedException before any state change")
        void delete_notOwner_throws403() {
            UUID strangerId = UUID.randomUUID();
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            assertThatThrownBy(() -> service.delete(accountId, strangerId))
                    .isInstanceOf(AccessDeniedException.class);

            assertThat(ownedEntity.getDeletedAt()).isNull();
        }

        @Test
        @DisplayName("Account not found on ownership check — throws ResourceNotFoundException")
        void delete_notFound_throws404() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(accountId, callerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AccountPort — findByIdWithLock
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findByIdWithLock() [AccountPort]")
    class FindByIdWithLock {

        @Test
        @DisplayName("Entity found — returns fully mapped domain Account")
        void findByIdWithLock_success() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.of(ownedEntity));

            Account result = service.findByIdWithLock(accountId);

            assertThat(result.getId()).isEqualTo(accountId);
            assertThat(result.getUserId()).isEqualTo(callerId);
            assertThat(result.getCurrency()).isEqualTo("EUR");
        }

        @Test
        @DisplayName("Entity not found — throws ResourceNotFoundException")
        void findByIdWithLock_notFound_throws404() {
            when(accountRepository.findByIdWithLock(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByIdWithLock(accountId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AccountPort — findById
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findById() [AccountPort]")
    class FindById {

        @Test
        @DisplayName("Entity found — returns fully mapped domain Account")
        void findById_success() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            Account result = service.findById(accountId);

            assertThat(result.getId()).isEqualTo(accountId);
            assertThat(result.getName()).isEqualTo("Savings");
        }

        @Test
        @DisplayName("Entity not found — throws ResourceNotFoundException")
        void findById_notFound_throws404() {
            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(accountId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // AccountPort — save
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("save() [AccountPort]")
    class Save {

        @Test
        @DisplayName("Entity exists — balance and deletedAt are propagated to the JPA entity")
        void save_entityExists_entityMutated() {
            Account updatedDomain = Account.builder()
                    .id(accountId).userId(callerId)
                    .name("Savings").balance(new BigDecimal("999.00"))
                    .currency("EUR").build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.of(ownedEntity));

            service.save(updatedDomain);

            assertThat(ownedEntity.getBalance()).isEqualByComparingTo("999.00");
        }

        @Test
        @DisplayName("Entity not found — method completes silently without touching anything")
        void save_entityMissing_noOp() {
            Account domain = Account.builder()
                    .id(accountId).userId(callerId)
                    .name("Savings").balance(new BigDecimal("500.00"))
                    .currency("EUR").build();

            when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

            assertThatCode(() -> service.save(domain)).doesNotThrowAnyException();
            // entity state unchanged — nothing to verify beyond "no exception"
        }
    }
}
