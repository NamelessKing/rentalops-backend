package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response DTO for PUT /users/operators/{id}.
 *
 * <p>Returns the full updated operator state so the frontend can refresh
 * the edit form or list without issuing a separate GET request.
 */
public record UpdateOperatorResponse(
        UUID id,
        String fullName,
        String email,
        String role,
        String status,
        String specializationCategory
) {}

