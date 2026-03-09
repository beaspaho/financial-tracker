package com.test.financialtracker.identity.infrastructure.keycloak.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRepresentation {

    private String id;
    private Long createdTimestamp;
    private String username;
    private Boolean enabled;
    private Boolean totp;
    private Boolean emailVerified;
    private String firstName;
    private String lastName;
    private String email;
    private String federationLink;
    private String serviceAccountClientId;

    private List<String> disableableCredentialTypes;
    private List<String> requiredActions;
    private List<CredentialRepresentation> credentials;

    // Keycloak allows custom attributes (e.g., "phone": ["555-1234"])
    private Map<String, List<String>> attributes;

    private Map<String, Boolean> access;
//    private List<UserConsentRepresentation> clientConsents;
//    private List<FederatedIdentityRepresentation> federat/edIdentities;
    private List<String> realmRoles;
    private Map<String, List<String>> clientRoles;
    private List<String> groups;
}
