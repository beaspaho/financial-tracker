package com.test.financialtracker.account.domain.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/accounts
 */
public record CreateAccountRequest(

        @NotBlank(message = "Account name is required")
        @Size(min = 1, max = 50, message = "Account name must be between 1 and 50 characters")
        String name,

        /**
         * ISO 4217 currency code — 3 uppercase letters (EUR, USD, GBP).
         * Validated by regex here; business validation (supported currencies)
         */
        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g. EUR, USD)")
        String currency

) {}