package com.rentalops.issuereports.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request body for POST /issue-reports.
 *
 * <p>Structural constraints are enforced here via Bean Validation.
 * Business rules (property belongs to the tenant, caller is OPERATOR)
 * are enforced in IssueReportApplicationService.
 */
public record CreateIssueReportRequest(

        @NotNull(message = "propertyId is required")
        UUID propertyId,

        @NotBlank(message = "description is required")
        @Size(max = 2000, message = "description must not exceed 2000 characters")
        String description
) {}

