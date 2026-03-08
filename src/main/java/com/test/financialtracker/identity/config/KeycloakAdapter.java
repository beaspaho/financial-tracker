package com.test.financialtracker.identity.config;

import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Keycloak REST adapter

 * 1. Admin REST API  → create/delete users, assign roles
 * 2. Token endpoint  → authenticate (ROPC grant), get JWT


 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdapter implements IdentityProviderPort {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${keycloak.server-url}")
    private String serverUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin-client-id}")
    private String adminClientId;

    @Value("${keycloak.admin-client-secret}")
    private String adminClientSecret;

    @Value("${keycloak.app-client-id}")
    private String appClientId;


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
        String url = tokenUrl();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", appClientId);
        body.add("username", email);
        body.add("password", password);
        body.add("scope", "openid");

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(body, headers), Map.class
            );

            Map<?, ?> map = response.getBody();
            return new TokenResponse(
                    (String) map.get("access_token"),
                    (String) map.get("refresh_token"),
                    (int) map.get("expires_in"),
                    (String) map.get("token_type")
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
        
        HttpHeaders headers = bearerHeaders(adminToken);
        try {
            log.info("Ready to logout: {}", url);
            var response = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
            log.info("Logged out successfully: {}", response.getStatusCode().value());
        } catch (HttpClientErrorException e) {
            throw new IdentityProviderException("Logout failed", e.getStatusCode().value());
        }
    }

    @Override
    public void deleteUser(String keycloakId) {
        String adminToken = obtainAdminToken();
        String url = adminUsersUrl() + "/" + keycloakId;

        HttpHeaders headers = bearerHeaders(adminToken);
        try {
            restTemplate.exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Keycloak user deleted keycloakId={}", keycloakId);
        } catch (HttpClientErrorException e) {
            log.warn("Failed to delete Keycloak user keycloakId={} status={}", keycloakId, e.getStatusCode());
            throw new IdentityProviderException("Delete failed", e.getStatusCode().value());
        }
    }

    @Override
    public boolean isSessionValid(String token) {
        var introspectUrl = tokenUrl() + "/introspect";
        log.info("Jwt Token for validation: {}", token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", adminClientId);
        body.add("client_secret", adminClientSecret);
        body.add("token", token);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    introspectUrl, new HttpEntity<>(body, headers), Map.class
            );

            Map<?, ?> map = response.getBody();
            return
                    (Boolean) map.get("active");
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                throw new IdentityProviderException("Invalid credentials", 401);
            }
            throw new IdentityProviderException("Introspection failed: " + e.getMessage(), e.getStatusCode().value());
        }
    }


    /**
     * Obtains a short-lived admin access token using client_credentials grant.
     * This token is used only for Admin REST API calls — never returned to users.
     */
    /**
     * Replace your existing obtainAdminToken() with this version.
     * Retries up to 3 times with exponential backoff so a brief Keycloak
     * startup lag doesn't surface as a hard 500 to the client.
     */
    private String obtainAdminToken() {
        int maxAttempts = 3;
        long delayMs = 1000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("grant_type", "client_credentials");
                body.add("client_id", adminClientId);
                body.add("client_secret", adminClientSecret);

                ResponseEntity<Map> response = restTemplate.postForEntity(
                        serverUrl + "/realms/" + realm + "/protocol/openid-connect/token",
                        new HttpEntity<>(body, headers),
                        Map.class
                );

                return (String) response.getBody().get("access_token");

            } catch (ResourceAccessException e) {
                // Connection refused — Keycloak not ready yet
                if (attempt == maxAttempts) {
                    throw new IdentityProviderPort.IdentityProviderException(
                            "Keycloak is unreachable after " + maxAttempts + " attempts: " + e.getMessage(), e.hashCode());
                }
                log.warn("Keycloak unreachable (attempt {}/{}), retrying in {}ms...", attempt, maxAttempts, delayMs);
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                delayMs *= 2; // exponential backoff: 1s, 2s, 4s
            }
        }
        throw new IdentityProviderPort.IdentityProviderException("Keycloak token fetch failed",400);

    }

    /**
     * Creates the user representation in Keycloak.
     * Returns the Keycloak-assigned UUID (the "sub" claim).
     */
    @SuppressWarnings("unchecked")
        private String createKeycloakUser(UserRegistrationRequest userRegistrationRequest, String adminToken) {
            // TODO: Remove dummy
        String appUserId = UUID.randomUUID().toString();

        Map<String, List<String>> attributes = new HashMap<>();
        attributes.put("app_user_id", Collections.singletonList(appUserId));

        var body = """
                {
                     "username": "%s",
                      "firstName": "ssss",
                      "lastName": "ssss",
                      "email": "%s",
                      "enabled": true,
                      "emailVerified": true,
                      "requiredActions": [],
                      "credentials": [{
                        "type": "password",
                        "value": "%s",
                        "temporary": false
                      }],
                      "attributes": {
                          "app_user_id": ["%s"]
                      }
                }
                """.formatted(userRegistrationRequest.email(),
                userRegistrationRequest.email(), userRegistrationRequest.password(), userRegistrationRequest.appUserId());

        log.info("Body of creating Keycloak user: {}", body);
        HttpHeaders headers = bearerHeaders(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    adminUsersUrl(), new HttpEntity<>(body, headers), Void.class
            );
            log.info("Keycloak user created: {}", response.getBody());
            String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
            log.info("Keycloak user location: {}", location);
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
    @SuppressWarnings("unchecked")
    private void assignRealmRole(String keycloakUserId, String roleName, String adminToken) {
        HttpHeaders headers = bearerHeaders(adminToken);

        String roleUrl = String.format("%s/admin/realms/%s/roles/%s", serverUrl, realm, roleName);
        ResponseEntity<Map> roleResponse = restTemplate.exchange(
                roleUrl, HttpMethod.GET, new HttpEntity<>(headers), Map.class
        );
        Map<?, ?> role = roleResponse.getBody();

        String mappingUrl = adminUsersUrl() + "/" + keycloakUserId + "/role-mappings/realm";
        headers.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.postForEntity(mappingUrl, new HttpEntity<>(List.of(role), headers), Void.class);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        return h;
    }

    private String tokenUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
    }

    private String adminUsersUrl() {
        return String.format("%s/admin/realms/%s/users", serverUrl, realm);
    }
}
