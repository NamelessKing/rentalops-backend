package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response body for GET /users/operators endpoint.
 *
 * <p>Represents a single operator in the list view.
 * Includes minimal information needed for the Admin to see and manage operators.
 */
public record OperatorListItemResponse(
        UUID id,
        String fullName,
        String email,
        String status,
        String specializationCategory
) {
}

