package com.rentalops.properties.api.dto;

import java.util.UUID;

/**
 * Full property shape used by detail and create responses.
 */
public record PropertyDetailResponse(
        UUID id,
        String propertyCode,
        String name,
        String address,
        String city,
        String notes,
        boolean active
) {
}

