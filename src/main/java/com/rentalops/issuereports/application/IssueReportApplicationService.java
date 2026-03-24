package com.rentalops.issuereports.application;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskRequest;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskResponse;
import com.rentalops.issuereports.api.dto.CreateIssueReportRequest;
import com.rentalops.issuereports.api.dto.CreateIssueReportResponse;
import com.rentalops.issuereports.api.dto.DismissIssueReportResponse;
import com.rentalops.issuereports.domain.model.IssueReport;
import com.rentalops.issuereports.domain.model.IssueReportStatus;
import com.rentalops.issuereports.infrastructure.persistence.IssueReportRepository;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.domain.model.Task;
import com.rentalops.tasks.domain.model.TaskDispatchMode;
import com.rentalops.tasks.domain.model.TaskPriority;
import com.rentalops.tasks.domain.model.TaskStatus;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Handles issue report mutation use cases:
 *   - Operator creates a new report (OPEN)
 *   - Admin converts a report to a task (OPEN → CONVERTED, creates Task atomically)
 *   - Admin dismisses a report (OPEN → DISMISSED)
 *
 * <p>All business rule enforcement lives here, never in the controller:
 *   - only OPERATOR can create issue reports
 *   - property must belong to the authenticated tenant
 *   - only ADMIN can convert or dismiss
 *   - only OPEN reports can be converted or dismissed (already-reviewed reports → 409)
 *   - convert → task creation is atomic within the same transaction
 *   - property is inherited from the issue report, never provided by the client
 */
@Service
public class IssueReportApplicationService {

    private final IssueReportRepository issueReportRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final CurrentUserProvider currentUserProvider;

