package com.test.financialtracker.identity.domain.models;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RegisterRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,


        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,


        @NotBlank(message = "First name is required")
        @Size(min = 4, max = 50, message = "First name should be between 4 and 50 characters")
        String firstName,


        @NotBlank(message = "Last name is required")
        @Size(min = 4, max = 50, message = "Last name should be between 4 and 50 characters")
        String lastName

) {}
