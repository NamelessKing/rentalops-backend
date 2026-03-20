package com.rentalops.tasks.api;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.tasks.api.dto.MyTaskItemResponse;
import com.rentalops.tasks.api.dto.TaskDetailResponse;
import com.rentalops.tasks.api.dto.TaskListItemResponse;
import com.rentalops.tasks.api.dto.TaskPoolItemResponse;
import com.rentalops.tasks.application.TaskApplicationService;
import com.rentalops.tasks.application.TaskQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for GET /tasks, GET /tasks/{id}, GET /tasks/pool, GET /tasks/my.
 *
 * <p>Standalone setup: tests the HTTP contract in isolation — status codes,
 * JSON shape and error handling. Business logic is covered by mocking
 * TaskQueryService.
 */
class TaskQueryWebTest {

    private MockMvc mockMvc;
    private final TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
    private final TaskQueryService taskQueryService = mock(TaskQueryService.class);

    private static final UUID TENANT_ID   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID    = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OPERATOR_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PROPERTY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID TASK_ID     = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new TaskController(taskApplicationService, taskQueryService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private void authenticateAsAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(
                ADMIN_ID, TENANT_ID, UserRole.ADMIN, "admin@test.com", UserStatus.ACTIVE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateAsOperator() {
        AuthenticatedUser principal = new AuthenticatedUser(
                OPERATOR_ID, TENANT_ID, UserRole.OPERATOR, "op@test.com", UserStatus.ACTIVE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- GET /tasks ---

    @Test
    void listAdminTasks_shouldReturn200_withCorrectShape() throws Exception {
        authenticateAsAdmin();

        TaskListItemResponse item = new TaskListItemResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "CLEANING", "HIGH", "Pulizia post checkout",
                "PENDING", "POOL", null, null);

        when(taskQueryService.listAdminTasks()).thenReturn(List.of(item));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$[0].propertyName").value("Milano Loft"))
                .andExpect(jsonPath("$[0].category").value("CLEANING"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].dispatchMode").value("POOL"))
                .andExpect(jsonPath("$[0].assigneeId").doesNotExist())
                .andExpect(jsonPath("$[0].assigneeName").doesNotExist());
    }

    @Test
    void listAdminTasks_shouldReturn200_emptyListWhenNoTasks() throws Exception {
        authenticateAsAdmin();

        when(taskQueryService.listAdminTasks()).thenReturn(List.of());

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listAdminTasks_shouldReturn403_whenCalledByOperator() throws Exception {
        authenticateAsOperator();

        when(taskQueryService.listAdminTasks())
                .thenThrow(new ForbiddenOperationException("Only ADMIN users can list all tasks."));

        mockMvc.perform(get("/tasks"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    // --- GET /tasks/{id} ---

    @Test
    void getTaskDetail_shouldReturn200_withFullShape() throws Exception {
        authenticateAsAdmin();

        TaskDetailResponse detail = new TaskDetailResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "CLEANING", "HIGH",
                "Pulizia post checkout", "Pulizia completa entro le 14:00",
                "ASSIGNED", "DIRECT_ASSIGNMENT", 2,
                OPERATOR_ID, "Giulia Verdi", null);

        when(taskQueryService.getTaskDetail(TASK_ID)).thenReturn(detail);

        mockMvc.perform(get("/tasks/" + TASK_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.propertyName").value("Milano Loft"))
                .andExpect(jsonPath("$.category").value("CLEANING"))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.dispatchMode").value("DIRECT_ASSIGNMENT"))
                .andExpect(jsonPath("$.estimatedHours").value(2))
                .andExpect(jsonPath("$.assigneeId").value(OPERATOR_ID.toString()))
                .andExpect(jsonPath("$.assigneeName").value("Giulia Verdi"))
                .andExpect(jsonPath("$.sourceIssueReportId").doesNotExist());
    }

    @Test
    void getTaskDetail_shouldReturn404_whenTaskNotFound() throws Exception {
        authenticateAsAdmin();

        when(taskQueryService.getTaskDetail(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Task not found: " + TASK_ID));

        mockMvc.perform(get("/tasks/" + TASK_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getTaskDetail_shouldReturn403_whenOperatorIsNotAssignee() throws Exception {
        authenticateAsOperator();

        when(taskQueryService.getTaskDetail(TASK_ID))
                .thenThrow(new ForbiddenOperationException("Access to this task is not permitted."));

        mockMvc.perform(get("/tasks/" + TASK_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Access to this task is not permitted."));
    }

    // --- GET /tasks/pool ---

    @Test
    void listPoolTasks_shouldReturn200_withCorrectShape() throws Exception {
        authenticateAsOperator();

        TaskPoolItemResponse item = new TaskPoolItemResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "CLEANING", "HIGH", "Pulizia post checkout", 2);

        when(taskQueryService.listPoolTasks()).thenReturn(List.of(item));

        mockMvc.perform(get("/tasks/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$[0].propertyName").value("Milano Loft"))
                .andExpect(jsonPath("$[0].category").value("CLEANING"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].summary").value("Pulizia post checkout"))
                .andExpect(jsonPath("$[0].estimatedHours").value(2));
    }

    @Test
    void listPoolTasks_shouldReturn200_emptyListWhenNoCompatibleTasks() throws Exception {
        authenticateAsOperator();

        when(taskQueryService.listPoolTasks()).thenReturn(List.of());

        mockMvc.perform(get("/tasks/pool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listPoolTasks_shouldReturn403_whenCalledByAdmin() throws Exception {
        authenticateAsAdmin();

        when(taskQueryService.listPoolTasks())
                .thenThrow(new ForbiddenOperationException("Only OPERATOR users can access the task pool."));

        mockMvc.perform(get("/tasks/pool"))
                .andExpect(status().isForbidden());
    }

    // --- GET /tasks/my ---

    @Test
    void listMyTasks_shouldReturn200_withCorrectShape() throws Exception {
        authenticateAsOperator();

        MyTaskItemResponse item = new MyTaskItemResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "PLUMBING", "CRITICAL", "Riparare perdita bagno", "ASSIGNED");

        when(taskQueryService.listMyTasks()).thenReturn(List.of(item));

        mockMvc.perform(get("/tasks/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$[0].propertyName").value("Milano Loft"))
                .andExpect(jsonPath("$[0].category").value("PLUMBING"))
                .andExpect(jsonPath("$[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$[0].summary").value("Riparare perdita bagno"))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));
    }

    @Test
    void listMyTasks_shouldReturn200_emptyListWhenNoAssignedTasks() throws Exception {
        authenticateAsOperator();

        when(taskQueryService.listMyTasks()).thenReturn(List.of());

        mockMvc.perform(get("/tasks/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listMyTasks_shouldReturn403_whenCalledByAdmin() throws Exception {
        authenticateAsAdmin();

        when(taskQueryService.listMyTasks())
                .thenThrow(new ForbiddenOperationException("Only OPERATOR users can access their task list."));

        mockMvc.perform(get("/tasks/my"))
                .andExpect(status().isForbidden());
    }
}

