package com.rentalops.dashboard.api.dto;

/**
 * Response shape for GET /dashboard/admin-summary.
 *
 * <p>The nested records {@link TaskCounts} and {@link IssueReportCounts} mirror the
 * JSON nesting documented in the API contract. Field names are camelCase and Jackson
 * serialises them as-is, so no @JsonProperty overrides are needed.
 */
public record AdminDashboardResponse(
        long propertiesCount,
        long operatorsCount,
        TaskCounts taskCounts,
        IssueReportCounts issueReportCounts
) {

    /**
     * Per-status breakdown of tasks in the tenant.
     * All counts default to zero for a tenant with no tasks.
     */
    public record TaskCounts(
            long pending,
            long assigned,
            long inProgress,
            long completed
    ) {}

    /**
     * Per-status breakdown of issue reports in the tenant.
     * All counts default to zero for a tenant with no issue reports.
     */
    public record IssueReportCounts(
            long open,
            long converted,
            long dismissed
    ) {}
}

