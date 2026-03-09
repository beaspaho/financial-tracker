package com.test.financialtracker.identity.domain.transformers;

import com.test.financialtracker.identity.domain.entity.Users;
import com.test.financialtracker.identity.domain.models.User;
import org.springframework.stereotype.Component;


@Component
public class UserMapper {

    public User toDomain(Users entity) {
        return User.builder()
                .id(entity.getId())
                .keycloakId(entity.getKeycloakId())
                .email(entity.getEmail())
                .role(entity.getRole())
                .build();
    }

    public Users toEntity(User domain) {
        return Users.builder()
                .id(domain.getId())
                .keycloakId(domain.getKeycloakId())
                .email(domain.getEmail())
                .role(domain.getRole())
                .build();
    }
}