package com.test.financialtracker.account.repository;

import com.test.financialtracker.account.domain.entity.Accounts;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Accounts, UUID> {


    /**
     * Lists all active accounts for a user.
     * @SQLRestriction automatically appends AND deleted_at IS NULL.
     */
    List<Accounts> findAllByUserId(UUID userId);


    Optional<Accounts> findById(UUID id);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Accounts a WHERE a.id = :id")
    Optional<Accounts> findByIdWithLock(@Param("id") UUID id);


    @Query(
            value  = "SELECT * FROM accounts ORDER BY user_id, created_at LIMIT :size OFFSET :offset",
            nativeQuery = true
    )
    List<Accounts> findAllForAdmin(@Param("size") int size, @Param("offset") int offset);


    @Query(value = "SELECT COUNT(*) FROM accounts", nativeQuery = true)
    long countAllForAdmin();


    List<Accounts> findAllByUserIdOrderByCreatedAtAsc(UUID userId);


    @Query("SELECT a FROM Accounts a")
    List<Accounts> findAllActive();
}
