package com.rentalops.issuereports.api.dto;

import java.util.UUID;

/**
 * Response for PATCH /issue-reports/{id}/convert-to-task.
 *
 * <p>Returns both the updated issue report and the newly created task so the
 * caller can navigate directly to either resource without an extra round-trip.
 * The {@code task.sourceIssueReportId} field records the link back to this report.
 */
public record ConvertIssueReportToTaskResponse(
        IssueReportSummary issueReport,
        TaskSummary task
) {
    public record IssueReportSummary(UUID id, String status) {}
    public record TaskSummary(UUID id, String status, String dispatchMode, UUID sourceIssueReportId) {}
}
