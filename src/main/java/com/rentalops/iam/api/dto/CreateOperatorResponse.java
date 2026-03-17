package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response body for POST /users/operators endpoint.
 *
 * <p>Returns the created operator with all relevant fields.
 * Role is always OPERATOR, status is always ACTIVE for newly created operators.
 */
public record CreateOperatorResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        String status,
        String specializationCategory
) {
}

