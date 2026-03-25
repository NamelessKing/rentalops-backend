package com.rentalops.tasks.api.dto;

import java.util.UUID;

/**
 * Projection used in GET /tasks (Admin list view).
 *
 * <p>Includes resolved names (propertyName, assigneeName) so the frontend
 * can render the list without additional round-trips.
 */
public record TaskListItemResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String category,
        String priority,
        String summary,
        String status,
        String dispatchMode,
        UUID assigneeId,
        String assigneeName
) {}

