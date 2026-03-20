package com.rentalops.tasks.api.dto;

import java.util.UUID;

/**
 * Full projection used in GET /tasks/{id} and returned by POST /tasks.
 *
 * <p>Returning this from POST /tasks lets the frontend redirect directly to the
 * task detail page after creation without an extra GET call.
 */
public record TaskDetailResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        String category,
        String priority,
        String summary,
        String description,
        String status,
        String dispatchMode,
        Integer estimatedHours,
        UUID assigneeId,
        String assigneeName,
        UUID sourceIssueReportId
) {}

