package com.rentalops.tasks.domain.model;

import com.rentalops.iam.domain.model.TaskCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Central operational entity representing approved work to be executed.
 *
 * <p>A task enters the system in one of two modes:
 *   - POOL: starts PENDING, visible to compatible operators for claiming
 *   - DIRECT_ASSIGNMENT: starts ASSIGNED, immediately linked to a specific operator
 *
 * <p>Cross-module references (tenantId, propertyId, assigneeId, createdByUserId,
 * sourceIssueReportId) are stored as plain UUID fields rather than @ManyToOne
 * associations. This keeps module boundaries clean in the modular monolith and
 * prevents accidental cross-module lazy-load joins.
 *
 * <p>The {@code version} field enables optimistic locking for concurrent claim
 * protection (Slice 4). It must be present from initial schema creation.
 */
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TaskCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    @Column(nullable = false, length = 255)
    private String summary;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_mode", nullable = false, length = 30)
    private TaskDispatchMode dispatchMode;

    // Null for POOL tasks until claimed. Required for DIRECT_ASSIGNMENT at creation.
    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    // Only set when this task was generated from an IssueReport conversion (Slice 5).
    // Unique constraint ensures one issue report generates at most one task.
    @Column(name = "source_issue_report_id", unique = true)
    private UUID sourceIssueReportId;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    /**
     * Optimistic locking version counter.
     * Prevents two operators from claiming the same POOL task concurrently (Slice 4).
     * Must exist from initial schema creation — adding it later requires a migration.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Task() {
        // Required by JPA. Not for direct use in application code.
    }

    public Task(UUID id,
                UUID tenantId,
                UUID propertyId,
                TaskCategory category,
                TaskPriority priority,
                String summary,
                String description,
                TaskStatus status,
                TaskDispatchMode dispatchMode,
                UUID assigneeId,
                UUID createdByUserId,
                UUID sourceIssueReportId,
                Integer estimatedHours) {
        this.id = id;
        this.tenantId = tenantId;
        this.propertyId = propertyId;
        this.category = category;
        this.priority = priority;
        this.summary = summary;
        this.description = description;
        this.status = status;
        this.dispatchMode = dispatchMode;
        this.assigneeId = assigneeId;
        this.createdByUserId = createdByUserId;
        this.sourceIssueReportId = sourceIssueReportId;
        this.estimatedHours = estimatedHours;
    }

    // Small domain helpers so service code reads clearly without repeating enum comparisons.
    public boolean isPending()    { return status == TaskStatus.PENDING; }
    public boolean isAssigned()   { return status == TaskStatus.ASSIGNED; }
    public boolean isInProgress() { return status == TaskStatus.IN_PROGRESS; }
    public boolean isCompleted()  { return status == TaskStatus.COMPLETED; }
    public boolean isPool()       { return dispatchMode == TaskDispatchMode.POOL; }

    public UUID getId()                      { return id; }
    public UUID getTenantId()                { return tenantId; }
    public UUID getPropertyId()              { return propertyId; }
    public TaskCategory getCategory()        { return category; }
    public TaskPriority getPriority()        { return priority; }
    public String getSummary()               { return summary; }
    public String getDescription()           { return description; }
    public TaskStatus getStatus()            { return status; }
    public TaskDispatchMode getDispatchMode(){ return dispatchMode; }
    public UUID getAssigneeId()              { return assigneeId; }
    public UUID getCreatedByUserId()         { return createdByUserId; }
    public UUID getSourceIssueReportId()     { return sourceIssueReportId; }
    public Integer getEstimatedHours()       { return estimatedHours; }
    public Long getVersion()                 { return version; }
    public Instant getCreatedAt()            { return createdAt; }
    public Instant getUpdatedAt()            { return updatedAt; }

    // Setters exposed for Slice 4 (claim sets assigneeId, transitions update status).
    // tenantId, dispatchMode, createdByUserId are deliberately excluded — immutable.
    public void setStatus(TaskStatus status)    { this.status = status; }
    public void setAssigneeId(UUID assigneeId)  { this.assigneeId = assigneeId; }
}

