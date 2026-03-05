package com.test.financialtracker.account.domain.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/v1/accounts/{id}
 *
 * Only the name is user-editable.
 * Balance is NEVER directly settable via API — only through transactions.
 * Currency is immutable after creation (changing currency would corrupt history).
 */
public record UpdateAccountRequest(

        @NotBlank(message = "Account name is required")
        @Size(min = 1, max = 50, message = "Account name must be between 1 and 50 characters")
        String name

) {}
