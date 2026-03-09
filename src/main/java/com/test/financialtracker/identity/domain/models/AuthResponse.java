package com.test.financialtracker.identity.domain.models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.UUID;


@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        UUID userId,
        String email,
        String role,
        String message,
        String accessToken,
        String refreshToken,
        Integer expiresIn
) {
    public static AuthResponse registered(UUID userId, String email) {
        return new AuthResponse(
                userId, email,
                null,
                "Registration successful. Please log in.",
                null, null, null
        );
    }

    public static AuthResponse authenticated(
            UUID userId, String email, String role,
            String accessToken, String refreshToken, int expiresIn
    ) {
        return new AuthResponse(
                userId, email, role,
                null,
                accessToken, refreshToken, expiresIn
        );
    }

    public static AuthResponse logout(UUID userId, String email, String role) {
        return new AuthResponse(
                userId, email, role, "Successfully logged out", null, null, null
        );
    }
}
