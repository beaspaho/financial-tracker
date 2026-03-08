package com.test.financialtracker.account.service;

import com.test.financialtracker.account.domain.entity.Accounts;
import com.test.financialtracker.account.domain.models.AdminAccountResponse;
import com.test.financialtracker.account.domain.tranformers.AccountMapper;
import com.test.financialtracker.account.repository.AccountRepository;
import com.test.financialtracker.common.exception.ResourceNotFoundException;
import com.test.financialtracker.common.wrapper.PagedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock AccountRepository accountRepository;

    // Real mapper — mapping logic (including PII masking in AdminAccountResponse) is tested here
    final AccountMapper accountMapper = new AccountMapper();

    AdminService service;

    UUID userId1    = UUID.randomUUID();
    UUID userId2    = UUID.randomUUID();
    UUID accountId1 = UUID.randomUUID();
    UUID accountId2 = UUID.randomUUID();

    Accounts activeEntity;
    Accounts deletedEntity;

    @BeforeEach
    void setUp() {
        service = new AdminService(accountRepository, accountMapper);

        activeEntity = Accounts.builder()
                .id(accountId1).userId(userId1)
                .name("Active Account").balance(new BigDecimal("1500.00"))
                .currency("EUR").build();

        deletedEntity = Accounts.builder()
                .id(accountId2).userId(userId2)
                .name("Closed Account").balance(BigDecimal.ZERO)
                .currency("USD").deletedAt(Instant.now()).build();
    }

    // ════════════════════════════════════════════════════════════════
    // listAllAccounts()
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("listAllAccounts()")
    class ListAllAccounts {

        @Test
        @DisplayName("Happy path — returns items, correct page metadata, and masked PII")
        void listAllAccounts_success() {
            when(accountRepository.countAllForAdmin()).thenReturn(2L);
            when(accountRepository.findAllForAdmin(20, 0, "ALL"))
                    .thenReturn(List.of(activeEntity, deletedEntity));

            PagedResponse<AdminAccountResponse> result = service.listAllAccounts(0, 20, "ALL");

            assertThat(result.items()).hasSize(2);
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.pageSize()).isEqualTo(20);
            assertThat(result.totalItems()).isEqualTo(2);
        }

        @Test
        @DisplayName("Page size above MAX (100) is clamped to 100")
        void listAllAccounts_pageSizeClamped_toMax() {
            when(accountRepository.countAllForAdmin()).thenReturn(1L);
            when(accountRepository.findAllForAdmin(100, 0, "ALL")).thenReturn(List.of(activeEntity));

            service.listAllAccounts(0, 999, "ALL");

            verify(accountRepository).findAllForAdmin(100, 0, "ALL");
        }

        @Test
        @DisplayName("Page size of 0 is clamped to minimum of 1")
        void listAllAccounts_pageSizeClamped_toMin() {
            when(accountRepository.countAllForAdmin()).thenReturn(1L);
            when(accountRepository.findAllForAdmin(1, 0, "ALL")).thenReturn(List.of(activeEntity));

            service.listAllAccounts(0, 0, "ALL");

            verify(accountRepository).findAllForAdmin(1, 0, "ALL");
        }

        @Test
        @DisplayName("Negative page size is clamped to minimum of 1")
        void listAllAccounts_negativePageSize_clamped() {
            when(accountRepository.countAllForAdmin()).thenReturn(1L);
            when(accountRepository.findAllForAdmin(1, 0, "ALL")).thenReturn(List.of(activeEntity));

            service.listAllAccounts(0, -10, "ALL");

            verify(accountRepository).findAllForAdmin(1, 0, "ALL");
        }

        @Test
        @DisplayName("Offset is correctly computed as page * effectiveSize")
        void listAllAccounts_offsetIsPageTimesSize() {
            when(accountRepository.countAllForAdmin()).thenReturn(100L);
            when(accountRepository.findAllForAdmin(10, 20, "ALL")).thenReturn(List.of(activeEntity));

            // page=2, size=10  →  offset = 2 * 10 = 20
            service.listAllAccounts(2, 10, "ALL");

            verify(accountRepository).findAllForAdmin(10, 20, "ALL");
        }

        @Test
        @DisplayName("hasNext is true when more pages exist, false on the last page")
        void listAllAccounts_hasNextFlag() {
            when(accountRepository.countAllForAdmin()).thenReturn(25L);
            when(accountRepository.findAllForAdmin(10, 0, "ALL")).thenReturn(List.of(activeEntity));

            PagedResponse<AdminAccountResponse> page0 = service.listAllAccounts(0, 10, "ALL");
            assertThat(page0.hasNext()).isTrue();       // 3 pages total → page 0 has next
            assertThat(page0.hasPrevious()).isFalse();  // page 0 has no previous
        }

        @Test
        @DisplayName("hasPrevious is true for any page beyond the first")
        void listAllAccounts_hasPreviousFlag() {
            when(accountRepository.countAllForAdmin()).thenReturn(25L);
            when(accountRepository.findAllForAdmin(10, 10, "ALL")).thenReturn(List.of(activeEntity));

            PagedResponse<AdminAccountResponse> page1 = service.listAllAccounts(1, 10, "ALL");
            assertThat(page1.hasPrevious()).isTrue();
        }

        @Test
        @DisplayName("Empty result set — items list is empty but wrapper is well-formed")
        void listAllAccounts_emptyResult() {
            when(accountRepository.countAllForAdmin()).thenReturn(0L);
            when(accountRepository.findAllForAdmin(20, 0, "ALL")).thenReturn(Collections.emptyList());

            PagedResponse<AdminAccountResponse> result = service.listAllAccounts(0, 20, "ALL");

            assertThat(result.items()).isEmpty();
            assertThat(result.totalItems()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.hasPrevious()).isFalse();
        }

        @Test
        @DisplayName("filterAccount parameter is passed through unchanged to the repository")
        void listAllAccounts_filterPropagated() {
            when(accountRepository.countAllForAdmin()).thenReturn(1L);
            when(accountRepository.findAllForAdmin(20, 0, "ACTIVE")).thenReturn(List.of(activeEntity));

            service.listAllAccounts(0, 20, "ACTIVE");

            verify(accountRepository).findAllForAdmin(20, 0, "ACTIVE");
        }

        @Test
        @DisplayName("Soft-deleted accounts are included and correctly marked as inactive")
        void listAllAccounts_includesDeletedAccountsAsInactive() {
            when(accountRepository.countAllForAdmin()).thenReturn(1L);
            when(accountRepository.findAllForAdmin(20, 0, "ALL")).thenReturn(List.of(deletedEntity));

            PagedResponse<AdminAccountResponse> result = service.listAllAccounts(0, 20, "ALL");

            assertThat(result.items().get(0).active()).isFalse();
        }
    }

    // ════════════════════════════════════════════════════════════════
    // getAccountById()
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("getAccountById()")
    class GetAccountById {

        @Test
        @DisplayName("Happy path — returns AdminAccountResponse for existing account")
        void getAccountById_success() {
            when(accountRepository.findById(accountId1)).thenReturn(Optional.of(activeEntity));

            AdminAccountResponse result = service.getAccountById(accountId1);

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("Active Account");
            assertThat(result.balance()).isEqualByComparingTo("1500.00");
            assertThat(result.currency()).isEqualTo("EUR");
            assertThat(result.active()).isTrue();
        }

        @Test
        @DisplayName("Account ID in response is masked (first 8 chars + '-****')")
        void getAccountById_accountIdIsMasked() {
            when(accountRepository.findById(accountId1)).thenReturn(Optional.of(activeEntity));

            AdminAccountResponse result = service.getAccountById(accountId1);

            String expectedMasked = accountId1.toString().substring(0, 8) + "-****";
            assertThat(result.accountId()).isEqualTo(expectedMasked);
        }

        @Test
        @DisplayName("User ID in response is masked (first 8 chars + '-****')")
        void getAccountById_userIdIsMasked() {
            when(accountRepository.findById(accountId1)).thenReturn(Optional.of(activeEntity));

            AdminAccountResponse result = service.getAccountById(accountId1);

            String expectedMasked = userId1.toString().substring(0, 8) + "-****";
            assertThat(result.userId()).isEqualTo(expectedMasked);
        }

        @Test
        @DisplayName("Full UUIDs are never exposed in the response")
        void getAccountById_fullUuidsNotExposed() {
            when(accountRepository.findById(accountId1)).thenReturn(Optional.of(activeEntity));

            AdminAccountResponse result = service.getAccountById(accountId1);

            assertThat(result.accountId()).doesNotContain(accountId1.toString());
            assertThat(result.userId()).doesNotContain(userId1.toString());
        }

        @Test
        @DisplayName("Soft-deleted account is returned but marked as inactive")
        void getAccountById_deletedAccount_returnedAsInactive() {
            when(accountRepository.findById(accountId2)).thenReturn(Optional.of(deletedEntity));

            AdminAccountResponse result = service.getAccountById(accountId2);

            assertThat(result.active()).isFalse();
        }

        @Test
        @DisplayName("Account not found — throws ResourceNotFoundException")
        void getAccountById_notFound_throws404() {
            UUID unknown = UUID.randomUUID();
            when(accountRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAccountById(unknown))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Account");
        }
    }

    // ════════════════════════════════════════════════════════════════
    // listAccountsByUser()
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("listAccountsByUser()")
    class ListAccountsByUser {

        @Test
        @DisplayName("Happy path — returns all active accounts for the given userId")
        void listAccountsByUser_success() {
            Accounts second = Accounts.builder()
                    .id(UUID.randomUUID()).userId(userId1)
                    .name("Checking").balance(new BigDecimal("200.00"))
                    .currency("EUR").build();

            when(accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId1))
                    .thenReturn(List.of(activeEntity, second));

            List<AdminAccountResponse> result = service.listAccountsByUser(userId1);

            assertThat(result).hasSize(2);
            assertThat(result).extracting(AdminAccountResponse::name)
                    .containsExactly("Active Account", "Checking");
        }

        @Test
        @DisplayName("Results respect insertion order (ordered by createdAt ASC)")
        void listAccountsByUser_preservesOrder() {
            Accounts older = Accounts.builder()
                    .id(UUID.randomUUID()).userId(userId1)
                    .name("First").balance(BigDecimal.ZERO).currency("EUR").build();
            Accounts newer = Accounts.builder()
                    .id(UUID.randomUUID()).userId(userId1)
                    .name("Second").balance(BigDecimal.ZERO).currency("EUR").build();

            when(accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId1))
                    .thenReturn(List.of(older, newer));

            List<AdminAccountResponse> result = service.listAccountsByUser(userId1);

            assertThat(result).extracting(AdminAccountResponse::name)
                    .containsExactly("First", "Second");
        }

        @Test
        @DisplayName("User ID in each result is masked for PII compliance")
        void listAccountsByUser_piiMasked() {
            when(accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId1))
                    .thenReturn(List.of(activeEntity));

            List<AdminAccountResponse> result = service.listAccountsByUser(userId1);

            String expectedMasked = userId1.toString().substring(0, 8) + "-****";
            assertThat(result.get(0).userId()).isEqualTo(expectedMasked);
        }

        @Test
        @DisplayName("No accounts found for user — returns empty list without throwing")
        void listAccountsByUser_noAccounts_returnsEmptyList() {
            when(accountRepository.findAllByUserIdOrderByCreatedAtAsc(userId1))
                    .thenReturn(Collections.emptyList());

            List<AdminAccountResponse> result = service.listAccountsByUser(userId1);

            assertThat(result).isEmpty();
        }
    }
}