    public IssueReportApplicationService(IssueReportRepository issueReportRepository,
                                         PropertyRepository propertyRepository,
                                         UserRepository userRepository,
                                         TaskRepository taskRepository,
                                         CurrentUserProvider currentUserProvider) {
        this.issueReportRepository = issueReportRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Operator creates a new issue report for a property in their tenant.
     *
     * <p>The report is always created in OPEN status. No task is generated automatically.
     */
    @Transactional
    public CreateIssueReportResponse createIssueReport(CreateIssueReportRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        if (!currentUser.isOperator()) {
            throw new ForbiddenOperationException("Only OPERATOR users can create issue reports.");
        }

        UUID tenantId = currentUser.tenantId();

        // Verify that the property belongs to this tenant.
        propertyRepository.findByIdAndTenantId(request.propertyId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found in tenant: " + request.propertyId()));

        IssueReport report = new IssueReport(
                UUID.randomUUID(),
                tenantId,
                request.propertyId(),
                currentUser.userId(),
                request.description()
        );

        issueReportRepository.save(report);

        return new CreateIssueReportResponse(
                report.getId(),
                report.getPropertyId(),
                report.getReportedByUserId(),
                report.getDescription(),
                report.getStatus().name()
        );
    }

    /**
     * Admin converts an OPEN issue report into a task.
     *
     * <p>The conversion is atomic: the issue report status changes to CONVERTED and a new
     * Task is created in the same transaction. The task inherits the property from the
     * issue report. The {@code sourceIssueReportId} on the task records the link.
     *
     * <p>Enforces:
     *   - caller must be ADMIN
     *   - issue report must belong to the admin's tenant
     *   - report must be OPEN (already reviewed → 409)
     *   - POOL tasks must have no assignee; DIRECT_ASSIGNMENT must have one
     *   - assignee must be an OPERATOR in the same tenant with matching specialization
     */
    @Transactional
    public ConvertIssueReportToTaskResponse convertToTask(UUID reportId,
                                                          ConvertIssueReportToTaskRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can convert issue reports to tasks.");
        }

        UUID tenantId = currentUser.tenantId();

        IssueReport report = issueReportRepository.findByIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Issue report not found: " + reportId));

        if (!report.isOpen()) {
            throw new BusinessConflictException(
                    "Issue report has already been reviewed: " + report.getStatus());
        }

        TaskCategory category     = parseCategory(request.category());
        TaskPriority priority     = parsePriority(request.priority());
        TaskDispatchMode dispatch = parseDispatchMode(request.dispatchMode());

        User assignee = resolveAssignee(request.assigneeId(), dispatch, tenantId, category);

        TaskStatus initialStatus = dispatch == TaskDispatchMode.POOL
                ? TaskStatus.PENDING
                : TaskStatus.ASSIGNED;

        Task task = new Task(
                UUID.randomUUID(),
                tenantId,
                report.getPropertyId(),   // inherited from the issue report
                category,
                priority,
                request.summary(),
                request.description(),
                initialStatus,
                dispatch,
                assignee != null ? assignee.getId() : null,
                currentUser.userId(),
                reportId,                 // sourceIssueReportId links task back to report
                request.estimatedHours()
        );

        taskRepository.save(task);

        // Mark the issue report as CONVERTED after the task is persisted.
        report.setStatus(IssueReportStatus.CONVERTED);
        report.setReviewedByUserId(currentUser.userId());
        report.setReviewedAt(Instant.now());
        issueReportRepository.save(report);

        return new ConvertIssueReportToTaskResponse(
                new ConvertIssueReportToTaskResponse.IssueReportSummary(
                        report.getId(),
                        report.getStatus().name()
                ),
                new ConvertIssueReportToTaskResponse.TaskSummary(
                        task.getId(),
                        task.getStatus().name(),
                        task.getDispatchMode().name(),
                        task.getSourceIssueReportId()
                )
        );
    }

    /**
     * Admin dismisses an OPEN issue report (archives it without creating a task).
     *
     * <p>Enforces:
     *   - caller must be ADMIN
     *   - issue report must belong to the admin's tenant
     *   - report must be OPEN (already reviewed → 409)
     */
    @Transactional
    public DismissIssueReportResponse dismiss(UUID reportId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can dismiss issue reports.");
        }

        UUID tenantId = currentUser.tenantId();

        IssueReport report = issueReportRepository.findByIdAndTenantId(reportId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Issue report not found: " + reportId));

        if (!report.isOpen()) {
            throw new BusinessConflictException(
                    "Issue report has already been reviewed: " + report.getStatus());
        }

        report.setStatus(IssueReportStatus.DISMISSED);
        report.setReviewedByUserId(currentUser.userId());
        report.setReviewedAt(Instant.now());
        issueReportRepository.save(report);

        return new DismissIssueReportResponse(report.getId(), report.getStatus().name());
    }

    // --- private helpers ---

    private User resolveAssignee(UUID assigneeId, TaskDispatchMode dispatchMode,
                                 UUID tenantId, TaskCategory taskCategory) {
        if (dispatchMode == TaskDispatchMode.POOL) {
            if (assigneeId != null) {
                throw new DomainValidationException("assigneeId must be null for POOL tasks.");
            }
            return null;
        }

        // DIRECT_ASSIGNMENT path
        if (assigneeId == null) {
            throw new DomainValidationException("assigneeId is required for DIRECT_ASSIGNMENT tasks.");
        }

        User assignee = userRepository.findByIdAndTenantId(assigneeId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Assignee not found in tenant: " + assigneeId));

        if (assignee.getRole() != UserRole.OPERATOR) {
            throw new BusinessConflictException("Tasks can only be assigned to OPERATOR users.");
        }

        if (assignee.getSpecializationCategory() != taskCategory) {
            throw new DomainValidationException(
                    "Operator specialization category does not match the task category.");
        }

        return assignee;
    }

    private TaskCategory parseCategory(String value) {
        try {
            return TaskCategory.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Invalid category: " + value);
        }
    }

    private TaskPriority parsePriority(String value) {
        try {
            return TaskPriority.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Invalid priority: " + value);
        }
    }

    private TaskDispatchMode parseDispatchMode(String value) {
        try {
            return TaskDispatchMode.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new DomainValidationException("Invalid dispatchMode: " + value);
        }
    }
}

