package com.rentalops.issuereports.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Full detail response for GET /issue-reports/{id}.
 *
 * <p>Extends the list projection with review fields: {@code reviewedByUserId} and
 * {@code reviewedAt} are null for OPEN reports and populated once an Admin acts on them.
 *
 * <p>Timestamp fields use {@link Instant} so Jackson serialises them with the UTC
 * suffix "Z" (e.g. {@code "2026-03-11T10:00:00Z"}), matching the API contract.
 */
public record IssueReportDetailResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID reportedByUserId,
        String reportedByUserName,
        String description,
        String status,
        Instant createdAt,
        UUID reviewedByUserId,
        Instant reviewedAt
) {}
