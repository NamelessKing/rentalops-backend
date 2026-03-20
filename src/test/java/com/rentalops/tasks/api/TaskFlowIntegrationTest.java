package com.rentalops.tasks.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.infrastructure.persistence.TenantRepository;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration test for the full Slice 3 task flow.
 *
 * <p>Tests the real Spring context with a Testcontainers PostgreSQL instance:
 * Admin registers, creates property and operator, creates POOL and DIRECT_ASSIGNMENT tasks,
 * Admin lists/details tasks, Operator sees pool and my-tasks.
 *
 * <p>This test covers the primary demo scenario required to close Slice 3.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class TaskFlowIntegrationTest {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
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
     * Full Slice 3 demo scenario:
     * Admin registers → creates property → creates operator → creates POOL task →
     * creates DIRECT_ASSIGNMENT task → lists tasks → gets task detail →
     * Operator logs in → sees pool → sees my-tasks.
     */
    @Test
    void adminCreatesTasksAndOperatorSeesThemCorrectly() throws Exception {

        // 1. Register Admin and get JWT
        String adminJwt = registerAdminAndLogin(
                "Admin Rossi", "admin@tenantA.com", "Secret123!", "Tenant A");

        // 2. Create property
        String propertyId = createProperty(adminJwt, "APT-001", "Milano Loft", "Via Roma 1", "Milano");

        // 3. Create operator with CLEANING specialization
        createOperator(adminJwt, "Giulia Verde", "giulia@tenantA.com", "Operatore123!", "CLEANING");
        String operatorJwt = login("giulia@tenantA.com", "Operatore123!");

        // 4. Create a POOL task — no assignee, status must be PENDING
        MvcResult poolTaskResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "HIGH",
                              "summary": "Pulizia post checkout",
                              "description": "Pulizia completa entro le 14:00",
                              "dispatchMode": "POOL",
                              "estimatedHours": 3
                            }
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.dispatchMode").value("POOL"))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andReturn();

        String poolTaskId = objectMapper.readTree(
                poolTaskResult.getResponse().getContentAsString()).get("id").asText();

        // 5. Create a DIRECT_ASSIGNMENT task — operator must have matching PLUMBING category
        //    First create a PLUMBING operator
        String plumberOperatorId = createOperator(adminJwt, "Marco Idraulico", "marco@tenantA.com", "Operatore123!", "PLUMBING");

        MvcResult directTaskResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "PLUMBING",
                              "priority": "CRITICAL",
                              "summary": "Riparare perdita bagno",
                              "dispatchMode": "DIRECT_ASSIGNMENT",
                              "assigneeId": "%s"
                            }
                            """.formatted(propertyId, plumberOperatorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.dispatchMode").value("DIRECT_ASSIGNMENT"))
                .andExpect(jsonPath("$.assigneeId").value(plumberOperatorId))
                .andReturn();

        String directTaskId = objectMapper.readTree(
                directTaskResult.getResponse().getContentAsString()).get("id").asText();

        // 6. Admin lists all tasks — should see both
        mockMvc.perform(get("/tasks")
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // 7. Admin gets detail of POOL task
        mockMvc.perform(get("/tasks/" + poolTaskId)
                        .header("Authorization", "Bearer " + adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(poolTaskId))
                .andExpect(jsonPath("$.summary").value("Pulizia post checkout"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.propertyName").value("Milano Loft"))
                .andExpect(jsonPath("$.estimatedHours").value(3));

        // 8. Operator (CLEANING) sees the POOL task in the pool
        mockMvc.perform(get("/tasks/pool")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(poolTaskId))
                .andExpect(jsonPath("$[0].category").value("CLEANING"));

        // 9. PLUMBING operator sees the DIRECT_ASSIGNMENT task in my-tasks
        String plumberJwt = login("marco@tenantA.com", "Operatore123!");
        mockMvc.perform(get("/tasks/my")
                        .header("Authorization", "Bearer " + plumberJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(directTaskId))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        // 10. CLEANING operator has no my-tasks (not assigned to any task)
        mockMvc.perform(get("/tasks/my")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 11. Category mismatch: assigning CLEANING task to PLUMBING operator must be rejected
        mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "LOW",
                              "summary": "Pulizia balconi",
                              "dispatchMode": "DIRECT_ASSIGNMENT",
                              "assigneeId": "%s"
                            }
                            """.formatted(propertyId, plumberOperatorId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Operator specialization category does not match the task category."));

        // 12. Operator cannot access a task they are not assigned to (403)
        mockMvc.perform(get("/tasks/" + poolTaskId)
                        .header("Authorization", "Bearer " + plumberJwt))
                .andExpect(status().isForbidden());

        // Verify DB state: 2 tasks persisted
        assertThat(taskRepository.count()).isEqualTo(2);
    }

    // --- helpers ---

    private String registerAdminAndLogin(String fullName, String email, String password, String workspaceName)
            throws Exception {
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

    private String createProperty(String jwt, String propertyCode, String name, String address, String city)
            throws Exception {
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

    private String createOperator(String jwt, String fullName, String email,
                                   String password, String category) throws Exception {
        MvcResult result = mockMvc.perform(post("/users/operators")
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "fullName": "%s",
                              "email": "%s",
                              "password": "%s",
                              "specializationCategory": "%s"
                            }
                            """.formatted(fullName, email, password, category)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}




