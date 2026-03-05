package com.test.financialtracker.account.ports;

import com.test.financialtracker.account.domain.models.AccountResponse;
import com.test.financialtracker.account.domain.models.CreateAccountRequest;
import com.test.financialtracker.account.domain.models.UpdateAccountRequest;
import com.test.financialtracker.account.service.AccountService;
import com.test.financialtracker.utils.SecurityContextHelper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**

 * ENDPOINTS:
 *   GET    /api/v1/accounts          — list caller's accounts
 *   POST   /api/v1/accounts          — create new account
 *   GET    /api/v1/accounts/{id}     — get single account
 *   PUT    /api/v1/accounts/{id}     — rename account
 *   DELETE /api/v1/accounts/{id}     — soft-delete account
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final SecurityContextHelper securityContextHelper;

    /**
     * GET /api/v1/accounts
     * Returns all active accounts belonging to the authenticated user.
     * Soft-deleted accounts are excluded (via @SQLRestriction on entity).
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(accountService.listForUser(callerId));
    }

    /**
     * GET /api/v1/accounts/{id}
     * 200 OK    — account found and belongs to caller
     * 403       — account exists but belongs to another user
     * 404       — account doesn't exist or is soft-deleted
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable UUID id) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(accountService.getById(id, callerId));
    }

    /**
     * POST /api/v1/accounts
     * 201 Created   — account created with balance = 0.00
     * 400           — validation failure (missing name, invalid currency)
     */
    @PostMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody CreateAccountRequest request
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        AccountResponse response = accountService.create(request, callerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * PUT /api/v1/accounts/{id}
     * 200 OK    — account renamed
     * 400       — validation failure
     * 403       — not owner
     * 404       — not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<AccountResponse> updateAccount(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAccountRequest request
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        return ResponseEntity.ok(accountService.update(id, request, callerId));
    }

    /**
     * DELETE /api/v1/accounts/{id}
     * 204 No Content — soft-deleted successfully
     * 400            — balance is not zero
     * 403            — not owner
     * 404            — not found
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID id) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        accountService.delete(id, callerId);
        return ResponseEntity.noContent().build();
    }
}
