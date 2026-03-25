package com.rentalops.dashboard.api;

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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for GET /dashboard/admin-summary.
 *
 * <p>Covers three scenarios:
 *   - Happy path: admin gets correct property, operator, task and issue-report counts
 *   - Operator denied: OPERATOR role receives 403
 *   - Unauthenticated denied: missing JWT receives 401
 *
 * <p>Requires Docker for Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class DashboardIntegrationTest {

    @SuppressWarnings("unused")
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private IssueReportRepository issueReportRepository;

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
     * Admin receives correct counts after creating real data in their tenant.
     *
     * <p>Setup: 2 properties, 1 operator, 1 POOL task (PENDING), 1 DIRECT_ASSIGNMENT task
     * (ASSIGNED), 1 issue report (OPEN).
     * Expected: propertiesCount=2, operatorsCount=1, pending=1, assigned=1,
     * inProgress=0, completed=0, open=1, converted=0, dismissed=0.
     */
    @Test
    void adminReceivesCorrectSummary() throws Exception {
        String adminJwt = registerAdminAndLogin(
                "Admin Dash", "admin@dash.test", "Secret123!", "DashTenant");

        // Create 2 properties
        String prop1Id = createProperty(adminJwt, "DASH-001", "Dash Loft 1", "Via Test 1", "Roma");
        createProperty(adminJwt, "DASH-002", "Dash Loft 2", "Via Test 2", "Roma");

        // Create 1 operator
        createOperator(adminJwt, "Op Dash", "op@dash.test", "Operatore123!", "CLEANING");
        String operatorJwt = login("op@dash.test", "Operatore123!");

        // Create 1 POOL task (status = PENDING)
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "HIGH",
                              "summary": "Pool task for dashboard test",
                              "dispatchMode": "POOL"
                            }
                            """.formatted(prop1Id)))
                .andExpect(status().isCreated());

        // Retrieve operator id for direct assignment
        MvcResult opListResult = mockMvc.perform(get("/users/operators")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andReturn();
        String operatorId = objectMapper.readTree(
                opListResult.getResponse().getContentAsString()).get(0).get("id").asText();

        // Create 1 DIRECT_ASSIGNMENT task (status = ASSIGNED)
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "LOW",
                              "summary": "Direct task for dashboard test",
                              "dispatchMode": "DIRECT_ASSIGNMENT",
                              "assigneeId": "%s"
                            }
                            """.formatted(prop1Id, operatorId)))
                .andExpect(status().isCreated());

        // Operator creates 1 issue report (status = OPEN)
        mockMvc.perform(post("/issue-reports")
                        .header("Authorization", "Bearer " + operatorJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "description": "Rubinetto che perde"
                            }
                            """.formatted(prop1Id)))
                .andExpect(status().isCreated());

        // Verify dashboard summary
        mockMvc.perform(get("/dashboard/admin-summary")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertiesCount").value(2))
                .andExpect(jsonPath("$.operatorsCount").value(1))
                .andExpect(jsonPath("$.taskCounts.pending").value(1))
                .andExpect(jsonPath("$.taskCounts.assigned").value(1))
                .andExpect(jsonPath("$.taskCounts.inProgress").value(0))
                .andExpect(jsonPath("$.taskCounts.completed").value(0))
                .andExpect(jsonPath("$.issueReportCounts.open").value(1))
                .andExpect(jsonPath("$.issueReportCounts.converted").value(0))
                .andExpect(jsonPath("$.issueReportCounts.dismissed").value(0));
    }

    /**
     * An OPERATOR calling GET /dashboard/admin-summary receives 403.
     */
    @Test
    void operatorCannotAccessDashboard() throws Exception {
        registerAdminAndLogin("Admin Dash2", "admin@dash2.test", "Secret123!", "DashTenant2");
        createOperator(
                login("admin@dash2.test", "Secret123!"),
                "Op Dash2", "op@dash2.test", "Operatore123!", "PLUMBING"
        );
        String operatorJwt = login("op@dash2.test", "Operatore123!");

        mockMvc.perform(get("/dashboard/admin-summary")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isForbidden());
    }

    /**
     * An unauthenticated request to GET /dashboard/admin-summary receives 401.
     */
    @Test
    void unauthenticatedRequestReceives401() throws Exception {
        mockMvc.perform(get("/dashboard/admin-summary"))
                .andExpect(status().isUnauthorized());
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



