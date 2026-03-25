package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response body for PATCH /users/operators/{id}/disable endpoint.
 */
public record DisableOperatorResponse(
        UUID id,
        String status
) {
}
