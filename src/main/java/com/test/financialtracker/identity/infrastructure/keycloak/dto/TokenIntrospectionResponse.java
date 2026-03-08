package com.test.financialtracker.identity.infrastructure.keycloak.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps the JSON body returned by:
 * POST /realms/{realm}/protocol/openid-connect/token/introspect
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TokenIntrospectionResponse {

    private Boolean active;

    private String scope;

    @JsonProperty("client_id")
    private String clientId;

    private String username;

    @JsonProperty("token_type")
    private String tokenType;

    private Long exp;

    private Long iat;

    private Long nbf;

    private String sub;

    private String iss;

    private String jti;
}
