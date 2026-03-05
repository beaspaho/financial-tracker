package com.test.financialtracker.account.ports;


import com.test.financialtracker.account.domain.models.AdminAccountResponse;
import com.test.financialtracker.account.service.AdminService;
import com.test.financialtracker.common.wrapper.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**

 * ALL OPERATIONS ARE READ-ONLY.
 *
 * ENDPOINTS:
 *   GET /api/v1/admin/accounts               — paginated list of all accounts
 *   GET /api/v1/admin/accounts/{id}          — single account detail
 *   GET /api/v1/admin/accounts?userId={id}   — accounts by user
 */
@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * GET /api/v1/admin/accounts?page=0&pageSize=20
     *
     * Returns all accounts (active + soft-deleted) with masked PII.
     * Offset-based pagination — supports jump-to-page for analysts.
     *
     * 200 OK   — paginated list
     * 403      — caller does not have ADMIN role
     */
    @GetMapping
    public ResponseEntity<PagedResponse<AdminAccountResponse>> listAllAccounts(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        return ResponseEntity.ok(adminService.listAllAccounts(page, pageSize));
    }

    /**
     * GET /api/v1/admin/accounts/{id}
     *
     * 200 OK   — account found (even if soft-deleted)
     * 404      — account never existed
     * 403      — not ADMIN
     */
    @GetMapping("/{id}")
    public ResponseEntity<AdminAccountResponse> getAccount(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getAccountById(id));
    }

    /**
     * GET /api/v1/admin/accounts/by-user/{userId}
     *
     * Returns all ACTIVE accounts for a specific user.
     * Useful for admin investigations and customer support.
     *
     * 200 OK   — list (may be empty if user has no accounts)
     * 403      — not ADMIN
     */
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<List<AdminAccountResponse>> listByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(adminService.listAccountsByUser(userId));
    }
}
