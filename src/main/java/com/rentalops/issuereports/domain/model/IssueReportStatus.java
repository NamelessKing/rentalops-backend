package com.rentalops.issuereports.domain.model;

/**
 * Lifecycle states for an IssueReport.
 *
 * <p>Valid transitions:
 *   - OPEN → CONVERTED (admin converts to task)
 *   - OPEN → DISMISSED (admin archives without creating a task)
 *
 * <p>Once an issue report leaves OPEN it cannot be re-opened in the MVP.
 */
public enum IssueReportStatus {
    OPEN,
    CONVERTED,
    DISMISSED
}

