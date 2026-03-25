package com.rentalops.tasks.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.tasks.api.dto.CreateTaskRequest;
import com.rentalops.tasks.api.dto.TaskDetailResponse;
import com.rentalops.tasks.application.TaskApplicationService;
import com.rentalops.tasks.application.TaskQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for POST /tasks.
 *
 * <p>Uses standaloneSetup to test the HTTP contract in isolation:
 * status codes, JSON shape and validation error format.
 * Business logic is covered by mocking TaskApplicationService.
 */
class TaskCreationWebTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskApplicationService taskApplicationService = mock(TaskApplicationService.class);
    private final TaskQueryService taskQueryService = mock(TaskQueryService.class);

    private static final UUID TENANT_ID   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID    = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PROPERTY_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID OPERATOR_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
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

    // Simulates a logged-in ADMIN in the security context for standaloneSetup tests.
    // Without this, the filter chain is bypassed but the service mock still receives calls.
    private void authenticateAsAdmin() {
        AuthenticatedUser principal = new AuthenticatedUser(
                ADMIN_ID, TENANT_ID, UserRole.ADMIN, "admin@test.com", UserStatus.ACTIVE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void createTask_pool_shouldReturn201WithPendingStatus() throws Exception {
        authenticateAsAdmin();

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "Pulizia post checkout", "Pulizia completa entro le 14:00",
                "POOL", null, 2);

        TaskDetailResponse response = new TaskDetailResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "CLEANING", "HIGH",
                "Pulizia post checkout", "Pulizia completa entro le 14:00",
                "PENDING", "POOL", 2, null, null, null);

        when(taskApplicationService.createTask(any(CreateTaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.dispatchMode").value("POOL"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist());
    }

    @Test
    void createTask_directAssignment_shouldReturn201WithAssignedStatus() throws Exception {
        authenticateAsAdmin();

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "Pulizia post checkout", null,
                "DIRECT_ASSIGNMENT", OPERATOR_ID, null);

        TaskDetailResponse response = new TaskDetailResponse(
                TASK_ID, PROPERTY_ID, "Milano Loft",
                "CLEANING", "HIGH",
                "Pulizia post checkout", null,
                "ASSIGNED", "DIRECT_ASSIGNMENT", null,
                OPERATOR_ID, "Giulia Verdi", null);

        when(taskApplicationService.createTask(any(CreateTaskRequest.class))).thenReturn(response);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.dispatchMode").value("DIRECT_ASSIGNMENT"))
                .andExpect(jsonPath("$.assigneeId").value(OPERATOR_ID.toString()))
                .andExpect(jsonPath("$.assigneeName").value("Giulia Verdi"));
    }

    @Test
    void createTask_directAssignment_withoutAssigneeId_shouldReturn400() throws Exception {
        authenticateAsAdmin();

        // assigneeId is null but dispatchMode is DIRECT_ASSIGNMENT — service rejects this
        when(taskApplicationService.createTask(any(CreateTaskRequest.class)))
                .thenThrow(new DomainValidationException("assigneeId is required for DIRECT_ASSIGNMENT tasks."));

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "Pulizia", null, "DIRECT_ASSIGNMENT", null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTask_missingPropertyId_shouldReturn400() throws Exception {
        authenticateAsAdmin();

        // propertyId is @NotNull — Bean Validation fires before the service is called
        CreateTaskRequest request = new CreateTaskRequest(
                null, "CLEANING", "HIGH",
                "Pulizia", null, "POOL", null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.propertyId").exists());
    }

    @Test
    void createTask_missingSummary_shouldReturn400() throws Exception {
        authenticateAsAdmin();

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "", null, "POOL", null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.summary").exists());
    }

    @Test
    void createTask_byNonAdmin_shouldReturn403() throws Exception {
        authenticateAsAdmin();

        when(taskApplicationService.createTask(any(CreateTaskRequest.class)))
                .thenThrow(new ForbiddenOperationException("Only ADMIN users can create tasks."));

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "Pulizia", null, "POOL", null, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void createTask_directAssignment_categoryMismatch_shouldReturn400() throws Exception {
        authenticateAsAdmin();

        // Task category is CLEANING but the operator specializes in PLUMBING — backend rejects this.
        when(taskApplicationService.createTask(any(CreateTaskRequest.class)))
                .thenThrow(new DomainValidationException(
                        "Operator specialization category does not match the task category."));

        CreateTaskRequest request = new CreateTaskRequest(
                PROPERTY_ID, "CLEANING", "HIGH",
                "Pulizia", null, "DIRECT_ASSIGNMENT", OPERATOR_ID, null);

        mockMvc.perform(post("/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Operator specialization category does not match the task category."));
    }
}
