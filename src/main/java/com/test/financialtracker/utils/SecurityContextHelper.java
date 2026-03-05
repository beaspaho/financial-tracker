package com.test.financialtracker.utils;


import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class SecurityContextHelper {


    public UUID getAuthenticatedUserId() {
        Jwt jwt = getJwt();
        String appUserId = jwt.getClaimAsString("app_user_id");
        if (appUserId != null) {
            return UUID.fromString(appUserId);
        }
        throw new IllegalStateException("app_user_id claim missing from JWT. Check Keycloak token mapper configuration.");
    }

    /**
     * Returns the Keycloak "sub" claim — the external identity UUID.
     * Useful for lookups when the local userId is not yet available
     * (e.g. right after registration before local profile is confirmed).
     */
    public String getKeycloakId() {
        return getJwt().getSubject();
    }

    private Jwt getJwt() {
        Object principal = SecurityContextHolder.getContext()
                .getAuthentication()
                .getPrincipal();

        if (principal instanceof Jwt jwt) {
            return jwt;
        }
        throw new IllegalStateException("No JWT principal in security context");
    }
}