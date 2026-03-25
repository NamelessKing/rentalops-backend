package com.rentalops.tasks.api.dto;

import java.util.UUID;

/**
 * Projection used in GET /tasks/pool (Operator pool view).
 *
 * <p>Intentionally minimal: operators need just enough to decide whether to claim.
 * Assignee fields are omitted because pool tasks have no assignee yet.
 */
public record TaskPoolItemResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String category,
        String priority,
        String summary,
        Integer estimatedHours
) {}

