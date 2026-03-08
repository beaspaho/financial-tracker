package com.test.financialtracker.identity.infrastructure.keycloak;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "keycloak")
record KeycloakProperties(
        String serverUrl,
        String realm,
        String adminClientId,
        String adminClientSecret,
        String appClientId
) {}
