package com.rentalops.tasks.api.dto;

import java.util.UUID;

/**
 * Projection used in GET /tasks/my (Operator my-tasks view).
 *
 * <p>Includes status so the frontend can render the correct CTA
 * (Start / Complete) for each task inline in the list.
 */
public record MyTaskItemResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String category,
        String priority,
        String summary,
        String status
) {}

