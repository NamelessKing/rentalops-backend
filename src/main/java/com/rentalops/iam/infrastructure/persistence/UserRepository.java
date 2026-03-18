package com.rentalops.iam.infrastructure.persistence;

import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
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

    /**
     * Used by operator listing to fetch all operators of a specific tenant.
     * Filters by both tenant ID and role to return only OPERATOR-role users.
     * This supports Slice 2 admin team management.
     */
    List<User> findAllByTenantIdAndRole(UUID tenantId, UserRole role);

    /**
     * Used by operator disable and other operations that need to verify
     * ownership within a tenant before modifying or reading operator details.
     */
    Optional<User> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Used by operator update to guard against duplicate emails while excluding
     * the operator being updated. Without the IdNot clause, updating an operator
     * without changing their email would incorrectly trigger a 409 conflict.
     */
    boolean existsByEmailAndIdNot(String email, UUID excludedId);
}
