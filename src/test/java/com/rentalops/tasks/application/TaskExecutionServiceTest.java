package com.rentalops.tasks.application;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.api.dto.TaskClaimResponse;
import com.rentalops.tasks.domain.model.Task;
import com.rentalops.tasks.domain.model.TaskDispatchMode;
import com.rentalops.tasks.domain.model.TaskPriority;
import com.rentalops.tasks.domain.model.TaskStatus;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the operator task execution use cases: claim, start and complete.
 *
 * <p>Each test exercises a single business rule in isolation — no Spring context,
 * no database, no Docker required. The service's dependencies are replaced with
 * mocks so that the assertions focus purely on the rule being tested.
 */
@ExtendWith(MockitoExtension.class)
class TaskExecutionServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private PropertyRepository propertyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private TaskApplicationService taskApplicationService;

    private static final UUID TENANT_ID   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OPERATOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OTHER_ID    = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TASK_ID     = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

    private AuthenticatedUser operatorPrincipal;
    private AuthenticatedUser adminPrincipal;

    @BeforeEach
    void setUp() {
        operatorPrincipal = new AuthenticatedUser(
                OPERATOR_ID, TENANT_ID, UserRole.OPERATOR, "op@example.com", UserStatus.ACTIVE);
        adminPrincipal = new AuthenticatedUser(
                OPERATOR_ID, TENANT_ID, UserRole.ADMIN, "admin@example.com", UserStatus.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // claimTask
    // -----------------------------------------------------------------------

    @Test
    void claimTask_shouldAssignOperatorAndReturnResponse_whenTaskIsClaimable() {
        Task task = poolPendingTask(TaskCategory.CLEANING);
        User operator = operatorWithCategory(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OPERATOR_ID, TENANT_ID)).thenReturn(Optional.of(operator));
        when(taskRepository.saveAndFlush(task)).thenReturn(task);

        TaskClaimResponse response = taskApplicationService.claimTask(TASK_ID);

        assertThat(response.id()).isEqualTo(TASK_ID);
        assertThat(response.status()).isEqualTo("ASSIGNED");
        assertThat(response.assigneeId()).isEqualTo(OPERATOR_ID);
        assertThat(task.getAssigneeId()).isEqualTo(OPERATOR_ID);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
    }

    @Test
    void claimTask_shouldThrowForbidden_whenCallerIsNotOperator() {
        // Admins create tasks; operators claim them. Mixing roles here is a bug.
        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);

        assertThatThrownBy(() -> taskApplicationService.claimTask(TASK_ID))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimTask_shouldThrowConflict_whenTaskIsNotPool() {
        // DIRECT_ASSIGNMENT tasks are pre-assigned at creation; they cannot be claimed.
        Task task = directAssignmentTask(TaskStatus.PENDING, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.claimTask(TASK_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("POOL");

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimTask_shouldThrowConflict_whenTaskIsAlreadyClaimed() {
        // A POOL task that was claimed by another operator becomes ASSIGNED.
        // Attempting to claim it again must fail.
        Task task = poolTaskWithStatus(TaskStatus.ASSIGNED, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.claimTask(TASK_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("not available");

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimTask_shouldThrowConflict_whenOperatorCategoryDoesNotMatchTask() {
        // Pool visibility already filters by category, but a direct API call could
        // bypass the UI filter. The service must re-enforce the compatibility check.
        Task task = poolPendingTask(TaskCategory.GENERAL_MAINTENANCE);
        User operator = operatorWithCategory(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));
        when(userRepository.findByIdAndTenantId(OPERATOR_ID, TENANT_ID)).thenReturn(Optional.of(operator));

        assertThatThrownBy(() -> taskApplicationService.claimTask(TASK_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("specialization");

        verify(taskRepository, never()).saveAndFlush(any());
    }

    @Test
    void claimTask_shouldThrowNotFound_whenTaskDoesNotExistInTenant() {
        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskApplicationService.claimTask(TASK_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // startTask
    // -----------------------------------------------------------------------

    @Test
    void startTask_shouldTransitionToInProgress_whenAssigneeStarts() {
        Task task = assignedTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        taskApplicationService.startTask(TASK_ID);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        verify(taskRepository).save(task);
    }

    @Test
    void startTask_shouldThrowForbidden_whenCallerIsNotTheAssignee() {
        // Another operator cannot start a task they do not own.
        AuthenticatedUser otherOperator = new AuthenticatedUser(
                OTHER_ID, TENANT_ID, UserRole.OPERATOR, "other@example.com", UserStatus.ACTIVE);
        Task task = assignedTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(otherOperator);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.startTask(TASK_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("assigned operator");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void startTask_shouldThrowConflict_whenTaskIsNotAssigned() {
        // Starting a task that is already IN_PROGRESS or COMPLETED is an invalid transition.
        Task task = inProgressTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.startTask(TASK_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("cannot be started");

        verify(taskRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // completeTask
    // -----------------------------------------------------------------------

    @Test
    void completeTask_shouldTransitionToCompleted_whenAssigneeCompletes() {
        Task task = inProgressTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        taskApplicationService.completeTask(TASK_ID);

        assertThat(task.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        verify(taskRepository).save(task);
    }

    @Test
    void completeTask_shouldThrowForbidden_whenCallerIsNotTheAssignee() {
        AuthenticatedUser otherOperator = new AuthenticatedUser(
                OTHER_ID, TENANT_ID, UserRole.OPERATOR, "other@example.com", UserStatus.ACTIVE);
        Task task = inProgressTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(otherOperator);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.completeTask(TASK_ID))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessageContaining("assigned operator");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void completeTask_shouldThrowConflict_whenTaskIsNotInProgress() {
        // A task must be IN_PROGRESS before it can be completed.
        // Jumping from ASSIGNED directly to COMPLETED is not a valid transition.
        Task task = assignedTask(OPERATOR_ID, TaskCategory.CLEANING);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(taskRepository.findByIdAndTenantId(TASK_ID, TENANT_ID)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> taskApplicationService.completeTask(TASK_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("cannot be completed");

        verify(taskRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // Helpers — build minimal Task and User instances for each scenario
    // -----------------------------------------------------------------------

    private Task poolPendingTask(TaskCategory category) {
        return new Task(TASK_ID, TENANT_ID, UUID.randomUUID(), category,
                TaskPriority.MEDIUM, "Test task", null,
                TaskStatus.PENDING, TaskDispatchMode.POOL,
                null, UUID.randomUUID(), null, null);
    }

    private Task poolTaskWithStatus(TaskStatus status, TaskCategory category) {
        return new Task(TASK_ID, TENANT_ID, UUID.randomUUID(), category,
                TaskPriority.MEDIUM, "Test task", null,
                status, TaskDispatchMode.POOL,
                OPERATOR_ID, UUID.randomUUID(), null, null);
    }

    private Task directAssignmentTask(TaskStatus status, TaskCategory category) {
        return new Task(TASK_ID, TENANT_ID, UUID.randomUUID(), category,
                TaskPriority.MEDIUM, "Test task", null,
                status, TaskDispatchMode.DIRECT_ASSIGNMENT,
                OPERATOR_ID, UUID.randomUUID(), null, null);
    }

    private Task assignedTask(UUID assigneeId, TaskCategory category) {
        return new Task(TASK_ID, TENANT_ID, UUID.randomUUID(), category,
                TaskPriority.MEDIUM, "Test task", null,
                TaskStatus.ASSIGNED, TaskDispatchMode.POOL,
                assigneeId, UUID.randomUUID(), null, null);
    }

    private Task inProgressTask(UUID assigneeId, TaskCategory category) {
        return new Task(TASK_ID, TENANT_ID, UUID.randomUUID(), category,
                TaskPriority.MEDIUM, "Test task", null,
                TaskStatus.IN_PROGRESS, TaskDispatchMode.POOL,
                assigneeId, UUID.randomUUID(), null, null);
    }

    private User operatorWithCategory(UUID id, TaskCategory category) {
        User user = new User(id, TENANT_ID, "Test Operator", "op@example.com",
                "hashed-pw", UserRole.OPERATOR, UserStatus.ACTIVE);
        user.setSpecializationCategory(category);
        return user;
    }
}

