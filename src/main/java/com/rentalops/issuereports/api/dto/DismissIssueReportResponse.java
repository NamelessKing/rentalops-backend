package com.rentalops.issuereports.api.dto;

import java.util.UUID;

/**
 * Response for PATCH /issue-reports/{id}/dismiss.
 *
 * <p>Minimal confirmation: the caller only needs to know the new status
 * to update the UI state. The full detail can be fetched separately if needed.
 */
public record DismissIssueReportResponse(
        UUID id,
        String status
) {}
