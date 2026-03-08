package com.test.financialtracker.identity.infrastructure.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CredentialRepresentation {

    private String id;
    private String type;
    private String userLabel;
    private Long createdDate;
    private String secretData;
    private String credentialData;
    private Integer priority;
    private String value;
    private Boolean temporary;
}
