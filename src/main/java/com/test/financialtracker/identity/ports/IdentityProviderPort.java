package com.test.financialtracker.identity.ports;


import com.test.financialtracker.identity.domain.models.User;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

public interface IdentityProviderPort {

    /**
     * Creates a user identity in Keycloak.
     *
     * @param request contains all fields needed for Keycloak user creation
     * @return the Keycloak "sub" (subject UUID) for the created user
     * @throws IdentityProviderException if Keycloak returns a non-2xx response
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
     * @throws IdentityProviderException on invalid credentials or Keycloak error
     */
    TokenResponse authenticate(String email, String password);

    void logout(String keycloakUserId);

    /**
     * Deletes a user from Keycloak by their subject ID.
     * Called on account deletion to keep IdP and local DB in sync.
     */
    //TODO:Decide approach
    void deleteUser(String keycloakId);

    boolean isSessionValid(String token);

    record TokenResponse(
            String accessToken,
            String refreshToken,
            int expiresIn,
            String tokenType
    ) {
    }

    class IdentityProviderException extends RuntimeException {
        private final int statusCode;

        public IdentityProviderException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }
}