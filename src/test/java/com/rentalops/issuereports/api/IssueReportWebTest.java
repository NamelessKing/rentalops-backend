package com.rentalops.issuereports.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskResponse;
import com.rentalops.issuereports.api.dto.CreateIssueReportResponse;
import com.rentalops.issuereports.api.dto.DismissIssueReportResponse;
import com.rentalops.issuereports.api.dto.IssueReportDetailResponse;
import com.rentalops.issuereports.api.dto.IssueReportListItemResponse;
import com.rentalops.issuereports.application.IssueReportApplicationService;
import com.rentalops.issuereports.application.IssueReportQueryService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the issue report HTTP contract.
 *
 * <p>Business logic is covered by mocking the services.
 * Only HTTP binding, response shape and error-status mapping are verified here.
 */
class IssueReportWebTest {

    private MockMvc mockMvc;
    private final IssueReportApplicationService applicationService =
            mock(IssueReportApplicationService.class);
    private final IssueReportQueryService queryService =
            mock(IssueReportQueryService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final UUID TENANT_ID  = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID ADMIN_ID   = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID OP_ID      = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID REPORT_ID  = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID PROP_ID    = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
    private static final UUID TASK_ID    = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new IssueReportController(applicationService, queryService))
                .setControllerAdvice(new ApiExceptionHandler())
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
                OP_ID, TENANT_ID, UserRole.OPERATOR, "op@test.com", UserStatus.ACTIVE);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // --- GET /issue-reports ---

    @Test
    void listIssueReports_adminSuccess_returns200() throws Exception {
        authenticateAsAdmin();
        IssueReportListItemResponse item = new IssueReportListItemResponse(
                REPORT_ID, PROP_ID, "Loft", OP_ID, "Giulia", "Rubinetto che perde", "OPEN",
                LocalDateTime.of(2026, 3, 11, 10, 0));
        when(queryService.listIssueReports()).thenReturn(List.of(item));

        mockMvc.perform(get("/issue-reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].propertyName").value("Loft"));
    }

    @Test
    void listIssueReports_notAdmin_returns403() throws Exception {
        authenticateAsAdmin();
        when(queryService.listIssueReports())
                .thenThrow(new ForbiddenOperationException("Only ADMIN users can list issue reports."));

        mockMvc.perform(get("/issue-reports"))
                .andExpect(status().isForbidden());
    }

    // --- POST /issue-reports ---

    @Test
    void createIssueReport_operatorSuccess_returns201() throws Exception {
        authenticateAsOperator();
        CreateIssueReportResponse response = new CreateIssueReportResponse(
                REPORT_ID, PROP_ID, OP_ID, "Rubinetto che perde", "OPEN");
        when(applicationService.createIssueReport(any())).thenReturn(response);

        String body = objectMapper.writeValueAsString(
                Map.of("propertyId", PROP_ID.toString(), "description", "Rubinetto che perde"));

        mockMvc.perform(post("/issue-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createIssueReport_missingDescription_returns400() throws Exception {
        authenticateAsOperator();

        String body = objectMapper.writeValueAsString(Map.of("propertyId", PROP_ID.toString()));

        mockMvc.perform(post("/issue-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createIssueReport_notOperator_returns403() throws Exception {
        authenticateAsOperator();
        when(applicationService.createIssueReport(any()))
                .thenThrow(new ForbiddenOperationException("Only OPERATOR users can create issue reports."));

        String body = objectMapper.writeValueAsString(
                Map.of("propertyId", PROP_ID.toString(), "description", "Problem"));

        mockMvc.perform(post("/issue-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void createIssueReport_propertyNotFound_returns404() throws Exception {
        authenticateAsOperator();
        when(applicationService.createIssueReport(any()))
                .thenThrow(new ResourceNotFoundException("Property not found in tenant: " + PROP_ID));

        String body = objectMapper.writeValueAsString(
                Map.of("propertyId", PROP_ID.toString(), "description", "Problem"));

        mockMvc.perform(post("/issue-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // --- GET /issue-reports/{id} ---

    @Test
    void getDetail_adminSuccess_returns200() throws Exception {
        authenticateAsAdmin();
        IssueReportDetailResponse detail = new IssueReportDetailResponse(
                REPORT_ID, PROP_ID, "Loft", OP_ID, "Giulia", "Rubinetto", "OPEN",
                LocalDateTime.of(2026, 3, 11, 10, 0), null, null);
        when(queryService.getIssueReportDetail(REPORT_ID)).thenReturn(detail);

        mockMvc.perform(get("/issue-reports/{id}", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist());
    }

    @Test
    void getDetail_notFound_returns404() throws Exception {
        authenticateAsAdmin();
        when(queryService.getIssueReportDetail(REPORT_ID))
                .thenThrow(new ResourceNotFoundException("Issue report not found: " + REPORT_ID));

        mockMvc.perform(get("/issue-reports/{id}", REPORT_ID))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /issue-reports/{id}/convert-to-task ---

    @Test
    void convertToTask_adminSuccess_returns200() throws Exception {
        authenticateAsAdmin();
        ConvertIssueReportToTaskResponse response = new ConvertIssueReportToTaskResponse(
                new ConvertIssueReportToTaskResponse.IssueReportSummary(REPORT_ID, "CONVERTED"),
                new ConvertIssueReportToTaskResponse.TaskSummary(TASK_ID, "PENDING", "POOL", REPORT_ID)
        );
        when(applicationService.convertToTask(eq(REPORT_ID), any())).thenReturn(response);

        String body = objectMapper.writeValueAsString(Map.of(
                "category", "PLUMBING",
                "priority", "HIGH",
                "summary", "Fix tap",
                "dispatchMode", "POOL"
        ));

        mockMvc.perform(patch("/issue-reports/{id}/convert-to-task", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueReport.status").value("CONVERTED"))
                .andExpect(jsonPath("$.task.sourceIssueReportId").value(REPORT_ID.toString()));
    }

    @Test
    void convertToTask_alreadyReviewed_returns409() throws Exception {
        authenticateAsAdmin();
        when(applicationService.convertToTask(eq(REPORT_ID), any()))
                .thenThrow(new BusinessConflictException("Issue report has already been reviewed: CONVERTED"));

        String body = objectMapper.writeValueAsString(Map.of(
                "category", "PLUMBING", "priority", "HIGH",
                "summary", "Fix tap", "dispatchMode", "POOL"
        ));

        mockMvc.perform(patch("/issue-reports/{id}/convert-to-task", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void convertToTask_notFound_returns404() throws Exception {
        authenticateAsAdmin();
        when(applicationService.convertToTask(eq(REPORT_ID), any()))
                .thenThrow(new ResourceNotFoundException("Issue report not found: " + REPORT_ID));

        String body = objectMapper.writeValueAsString(Map.of(
                "category", "PLUMBING", "priority", "HIGH",
                "summary", "Fix tap", "dispatchMode", "POOL"
        ));

        mockMvc.perform(patch("/issue-reports/{id}/convert-to-task", REPORT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    // --- PATCH /issue-reports/{id}/dismiss ---

    @Test
    void dismiss_adminSuccess_returns200() throws Exception {
        authenticateAsAdmin();
        DismissIssueReportResponse response = new DismissIssueReportResponse(REPORT_ID, "DISMISSED");
        when(applicationService.dismiss(REPORT_ID)).thenReturn(response);

        mockMvc.perform(patch("/issue-reports/{id}/dismiss", REPORT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    @Test
    void dismiss_alreadyReviewed_returns409() throws Exception {
        authenticateAsAdmin();
        when(applicationService.dismiss(REPORT_ID))
                .thenThrow(new BusinessConflictException("Issue report has already been reviewed: DISMISSED"));

        mockMvc.perform(patch("/issue-reports/{id}/dismiss", REPORT_ID))
                .andExpect(status().isConflict());
    }

    @Test
    void dismiss_notFound_returns404() throws Exception {
        authenticateAsAdmin();
        when(applicationService.dismiss(REPORT_ID))
                .thenThrow(new ResourceNotFoundException("Issue report not found: " + REPORT_ID));

        mockMvc.perform(patch("/issue-reports/{id}/dismiss", REPORT_ID))
                .andExpect(status().isNotFound());
    }
}
