package com.test.financialtracker.identity.domain.entity;


import com.test.financialtracker.common.BaseEntity;
import com.test.financialtracker.identity.domain.models.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;
//TODO:SCRIPT FLYWAY
//Naming convention per table
@Entity
@Table(
        name = "fn_trn_users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_keycloak_id", columnNames = "keycloak_id"),
                @UniqueConstraint(name = "uq_users_email",       columnNames = "email")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Users extends BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /**
     * Maps to Keycloak's "sub" (subject) claim from the JWT.
     * This is the stable, external identifier — never changes even if user
     * updates their email in Keycloak.
     */
    @Column(name = "keycloak_id", nullable = false, updatable = false)
    private String keycloakId;

    @Column(nullable = false)
    private String email;

    /**TODO:Improve
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Role role;
}