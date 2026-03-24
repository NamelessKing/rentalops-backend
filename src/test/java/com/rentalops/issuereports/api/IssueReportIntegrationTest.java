package com.rentalops.issuereports.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.infrastructure.persistence.TenantRepository;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.issuereports.infrastructure.persistence.IssueReportRepository;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the issue report lifecycle.
 *
 * <p>Scenarios covered:
 *   1. Full lifecycle: operator creates → admin lists/details → admin converts to task → task linked
 *   2. Dismiss: admin dismisses an OPEN report → DISMISSED, no task created
 *   3. Double review guard: a CONVERTED report cannot be dismissed (409)
 *
 * <p>Requires Docker for Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class IssueReportIntegrationTest {

    @SuppressWarnings("unused")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private PropertyRepository propertyRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private IssueReportRepository issueReportRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        issueReportRepository.deleteAll();
        taskRepository.deleteAll();
        propertyRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    /**
     * Full lifecycle: operator creates report, admin lists and gets detail,
     * admin converts to task (POOL), report is CONVERTED, task links back via sourceIssueReportId.
     */
    @Test
    void operatorCreatesReport_adminConvertsToPoolTask() throws Exception {

        // 1. Admin setup
        String adminJwt = registerAdminAndLogin(
                "Admin Convert", "admin@convert.com", "Secret123!", "Convert Tenant");
        String propertyId = createProperty(adminJwt, "CNV-001", "Convert Loft", "Via Test 1", "Roma");

        // 2. Operator setup
        createOperator(adminJwt, "Op Convert", "op@convert.com", "Operatore123!", "PLUMBING");
        String operatorJwt = login("op@convert.com", "Operatore123!");

        // 3. Operator creates issue report
        MvcResult createResult = mockMvc.perform(post("/issue-reports")
                        .header("Authorization", "Bearer " + operatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "description": "Rubinetto che perde in bagno"
                            }
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.propertyId").value(propertyId))
                .andReturn();

        String reportId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // 4. Admin lists issue reports — should contain the new report
        mockMvc.perform(get("/issue-reports")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(reportId))
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].propertyName").value("Convert Loft"));

        // 5. Admin gets detail
        mockMvc.perform(get("/issue-reports/{id}", reportId)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.reviewedByUserId").doesNotExist());

        // 6. Admin converts the report to a POOL task
        MvcResult convertResult = mockMvc.perform(
                        patch("/issue-reports/{id}/convert-to-task", reportId)
                                .header("Authorization", "Bearer " + adminJwt)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "category": "PLUMBING",
                                      "priority": "HIGH",
                                      "summary": "Riparare rubinetto",
                                      "dispatchMode": "POOL"
                                    }
                                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueReport.id").value(reportId))
                .andExpect(jsonPath("$.issueReport.status").value("CONVERTED"))
                .andExpect(jsonPath("$.task.dispatchMode").value("POOL"))
                .andExpect(jsonPath("$.task.sourceIssueReportId").value(reportId))
                .andReturn();

        String taskId = objectMapper.readTree(
                convertResult.getResponse().getContentAsString()).get("task").get("id").asText();

        // 7. Verify DB state: report is CONVERTED, task exists and links back
        assertThat(issueReportRepository.findById(UUID.fromString(reportId)))
                .isPresent()
                .hasValueSatisfying(r -> {
                    assertThat(r.getStatus().name()).isEqualTo("CONVERTED");
                    assertThat(r.getReviewedByUserId()).isNotNull();
                    assertThat(r.getReviewedAt()).isNotNull();
                });

        assertThat(taskRepository.findById(UUID.fromString(taskId)))
                .isPresent()
                .hasValueSatisfying(t -> {
                    assertThat(t.getSourceIssueReportId()).isEqualTo(UUID.fromString(reportId));
                    assertThat(t.getStatus().name()).isEqualTo("PENDING");
                });
    }

    /**
     * Dismiss scenario: Admin dismisses an OPEN report without creating a task.
     */
    @Test
    void adminDismissesReport_neitherTaskCreatedNorConvertible() throws Exception {

        String adminJwt = registerAdminAndLogin(
                "Admin Dismiss", "admin@dismiss.com", "Secret123!", "Dismiss Tenant");
        String propertyId = createProperty(adminJwt, "DIS-001", "Dismiss Loft", "Via D 1", "Milano");
        createOperator(adminJwt, "Op Dismiss", "op@dismiss.com", "Operatore123!", "ELECTRICAL");
        String operatorJwt = login("op@dismiss.com", "Operatore123!");

        // Operator creates report
        MvcResult createResult = mockMvc.perform(post("/issue-reports")
                        .header("Authorization", "Bearer " + operatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"propertyId": "%s", "description": "Luce non funziona"}
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn();

        String reportId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // Admin dismisses it
        mockMvc.perform(patch("/issue-reports/{id}/dismiss", reportId)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        // No tasks created
        assertThat(taskRepository.count()).isZero();

        // DB state reflects DISMISSED
        assertThat(issueReportRepository.findById(UUID.fromString(reportId)))
                .isPresent()
                .hasValueSatisfying(r -> assertThat(r.getStatus().name()).isEqualTo("DISMISSED"));
    }

    /**
     * Double review guard: a CONVERTED report cannot be dismissed (409).
     */
    @Test
    void alreadyConvertedReport_cannotBeDismissed_returns409() throws Exception {

        String adminJwt = registerAdminAndLogin(
                "Admin Guard", "admin@guard.com", "Secret123!", "Guard Tenant");
        String propertyId = createProperty(adminJwt, "GRD-001", "Guard Loft", "Via G 1", "Torino");
        createOperator(adminJwt, "Op Guard", "op@guard.com", "Operatore123!", "GENERAL_MAINTENANCE");
        String operatorJwt = login("op@guard.com", "Operatore123!");

        // Operator creates report
        MvcResult createResult = mockMvc.perform(post("/issue-reports")
                        .header("Authorization", "Bearer " + operatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"propertyId": "%s", "description": "Porta non chiude"}
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn();

        String reportId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asText();

        // Admin converts it first
        mockMvc.perform(patch("/issue-reports/{id}/convert-to-task", reportId)
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "category": "GENERAL_MAINTENANCE",
                              "priority": "LOW",
                              "summary": "Riparare porta",
                              "dispatchMode": "POOL"
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueReport.status").value("CONVERTED"));

        // Second review attempt → 409
        mockMvc.perform(patch("/issue-reports/{id}/dismiss", reportId)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isConflict());
    }

    // --- helpers ---

    private String registerAdminAndLogin(String fullName, String email,
                                          String password, String workspaceName) throws Exception {
        mockMvc.perform(post("/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "fullName": "%s",
                              "email": "%s",
                              "password": "%s",
                              "workspaceName": "%s"
                            }
                            """.formatted(fullName, email, password, workspaceName)))
                .andExpect(status().isCreated());
        return login(email, password);
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"email": "%s", "password": "%s"}
                            """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String createProperty(String jwt, String propertyCode, String name,
                                   String address, String city) throws Exception {
        MvcResult result = mockMvc.perform(post("/properties")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyCode": "%s",
                              "name": "%s",
                              "address": "%s",
                              "city": "%s"
                            }
                            """.formatted(propertyCode, name, address, city)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }

    private void createOperator(String jwt, String fullName, String email,
                                  String password, String category) throws Exception {
        mockMvc.perform(post("/users/operators")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "fullName": "%s",
                              "email": "%s",
                              "initialPassword": "%s",
                              "specializationCategory": "%s"
                            }
                            """.formatted(fullName, email, password, category)))
                .andExpect(status().isCreated());
    }
}

