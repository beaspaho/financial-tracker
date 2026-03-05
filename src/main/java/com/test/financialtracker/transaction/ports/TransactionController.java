package com.test.financialtracker.transaction.ports;

import com.test.financialtracker.common.exception.DuplicateTransactionException;
import com.test.financialtracker.utils.SecurityContextHelper;
import com.test.financialtracker.transaction.domains.models.*;
import com.test.financialtracker.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService txService;
    private final SecurityContextHelper securityContextHelper;


    /**
     * 201 Created  — new deposit processed
     * 200 OK       — duplicate idempotency key (same result returned)
     * 400 Bad Request — validation failure
     * 403 Forbidden   — account belongs to another user
     * 404 Not Found   — account doesn't exist or is deleted
     */
    @PostMapping("/deposit")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> deposit(
            @Valid @RequestBody DepositRequest request
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        try {
            TransactionResponse response = txService.deposit(request, callerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateTransactionException e) {
            // Idempotent replay — return original result as 200 (not 201)
            return ResponseEntity.ok(TransactionResponse.from(e.getExisting()));
        }
    }


    /**
     * 201 Created         — withdrawal processed
     * 200 OK              — duplicate idempotency key
     * 422 Unprocessable   — insufficient funds
     * 403 Forbidden       — account belongs to another user
     */
    @PostMapping("/withdraw")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> withdraw(
            @Valid @RequestBody WithdrawRequest request
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        try {
            TransactionResponse response = txService.withdraw(request, callerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateTransactionException e) {
            return ResponseEntity.ok(TransactionResponse.from(e.getExisting()));
        }
    }

    /**
     * 201 Created         — transfer processed
     * 200 OK              — duplicate idempotency key
     * 400 Bad Request     — source == destination
     * 422 Unprocessable   — insufficient funds in source
     * 403 Forbidden       — either account belongs to another user
     */
    @PostMapping("/transfer")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionResponse> transfer(
            @Valid @RequestBody TransferRequest request
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        try {
            TransactionResponse response = txService.transfer(request, callerId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DuplicateTransactionException e) {
            return ResponseEntity.ok(TransactionResponse.from(e.getExisting()));
        }
    }


    /**
     * GET /api/v1/transactions
     *   ?accountId=UUID        (required)
     *   &type=DEPOSIT          (optional: DEPOSIT | WITHDRAWAL | TRANSFER)
     *   &from=2025-01-01T00:00:00Z  (optional, ISO-8601)
     *   &to=2025-01-31T23:59:59Z    (optional, ISO-8601)
     *   &cursor=2025-01-15T10:00:00Z (optional, keyset cursor from previous response)
     *   &pageSize=20           (optional, default 20, max 100)
     *
     * 200 OK   — returns TransactionHistoryResponse
     * 400      — from > to, invalid type string
     * 403      — account belongs to another user
     */
    @GetMapping
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<TransactionHistoryResponse> getHistory(
            @RequestParam                                          UUID            accountId,
            @RequestParam(required = false)                        TransactionType type,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)   Instant         from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)   Instant         to,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)   Instant         cursor,
            @RequestParam(required = false, defaultValue = "20")  Integer         pageSize
    ) {
        UUID callerId = securityContextHelper.getAuthenticatedUserId();
        TransactionHistoryResponse response = txService.getHistory(
                accountId, type, from, to, cursor, pageSize, callerId
        );
        return ResponseEntity.ok(response);
    }
}
