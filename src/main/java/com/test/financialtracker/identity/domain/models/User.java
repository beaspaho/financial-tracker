package com.test.financialtracker.identity.domain.models;


import lombok.Builder;
import lombok.Getter;

import java.util.UUID;


@Getter
@Builder
public class User {

    private final UUID   id;
    private final String keycloakId;
    private final String email;
    private final Role   role;

    public enum Role {
        USER,
        ADMIN
    }

    public static User newUser(UUID userId, String keycloakId, String email, Role role) {
        return User.builder()
                .id(userId)
                .keycloakId(keycloakId)
                .email(email)
                .role(role)
                .build();
    }

}