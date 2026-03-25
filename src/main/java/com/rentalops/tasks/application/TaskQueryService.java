package com.rentalops.tasks.application;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.api.dto.MyTaskItemResponse;
import com.rentalops.tasks.api.dto.TaskDetailResponse;
import com.rentalops.tasks.api.dto.TaskListItemResponse;
import com.rentalops.tasks.api.dto.TaskPoolItemResponse;
import com.rentalops.tasks.domain.model.Task;
import com.rentalops.tasks.domain.model.TaskDispatchMode;
import com.rentalops.tasks.domain.model.TaskStatus;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only query service for task projections.
 *
 * <p>Kept separate from TaskApplicationService to isolate query intent from
 * command workflows. All methods use @Transactional(readOnly = true).
 *
 * <p>Name resolution (propertyName, assigneeName) uses batch-load + in-memory
 * filtering to avoid N+1 queries on list endpoints.
 */
@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public TaskQueryService(TaskRepository taskRepository,
                            PropertyRepository propertyRepository,
                            UserRepository userRepository,
                            CurrentUserProvider currentUserProvider) {
        this.taskRepository = taskRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Returns all tasks for the current tenant.
     * Admin only — operators use listMyTasks() or listPoolTasks().
     */
    @Transactional(readOnly = true)
    public List<TaskListItemResponse> listAdminTasks() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can list all tasks.");
        }

        UUID tenantId = currentUser.tenantId();
        List<Task> tasks = taskRepository.findAllByTenantId(tenantId);

        Map<UUID, String> propertyNames = buildPropertyNameMap(tasks, tenantId);
        Map<UUID, String> userNames     = buildAssigneeNameMap(tasks, tenantId);

        return tasks.stream()
                .map(t -> new TaskListItemResponse(
                        t.getId(),
                        t.getPropertyId(),
                        propertyNames.get(t.getPropertyId()),
                        t.getCategory().name(),
                        t.getPriority().name(),
                        t.getSummary(),
                        t.getStatus().name(),
                        t.getDispatchMode().name(),
                        t.getAssigneeId(),
                        t.getAssigneeId() != null ? userNames.get(t.getAssigneeId()) : null
                ))
                .toList();
    }

    /**
     * Returns the full detail of a single task.
     * Admin sees any task in the tenant; operators see only their own assigned task.
     */
    @Transactional(readOnly = true)
    public TaskDetailResponse getTaskDetail(UUID taskId) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        UUID tenantId = currentUser.tenantId();

        Task task = taskRepository.findByIdAndTenantId(taskId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found: " + taskId));

        // Operators can only view a task where they are the assignee.
        if (currentUser.isOperator() && !currentUser.userId().equals(task.getAssigneeId())) {
            throw new ForbiddenOperationException("Access to this task is not permitted.");
        }

        String propertyName = propertyRepository.findByIdAndTenantId(task.getPropertyId(), tenantId)
                .map(Property::getName)
                .orElse(null);

        String assigneeName = resolveUserFullName(task.getAssigneeId(), tenantId);

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

    /**
     * Returns PENDING POOL tasks whose category matches the operator's specialization.
     * Operator only. Returns an empty list if the operator has no specialization set.
     */
    @Transactional(readOnly = true)
    public List<TaskPoolItemResponse> listPoolTasks() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isOperator()) {
            throw new ForbiddenOperationException("Only OPERATOR users can access the task pool.");
        }

        UUID tenantId = currentUser.tenantId();

        User operator = userRepository.findByIdAndTenantId(currentUser.userId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Operator profile not found."));

        TaskCategory category = operator.getSpecializationCategory();
        if (category == null) {
            // No specialization means no compatible tasks — empty list is the correct response.
            return List.of();
        }

        List<Task> tasks = taskRepository.findAllByTenantIdAndStatusAndDispatchModeAndCategory(
                tenantId, TaskStatus.PENDING, TaskDispatchMode.POOL, category);

        Map<UUID, String> propertyNames = buildPropertyNameMap(tasks, tenantId);

        return tasks.stream()
                .map(t -> new TaskPoolItemResponse(
                        t.getId(),
                        t.getPropertyId(),
                        propertyNames.get(t.getPropertyId()),
                        t.getCategory().name(),
                        t.getPriority().name(),
                        t.getSummary(),
                        t.getEstimatedHours()
                ))
                .toList();
    }

    /**
     * Returns all tasks assigned to the authenticated operator.
     * Covers both claimed pool tasks and direct-assignment tasks.
     */
    @Transactional(readOnly = true)
    public List<MyTaskItemResponse> listMyTasks() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isOperator()) {
            throw new ForbiddenOperationException("Only OPERATOR users can access their task list.");
        }

        UUID tenantId = currentUser.tenantId();
        List<Task> tasks = taskRepository.findAllByTenantIdAndAssigneeId(tenantId, currentUser.userId());

        Map<UUID, String> propertyNames = buildPropertyNameMap(tasks, tenantId);

        return tasks.stream()
                .map(t -> new MyTaskItemResponse(
                        t.getId(),
                        t.getPropertyId(),
                        propertyNames.get(t.getPropertyId()),
                        t.getCategory().name(),
                        t.getPriority().name(),
                        t.getSummary(),
                        t.getStatus().name()
                ))
                .toList();
    }

    // --- private helpers ---

    /**
     * Batch-loads all tenant properties and returns a propertyId -> name map
     * filtered to only the property IDs present in the task list.
     * Avoids an individual SELECT per task (N+1).
     */
    private Map<UUID, String> buildPropertyNameMap(List<Task> tasks, UUID tenantId) {
        if (tasks.isEmpty()) return Map.of();

        Set<UUID> propertyIds = tasks.stream()
                .map(Task::getPropertyId)
                .collect(Collectors.toSet());

        return propertyRepository.findAllByTenantId(tenantId).stream()
                .filter(p -> propertyIds.contains(p.getId()))
                .collect(Collectors.toMap(Property::getId, Property::getName));
    }

    /**
     * Batch-loads operators for assignee IDs present in the task list
     * and returns an assigneeId -> fullName map.
     */
    private Map<UUID, String> buildAssigneeNameMap(List<Task> tasks, UUID tenantId) {
        Set<UUID> assigneeIds = tasks.stream()
                .map(Task::getAssigneeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (assigneeIds.isEmpty()) return Map.of();

        return userRepository.findAllByTenantIdAndRole(tenantId, UserRole.OPERATOR).stream()
                .filter(u -> assigneeIds.contains(u.getId()))
                .collect(Collectors.toMap(User::getId, User::getFullName));
    }

    private String resolveUserFullName(UUID userId, UUID tenantId) {
        if (userId == null) return null;
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .map(User::getFullName)
                .orElse(null);
    }
}



