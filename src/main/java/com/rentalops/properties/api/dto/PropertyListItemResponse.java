package com.rentalops.properties.api.dto;

import java.util.UUID;

/**
 * Compact property shape used by list screens.
 */
public record PropertyListItemResponse(
        UUID id,
        String propertyCode,
        String name,
        String city,
        boolean active
) {
}

