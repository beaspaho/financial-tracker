package com.test.financialtracker.identity.ports;


import com.test.financialtracker.identity.domain.models.User;

public interface IdentityProviderPort {

    /**
     * Creates a user identity in Keycloak.
     *
     * @param email    user's email (also used as Keycloak username)
     * @param password raw password — Keycloak handles hashing
     * @param role     assigned as a realm role in Keycloak
     * @return the Keycloak "sub" (subject UUID) for the created user
     * @throws IdentityProviderException if Keycloak returns a non-2xx response
     */
    String registerUser(String email, String password, User.Role role);

    /**
     * Authenticates credentials against Keycloak and returns a signed JWT.
     *
     * Uses the Resource Owner Password Credentials (ROPC) grant.

     * @return TokenResponse containing access_token, refresh_token, expires_in
     * @throws IdentityProviderException on invalid credentials or Keycloak error
     */
    TokenResponse authenticate(String email, String password);

    /**
     * Deletes a user from Keycloak by their subject ID.
     * Called on account deletion to keep IdP and local DB in sync.
     */
    //TODO:Decide approach
    void deleteUser(String keycloakId);

    record TokenResponse(
            String accessToken,
            String refreshToken,
            int    expiresIn,
            String tokenType
    ) {}

    class IdentityProviderException extends RuntimeException {
        private final int statusCode;

        public IdentityProviderException(String message, int statusCode) {
            super(message);
            this.statusCode = statusCode;
        }

        public int getStatusCode() { return statusCode; }
    }
}