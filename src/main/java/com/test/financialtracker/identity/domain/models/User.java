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


    public static User newUser(String keycloakId, String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .keycloakId(keycloakId)
                .email(email)
                .role(Role.USER)
                .build();
    }

//TODO:Implement or not decision
    public static User newAdmin(String keycloakId, String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .keycloakId(keycloakId)
                .email(email)
                .role(Role.ADMIN)
                .build();
    }
}