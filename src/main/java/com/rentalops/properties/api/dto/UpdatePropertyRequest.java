package com.rentalops.properties.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for PUT /properties/{id}.
 *
 * <p>Keeping a dedicated DTO for updates makes the API intent explicit and
 * allows update-specific validation to evolve without coupling it to create.
 */
public record UpdatePropertyRequest(
        @NotBlank(message = "Property code is required")
        String propertyCode,

        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Address is required")
        String address,

        @NotBlank(message = "City is required")
        String city,

        String notes
) {
}

