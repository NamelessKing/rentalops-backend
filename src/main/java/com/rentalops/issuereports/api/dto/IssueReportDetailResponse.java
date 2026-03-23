package com.rentalops.issuereports.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Full detail response for GET /issue-reports/{id}.
 *
 * <p>Extends the list projection with review fields: {@code reviewedByUserId} and
 * {@code reviewedAt} are null for OPEN reports and populated once an Admin acts on them.
 */
public record IssueReportDetailResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID reportedByUserId,
        String reportedByUserName,
        String description,
        String status,
        LocalDateTime createdAt,
        UUID reviewedByUserId,
        LocalDateTime reviewedAt
) {}
