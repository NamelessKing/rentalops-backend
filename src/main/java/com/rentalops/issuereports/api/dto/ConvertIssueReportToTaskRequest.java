package com.rentalops.issuereports.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for PATCH /issue-reports/{id}/convert-to-task.
 *
 * <p>Structural constraints are enforced here.
 * Business rules (POOL vs DIRECT_ASSIGNMENT assignee logic, category match) are
 * enforced in IssueReportApplicationService.
 *
 * <p>Note: propertyId is NOT in this request — the property is inherited from
 * the IssueReport source, not provided by the client.
 */
public record ConvertIssueReportToTaskRequest(

        @NotNull(message = "category is required")
        String category,

        @NotNull(message = "priority is required")
        String priority,

        @NotBlank(message = "summary is required")
        @Size(max = 255, message = "summary must not exceed 255 characters")
        String summary,

        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description,

        @NotNull(message = "dispatchMode is required")
        String dispatchMode,

        // Null for POOL, required for DIRECT_ASSIGNMENT (validated in service).
        UUID assigneeId,

        Integer estimatedHours
) {}

