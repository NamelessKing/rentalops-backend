package com.rentalops.iam.infrastructure.persistence;

import com.rentalops.iam.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for User entities.
 *
 * <p>Spring Data JPA derives the SQL for derived query methods automatically
 * from the method name. No manual SQL is needed for these simple lookups.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Used by login to load the user before verifying the password.
     * Returns Optional so the caller can decide how to handle absence
     * without catching exceptions.
     */
    Optional<User> findByEmail(String email);

    /**
     * Used by register-admin to guard against duplicate emails before
     * attempting to persist — gives a clean 409 instead of a DB constraint error.
     */
    boolean existsByEmail(String email);
}
