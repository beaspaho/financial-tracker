package com.test.financialtracker.identity.infrastructure.keycloak;

import com.test.financialtracker.common.exception.IdentityProviderException;
import com.test.financialtracker.identity.infrastructure.keycloak.dto.*;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

/**
 * Keycloak REST adapter
 * <p>
 * 1. Admin REST API  → create/delete users, assign roles
 * 2. Token endpoint  → authenticate (ROPC grant), get JWT
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdapter implements IdentityProviderPort {

    private final KeycloakProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public String registerUser(UserRegistrationRequest request) {
        String adminToken = obtainAdminToken();

        String userId = createKeycloakUser(request, adminToken);

        assignRealmRole(userId, request.role().name(), adminToken);

        log.info("Keycloak user created keycloakId={} email={} role={}", userId, request.email(), request.role());
        return userId;
    }

    @Override
    public TokenResponse authenticate(String email, String password) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", properties.appClientId());
        body.add("username", email);
        body.add("password", password);
        body.add("scope", "openid");

        try {
            KeycloakTokenResponse keycloakResponse = restClient.post()
                    .uri(tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(KeycloakTokenResponse.class);

            if (keycloakResponse == null) {
                throw new IdentityProviderException("Something went wrong during authenticate. Try again later.", HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
            return new TokenResponse(
                    keycloakResponse.getAccessToken(),
                    keycloakResponse.getRefreshToken(),
                    keycloakResponse.getExpiresIn(),
                    keycloakResponse.getTokenType()
            );
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IdentityProviderException("Invalid credentials", 401);
            }
            throw new IdentityProviderException("Authentication failed: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    @Override
    public void logout(String keycloakUserId) {
        String adminToken = obtainAdminToken();
        String url = adminUsersUrl() + "/" + keycloakUserId + "/logout";

        try {
            log.info("Ready to logout: {}", url);
            restClient.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Logged out successfully");
        } catch (HttpClientErrorException e) {
            throw new IdentityProviderException("Logout failed", e.getStatusCode().value());
        }
    }

    @Override
    public boolean isSessionValid(String token) {
        String introspectUrl = tokenUrl() + "/introspect";
        log.info("Jwt Token for validation: {}", token);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", properties.adminClientId());
        body.add("client_secret", properties.adminClientSecret());
        body.add("token", token);

        try {
            TokenIntrospectionResponse response = restClient.post()
                    .uri(introspectUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(TokenIntrospectionResponse.class);

            if (response == null) {
                throw new IdentityProviderException("Something went wrong during token validation. Try again later.", HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
            return Boolean.TRUE.equals(response.getActive());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IdentityProviderException("Invalid credentials", 401);
            }
            throw new IdentityProviderException("Introspection failed: " + e.getMessage(), e.getStatusCode().value());
        }
    }


    /**
     * Obtains a short-lived admin access token using client_credentials grant.
     * Retries up to 3 times with exponential backoff so a brief Keycloak
     * startup lag doesn't surface as a hard 500 to the client.
     */
    private String obtainAdminToken() {
        int maxAttempts = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("grant_type", "client_credentials");
                body.add("client_id", properties.adminClientId());
                body.add("client_secret", properties.adminClientSecret());

                KeycloakTokenResponse response = restClient.post()
                        .uri(properties.serverUrl() + "/realms/" + properties.realm() + "/protocol/openid-connect/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(body)
                        .retrieve()
                        .body(KeycloakTokenResponse.class);

                if (response == null) {
                    throw new IdentityProviderException("Something went wrong. Try again later.", HttpStatus.INTERNAL_SERVER_ERROR.value());
                }
                return response.getAccessToken();

            } catch (ResourceAccessException e) {
                if (attempt == maxAttempts) {
                    throw new IdentityProviderException(
                            "Keycloak is unreachable after " + maxAttempts + " attempts: " + e.getMessage(), e.hashCode());
                }
                log.warn("Keycloak unreachable (attempt {}/{}), retrying in {}ms...", attempt, maxAttempts, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                delayMs *= 2;
            }
        }
        throw new IdentityProviderException("Keycloak token fetch failed", 400);
    }

    /**
     * Creates the user representation in Keycloak.
     * Returns the Keycloak-assigned UUID (the "sub" claim).
     */
    private String createKeycloakUser(UserRegistrationRequest request, String adminToken) {
        CredentialRepresentation credential = CredentialRepresentation.builder()
                .type("password")
                .value(request.password())
                .temporary(false)
                .build();

        UserRepresentation userRepresentation = UserRepresentation.builder()
                .username(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .email(request.email())
                .enabled(true)
                .emailVerified(true)
                .requiredActions(Collections.emptyList())
                .credentials(List.of(credential))
                .attributes(Collections.singletonMap("app_user_id",
                        Collections.singletonList(request.appUserId().toString())))
                .build();

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri(adminUsersUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                    .body(userRepresentation)
                    .retrieve()
                    .toEntity(Void.class);

            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            if (location == null) throw new IdentityProviderException("No Location header in Keycloak response", 500);
            return location.substring(location.lastIndexOf('/') + 1);

        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.CONFLICT) {
                throw new IdentityProviderException("User already exists in Keycloak", 409);
            }
            throw new IdentityProviderException("Keycloak user creation failed: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    /**
     * Assigns a realm-level role to the user.
     * Roles must be pre-created in Keycloak realm (realm-export.json).
     */
    private void assignRealmRole(String keycloakUserId, String roleName, String adminToken) {
        String roleUrl = String.format("%s/admin/realms/%s/roles/%s", properties.serverUrl(), properties.realm(), roleName);

        RoleRepresentation role = restClient.get()
                .uri(roleUrl)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .retrieve()
                .body(RoleRepresentation.class);

        if (role == null) {
            throw new IdentityProviderException("Something went wrong while getting roles. Try again later.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        }

        String mappingUrl = adminUsersUrl() + "/" + keycloakUserId + "/role-mappings/realm";
        restClient.post()
                .uri(mappingUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    private String tokenUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/token", properties.serverUrl(), properties.realm());
    }

    private String adminUsersUrl() {
        return String.format("%s/admin/realms/%s/users", properties.serverUrl(), properties.realm());
    }
}
