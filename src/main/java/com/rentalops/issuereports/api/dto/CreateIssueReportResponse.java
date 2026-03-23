package com.rentalops.issuereports.api.dto;

import java.util.UUID;

/**
 * Response returned after a successful issue report creation.
 *
 * <p>The status is always {@code OPEN} at creation time.
 * Includes the identifiers needed by the caller to navigate to the created resource.
 */
public record CreateIssueReportResponse(
        UUID id,
        UUID propertyId,
        UUID reportedByUserId,
        String description,
        String status
) {}
