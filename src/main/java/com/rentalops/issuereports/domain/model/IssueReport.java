package com.rentalops.issuereports.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a field-level problem report created by an Operator.
 *
 * <p>An IssueReport is not a task. It represents an observed problem and requires
 * Admin review before any operational work is created. The Admin may:
 *   - convert the report into a task (OPEN → CONVERTED)
 *   - archive it without action (OPEN → DISMISSED)
 *
 * <p>Cross-module references (tenantId, propertyId, reportedByUserId,
 * reviewedByUserId) are stored as plain UUID fields to keep module boundaries
 * clean in the modular monolith.
 *
 * <p>The relationship to a generated task is modelled unidirectionally via
 * {@code Task.sourceIssueReportId}. IssueReport does not hold a task reference.
 */
@Entity
@Table(name = "issue_reports")
public class IssueReport {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false, updatable = false)
    private UUID propertyId;

    @Column(name = "reported_by_user_id", nullable = false, updatable = false)
    private UUID reportedByUserId;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IssueReportStatus status;

    // Null until an Admin reviews the report.
    @Column(name = "reviewed_by_user_id")
    private UUID reviewedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Set when the Admin performs the review action (convert or dismiss).
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected IssueReport() {
        // Required by JPA. Not for direct use in application code.
    }

    public IssueReport(UUID id,
                       UUID tenantId,
                       UUID propertyId,
                       UUID reportedByUserId,
                       String description) {
        this.id = id;
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.reportedByUserId = reportedByUserId;
        this.description = description;
        this.status = IssueReportStatus.OPEN;
    }

    public boolean isOpen()      { return status == IssueReportStatus.OPEN; }
    public boolean isConverted() { return status == IssueReportStatus.CONVERTED; }
    public boolean isDismissed() { return status == IssueReportStatus.DISMISSED; }

    public UUID getId()                 { return id; }
    public UUID getTenantId()           { return tenantId; }
    public UUID getPropertyId()         { return propertyId; }
    public UUID getReportedByUserId()   { return reportedByUserId; }
    public String getDescription()      { return description; }
    public IssueReportStatus getStatus(){ return status; }
    public UUID getReviewedByUserId()   { return reviewedByUserId; }
    public Instant getCreatedAt()       { return createdAt; }
    public Instant getReviewedAt()      { return reviewedAt; }

    // Setters for review transitions — only reviewedByUserId, reviewedAt and status are mutable.
    public void setStatus(IssueReportStatus status)       { this.status = status; }
    public void setReviewedByUserId(UUID reviewedByUserId){ this.reviewedByUserId = reviewedByUserId; }
    public void setReviewedAt(Instant reviewedAt)         { this.reviewedAt = reviewedAt; }
}

