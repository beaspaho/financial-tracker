package com.test.financialtracker.transaction.domains.models;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;


public record DepositRequest(

        @NotNull(message = "Account ID is required")
        UUID accountId,


        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "Amount format invalid (max 15 digits, 4 decimal places)")
        BigDecimal amount,

        /**

         * Required — rejected with 422 if absent (enforced by IdempotencyFilter).
         */
        @NotNull(message = "Idempotency key is required")
        UUID depositKey

) {}