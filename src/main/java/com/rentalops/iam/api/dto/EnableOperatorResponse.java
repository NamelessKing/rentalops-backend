package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response DTO for PATCH /users/operators/{id}/enable.
 *
 * <p>Mirrors {@link DisableOperatorResponse} intentionally — both operations
 * change only the status field and return the same minimal shape. Keeping them
 * as separate records preserves explicit naming at the API boundary.
 */
public record EnableOperatorResponse(
        UUID id,
        String status
) {}

