package com.test.financialtracker.identity.config;

import com.test.financialtracker.identity.domain.models.User;
import com.test.financialtracker.identity.ports.IdentityProviderPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Keycloak REST adapter

 * 1. Admin REST API  → create/delete users, assign roles
 * 2. Token endpoint  → authenticate (ROPC grant), get JWT


 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeycloakAdapter implements IdentityProviderPort {

    private final RestTemplate restTemplate;

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
    public String registerUser(String email, String password, User.Role role) {
        String adminToken = obtainAdminToken();

        String userId = createKeycloakUser(email, password, adminToken);

        assignRealmRole(userId, role.name(), adminToken);

        log.info("Keycloak user created keycloakId={} email={} role={}", userId, email, role);
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


    /**
     * Obtains a short-lived admin access token using client_credentials grant.
     * This token is used only for Admin REST API calls — never returned to users.
     */
    private String obtainAdminToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "client_credentials");
        body.add("client_id", adminClientId);
        body.add("client_secret", adminClientSecret);

        ResponseEntity<Map> response = restTemplate.postForEntity(
                tokenUrl(), new HttpEntity<>(body, headers), Map.class
        );
        return (String) response.getBody().get("access_token");
    }

    /**
     * Creates the user representation in Keycloak.
     * Returns the Keycloak-assigned UUID (the "sub" claim).
     */
    @SuppressWarnings("unchecked")
    private String createKeycloakUser(String email, String password, String adminToken) {
        Map<String, Object> userRep = Map.of(
                "username", email,
                "email", email,
                "enabled", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", false
                ))
        );

        HttpHeaders headers = bearerHeaders(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(
                    adminUsersUrl(), new HttpEntity<>(userRep, headers), Void.class
            );

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
