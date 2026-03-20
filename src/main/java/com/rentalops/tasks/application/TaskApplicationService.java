package com.rentalops.tasks.application;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.api.dto.CreateTaskRequest;
import com.rentalops.tasks.api.dto.TaskDetailResponse;
import com.rentalops.tasks.domain.model.Task;
import com.rentalops.tasks.domain.model.TaskDispatchMode;
import com.rentalops.tasks.domain.model.TaskPriority;
import com.rentalops.tasks.domain.model.TaskStatus;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles task mutation use cases.
 *
 * <p>All business rule enforcement lives here, never in the controller:
 *   - only ADMIN can create tasks
 *   - property must belong to the authenticated tenant
 *   - POOL tasks must have no assignee; DIRECT_ASSIGNMENT tasks must have one
 *   - the assignee must be an OPERATOR in the same tenant
 *   - for DIRECT_ASSIGNMENT, the assignee's specializationCategory must match the task category
 *   - initial status is determined by the service, never by the client
 */
@Service
public class TaskApplicationService {

    private final TaskRepository taskRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public TaskApplicationService(TaskRepository taskRepository,
                                  PropertyRepository propertyRepository,
                                  UserRepository userRepository,
                                  CurrentUserProvider currentUserProvider) {
        this.taskRepository = taskRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Creates a new task in POOL or DIRECT_ASSIGNMENT mode.
     *
     * <p>Returns a full TaskDetailResponse so the frontend can redirect directly
     * to the task detail page after creation without an extra GET call.
     */
    @Transactional
    public TaskDetailResponse createTask(CreateTaskRequest request) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();

        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can create tasks.");
        }

        UUID tenantId = currentUser.tenantId();

        Property property = propertyRepository.findByIdAndTenantId(request.propertyId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Property not found in tenant: " + request.propertyId()));

        TaskCategory category     = parseCategory(request.category());
        TaskPriority priority     = parsePriority(request.priority());
        TaskDispatchMode dispatch = parseDispatchMode(request.dispatchMode());

        // Category is passed so that DIRECT_ASSIGNMENT can validate the specialization match.
        User assignee = resolveAssignee(request.assigneeId(), dispatch, tenantId, category);

        // Initial status is derived entirely from dispatch mode — client cannot override this.
        TaskStatus initialStatus = dispatch == TaskDispatchMode.POOL
                ? TaskStatus.PENDING
                : TaskStatus.ASSIGNED;

        Task task = new Task(
                UUID.randomUUID(),
                tenantId,
                property.getId(),
                category,
                priority,
                request.summary(),
                request.description(),
                initialStatus,
                dispatch,
                assignee != null ? assignee.getId() : null,
                currentUser.userId(),
                null, // sourceIssueReportId: null for directly created tasks
                request.estimatedHours()
        );

        taskRepository.save(task);

        return toDetailResponse(task, property.getName(), assignee != null ? assignee.getFullName() : null);
    }

    // --- private helpers ---

    /**
     * Validates assignee constraints based on dispatch mode and returns the resolved User.
     *
     * <p>For POOL: assigneeId must be absent — no operator is pre-assigned.
     * For DIRECT_ASSIGNMENT: the operator must exist in the tenant, hold the OPERATOR role,
     * and have a specializationCategory that matches the task category.
     * The category check prevents assigning work outside the operator's expertise,
     * which would be inconsistent with how pool visibility works for the same operator.
     */
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

        // The UI enforces this as a filter, but the backend must not trust client-side filtering.
        // An operator with no specialization or a different one cannot receive this task.
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

    private TaskDetailResponse toDetailResponse(Task task, String propertyName, String assigneeName) {
        return new TaskDetailResponse(
                task.getId(),
                task.getPropertyId(),
                propertyName,
                task.getCategory().name(),
                task.getPriority().name(),
                task.getSummary(),
                task.getDescription(),
                task.getStatus().name(),
                task.getDispatchMode().name(),
                task.getEstimatedHours(),
                task.getAssigneeId(),
                assigneeName,
                task.getSourceIssueReportId()
        );
    }
}

