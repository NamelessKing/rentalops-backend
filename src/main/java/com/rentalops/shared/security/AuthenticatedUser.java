package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;

import java.util.UUID;

/**
 * Application-level representation of the logged-in user.
 *
 * <p>This is not:
 * - a database entity
 * - a request/response DTO
 * - a Spring-specific JPA model
 *
 * It is the principal that the backend uses after authentication has already
 * happened. In other words, once a JWT has been accepted, this record is the
 * "current user" seen by the application layer.
 */
public record AuthenticatedUser(
        UUID userId,
        UUID tenantId,
        UserRole role,
        String email,
        UserStatus status
) {
    /*
     * Small helper methods keep later service code readable:
     * "if (!currentUser.isAdmin())" is clearer than repeating enum comparisons.
     */
    public boolean isAdmin() {
        return role == UserRole.ADMIN;
    }

    public boolean isOperator() {
        return role == UserRole.OPERATOR;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isDisabled() {
        return status == UserStatus.DISABLED;
    }
}
