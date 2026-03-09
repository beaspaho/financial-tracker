package com.test.financialtracker.common.exception;

import com.test.financialtracker.identity.ports.IdentityProviderPort;

/**
 * Thrown by KeycloakAdapter when the Identity Provider returns an error.
 *
 * Lives in the common package so GlobalExceptionHandler can reference it
 * without creating a cross-module dependency on the auth module internals.
 *
 * statusCode mirrors the HTTP status Keycloak returned:
 *   401 → invalid credentials
 *   409 → user already exists
 *   503 → Keycloak unreachable
 */
public class IdentityProviderException extends RuntimeException {

    private final int statusCode;

    public IdentityProviderException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
