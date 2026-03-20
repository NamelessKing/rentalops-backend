package com.rentalops.tasks.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for POST /tasks.
 *
 * <p>Bean Validation enforces structural constraints here.
 * Business rules are enforced in TaskApplicationService:
 *   - if dispatchMode=POOL, assigneeId must be null
 *   - if dispatchMode=DIRECT_ASSIGNMENT, assigneeId is required
 *   - property must belong to the authenticated tenant
 *   - assignee (if present) must be an OPERATOR in the same tenant
 *
 * <p>category, priority and dispatchMode are accepted as Strings and parsed
 * in the service layer, producing a readable 400 if the value is not a valid enum.
 */
public record CreateTaskRequest(

        @NotNull(message = "propertyId is required")
        UUID propertyId,

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

        // Nullable for POOL tasks; required for DIRECT_ASSIGNMENT (validated in service).
        UUID assigneeId,

        Integer estimatedHours
) {}

