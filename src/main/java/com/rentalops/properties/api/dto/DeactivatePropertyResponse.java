package com.rentalops.properties.api.dto;

import java.util.UUID;

/**
 * Response body for PATCH /properties/{id}/deactivate endpoint.
 */
public record DeactivatePropertyResponse(
        UUID id,
        boolean active
) {
}

