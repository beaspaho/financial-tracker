package com.test.financialtracker.identity.ports;


import com.test.financialtracker.identity.domain.models.User;

import java.util.UUID;

public interface IdentityProviderPort {

    /**
     * Creates a user identity in Keycloak.
     *
     * @param request contains all fields needed for Keycloak user creation
     * @return the Keycloak "sub" (subject UUID) for the created user
     * @throws com.test.financialtracker.common.exception.IdentityProviderException if Keycloak returns a non-2xx response
     */
    String registerUser(UserRegistrationRequest request);

    record UserRegistrationRequest(
            String email,
            String password,
            String firstName,
            String lastName,
            UUID appUserId,
            User.Role role
    ) {
        public static UserRegistrationRequest of(
                String email,
                String password,
                String firstName,
                String lastName,
                UUID appUserId,
                User.Role role
        ) {
            return new UserRegistrationRequest(
                    email,
                    password,
                    firstName,
                    lastName,
                    appUserId,
                    role
            );
        }

    }

    /**
     * Authenticates credentials against Keycloak and returns a signed JWT.
     * <p>
     * Uses the Resource Owner Password Credentials (ROPC) grant.
     *
     * @return TokenResponse containing access_token, refresh_token, expires_in
     * @throws com.test.financialtracker.common.exception.IdentityProviderException on invalid credentials or Keycloak error
     */
    TokenResponse authenticate(String email, String password);

    void logout(String keycloakUserId);

    boolean isSessionValid(String token);

    record TokenResponse(
            String accessToken,
            String refreshToken,
            int expiresIn,
            String tokenType
    ) {
    }

}