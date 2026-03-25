package com.rentalops.tasks.api;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.tasks.api.dto.TaskClaimResponse;
import com.rentalops.tasks.application.TaskApplicationService;
import com.rentalops.tasks.application.TaskQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for Slice 4 task execution endpoints:
 * POST /tasks/{id}/claim, PATCH /tasks/{id}/start, PATCH /tasks/{id}/complete.
 *
 * <p>Business logic is covered by mocking TaskApplicationService.
 * Only HTTP contract and error mapping are verified here.
 */
class TaskExecutionWebTest {

    private MockMvc mockMvc;
    private final TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
    private final TaskQueryService taskQueryService = mock(TaskQueryService.class);

    private static final UUID TENANT_ID   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OPERATOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID TASK_ID     = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskApplicationService, taskQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private void authenticateAsOperator() {
        AuthenticatedUser principal = new AuthenticatedUser(
                OPERATOR_ID, TENANT_ID, UserRole.OPERATOR, "op@test.com", UserStatus.ACTIVE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- POST /tasks/{id}/claim ---

    @Test
    void claimTask_success_shouldReturn200WithClaimResponse() throws Exception {
        authenticateAsOperator();

        TaskClaimResponse response = new TaskClaimResponse(TASK_ID, "ASSIGNED", OPERATOR_ID);
        when(taskApplicationService.claimTask(TASK_ID)).thenReturn(response);

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigneeId").value(OPERATOR_ID.toString()));
    }

    @Test
    void claimTask_serviceThrowsForbidden_shouldReturn403() throws Exception {
        authenticateAsOperator();

        when(taskApplicationService.claimTask(TASK_ID))
                .thenThrow(new ForbiddenOperationException("Only OPERATOR users can claim pool tasks."));

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Only OPERATOR users can claim pool tasks."));
    }

    @Test
    void claimTask_taskNotFound_shouldReturn404() throws Exception {
        authenticateAsOperator();

        when(taskApplicationService.claimTask(TASK_ID))
                .thenThrow(new ResourceNotFoundException("Task not found: " + TASK_ID));

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void claimTask_alreadyClaimed_shouldReturn409() throws Exception {
        authenticateAsOperator();

        when(taskApplicationService.claimTask(TASK_ID))
                .thenThrow(new BusinessConflictException("Task already claimed by another operator."));

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Task already claimed by another operator."));
    }

    @Test
    void claimTask_taskNotPending_shouldReturn409() throws Exception {
        authenticateAsOperator();

        when(taskApplicationService.claimTask(TASK_ID))
                .thenThrow(new BusinessConflictException("Task is not available for claiming."));

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void claimTask_categoryMismatch_shouldReturn409() throws Exception {
        authenticateAsOperator();

        // The operator's specialization does not match the task category.
        when(taskApplicationService.claimTask(TASK_ID))
                .thenThrow(new BusinessConflictException(
                        "Operator specialization does not match task category."));

        mockMvc.perform(post("/tasks/{id}/claim", TASK_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Operator specialization does not match task category."));
    }

    // --- PATCH /tasks/{id}/start ---

    @Test
    void startTask_success_shouldReturn204() throws Exception {
        authenticateAsOperator();

        doNothing().when(taskApplicationService).startTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/start", TASK_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void startTask_notAssignee_shouldReturn403() throws Exception {
        authenticateAsOperator();

        doThrow(new ForbiddenOperationException("Only the assigned operator can start this task."))
                .when(taskApplicationService).startTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/start", TASK_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void startTask_taskNotFound_shouldReturn404() throws Exception {
        authenticateAsOperator();

        doThrow(new ResourceNotFoundException("Task not found: " + TASK_ID))
                .when(taskApplicationService).startTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/start", TASK_ID))
                .andExpect(status().isNotFound());
    }

    @Test
    void startTask_invalidTransition_shouldReturn409() throws Exception {
        authenticateAsOperator();

        doThrow(new BusinessConflictException("Task cannot be started from its current state: PENDING"))
                .when(taskApplicationService).startTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/start", TASK_ID))
                .andExpect(status().isConflict());
    }

    // --- PATCH /tasks/{id}/complete ---

    @Test
    void completeTask_success_shouldReturn204() throws Exception {
        authenticateAsOperator();

        doNothing().when(taskApplicationService).completeTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void completeTask_notAssignee_shouldReturn403() throws Exception {
        authenticateAsOperator();

        doThrow(new ForbiddenOperationException("Only the assigned operator can complete this task."))
                .when(taskApplicationService).completeTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isForbidden());
    }

    @Test
    void completeTask_invalidTransition_shouldReturn409() throws Exception {
        authenticateAsOperator();

        doThrow(new BusinessConflictException("Task cannot be completed from its current state: ASSIGNED"))
                .when(taskApplicationService).completeTask(TASK_ID);

        mockMvc.perform(patch("/tasks/{id}/complete", TASK_ID))
                .andExpect(status().isConflict());
    }
}
