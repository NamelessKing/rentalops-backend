package com.rentalops.issuereports.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response item for GET /issue-reports (Admin list view).
 *
 * <p>Includes resolved names (propertyName, reportedByUserName) so the
 * frontend can render the list without additional round-trips.
 *
 * <p>{@code createdAt} uses {@link Instant} to produce UTC "Z" timestamps in JSON.
 */
public record IssueReportListItemResponse(
        UUID id,
        UUID propertyId,
        String propertyName,
        UUID reportedByUserId,
        String reportedByUserName,
        String description,
        String status,
        Instant createdAt
) {}
