package com.test.financialtracker.identity.repository;

import com.test.financialtracker.identity.domain.entity.Users;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port — Spring Data handles implementation.
 * Only the auth module accesses this repository directly.
 * Other modules go through UserPort .
 */
public interface UserRepository extends JpaRepository<Users, UUID> {

    Optional<Users> findByKeycloakId(String keycloakId);


    boolean existsByEmail(String email);
}
