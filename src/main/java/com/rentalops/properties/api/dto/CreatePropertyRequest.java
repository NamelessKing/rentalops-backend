package com.rentalops.properties.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for property creation.
 */
public record CreatePropertyRequest(
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

