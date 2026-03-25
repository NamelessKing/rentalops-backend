package com.rentalops.tasks.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.infrastructure.persistence.TenantRepository;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.tasks.domain.model.TaskStatus;
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
 * End-to-end integration test for the Slice 4 operator task execution flow.
 *
 * <p>Covers the full lifecycle: pool visibility → claim → my-tasks → start → complete.
 * Also verifies that a concurrent claim attempt returns 409 and that ownership checks
 * prevent unauthorized state transitions.
 *
 * <p>Requires Docker for Testcontainers PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class TaskExecutionIntegrationTest {

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
     * Full Slice 4 demo scenario:
     * Admin registers → creates property → creates CLEANING operator →
     * creates POOL task → Operator claims → task appears in my-tasks →
     * Operator starts → Operator completes.
     */
    @Test
    void operatorClaimsStartsAndCompletesPoolTask() throws Exception {

        // 1. Setup: admin registers, creates property and operator
        String adminJwt = registerAdminAndLogin(
                "Admin Test", "admin@slice4.com", "Secret123!", "Slice4 Tenant");
        String propertyId = createProperty(adminJwt, "SL4-001", "Slice4 Loft", "Via Test 1", "Roma");
        createOperator(adminJwt, "Op Slice4", "op@slice4.com", "Operatore123!", "CLEANING");
        String operatorJwt = login("op@slice4.com", "Operatore123!");

        // 2. Admin creates a POOL task
        MvcResult poolTaskResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "HIGH",
                              "summary": "Pulizia Slice 4",
                              "dispatchMode": "POOL",
                              "estimatedHours": 2
                            }
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String taskId = objectMapper.readTree(
                poolTaskResult.getResponse().getContentAsString()).get("id").asText();

        // 3. Operator sees the task in the pool
        mockMvc.perform(get("/tasks/pool")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId));

        // 4. Operator claims the task → status becomes ASSIGNED, task leaves the pool
        mockMvc.perform(post("/tasks/{id}/claim", taskId)
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(taskId))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assigneeId").isNotEmpty());

        // 5. Pool is now empty for the operator
        mockMvc.perform(get("/tasks/pool")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        // 6. Task now appears in my-tasks
        mockMvc.perform(get("/tasks/my")
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(taskId))
                .andExpect(jsonPath("$[0].status").value("ASSIGNED"));

        // 7. Operator starts the task → ASSIGNED → IN_PROGRESS
        mockMvc.perform(patch("/tasks/{id}/start", taskId)
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isNoContent());

        // Verify DB state
        assertThat(taskRepository.findById(UUID.fromString(taskId)))
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS));

        // 8. Operator completes the task → IN_PROGRESS → COMPLETED
        mockMvc.perform(patch("/tasks/{id}/complete", taskId)
                        .header("Authorization", "Bearer " + operatorJwt))
                .andExpect(status().isNoContent());

        // Verify final DB state
        assertThat(taskRepository.findById(UUID.fromString(taskId)))
                .isPresent()
                .hasValueSatisfying(t -> assertThat(t.getStatus()).isEqualTo(TaskStatus.COMPLETED));
    }

    /**
     * Concurrent claim scenario: two operators try to claim the same task.
     * The second claim must return 409 Conflict.
     */
    @Test
    void concurrentClaimReturnsWith409ForSecondOperator() throws Exception {

        // Setup: admin + property + 2 CLEANING operators + 1 POOL task
        String adminJwt = registerAdminAndLogin(
                "Admin Concurrent", "admin@concurrent.com", "Secret123!", "Concurrent Tenant");
        String propertyId = createProperty(adminJwt, "CON-001", "Concurrent Loft", "Via C 1", "Milano");

        createOperator(adminJwt, "Op Alpha", "alpha@concurrent.com", "Operatore123!", "CLEANING");
        createOperator(adminJwt, "Op Beta",  "beta@concurrent.com",  "Operatore123!", "CLEANING");

        String alphaJwt = login("alpha@concurrent.com", "Operatore123!");
        String betaJwt  = login("beta@concurrent.com",  "Operatore123!");

        MvcResult poolTaskResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "MEDIUM",
                              "summary": "Pulizia concorrente",
                              "dispatchMode": "POOL"
                            }
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn();

        String taskId = objectMapper.readTree(
                poolTaskResult.getResponse().getContentAsString()).get("id").asText();

        // First operator claims successfully
        mockMvc.perform(post("/tasks/{id}/claim", taskId)
                        .header("Authorization", "Bearer " + alphaJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"));

        // Second operator attempts to claim the already-assigned task → 409
        mockMvc.perform(post("/tasks/{id}/claim", taskId)
                        .header("Authorization", "Bearer " + betaJwt))
                .andExpect(status().isConflict());
    }

    /**
     * Ownership enforcement: an operator cannot start or complete a task assigned to another.
     */
    @Test
    void ownershipCheck_unauthorizedOperatorCannotMutateTask() throws Exception {

        // Setup: admin + property + 2 operators + 1 POOL task claimed by operator A
        String adminJwt = registerAdminAndLogin(
                "Admin Owner", "admin@owner.com", "Secret123!", "Owner Tenant");
        String propertyId = createProperty(adminJwt, "OWN-001", "Owner Loft", "Via O 1", "Torino");

        createOperator(adminJwt, "Op Owner",    "owner@owner.com",    "Operatore123!", "CLEANING");
        createOperator(adminJwt, "Op Intruder", "intruder@owner.com", "Operatore123!", "CLEANING");

        String ownerJwt    = login("owner@owner.com",    "Operatore123!");
        String intruderJwt = login("intruder@owner.com", "Operatore123!");

        MvcResult poolTaskResult = mockMvc.perform(post("/tasks")
                        .header("Authorization", "Bearer " + adminJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "propertyId": "%s",
                              "category": "CLEANING",
                              "priority": "LOW",
                              "summary": "Pulizia ownership test",
                              "dispatchMode": "POOL"
                            }
                            """.formatted(propertyId)))
                .andExpect(status().isCreated())
                .andReturn();

        String taskId = objectMapper.readTree(
                poolTaskResult.getResponse().getContentAsString()).get("id").asText();

        // Owner claims the task
        mockMvc.perform(post("/tasks/{id}/claim", taskId)
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isOk());

        // Intruder tries to start the task assigned to owner → 403
        mockMvc.perform(patch("/tasks/{id}/start", taskId)
                        .header("Authorization", "Bearer " + intruderJwt))
                .andExpect(status().isForbidden());

        // Owner starts the task
        mockMvc.perform(patch("/tasks/{id}/start", taskId)
                        .header("Authorization", "Bearer " + ownerJwt))
                .andExpect(status().isNoContent());

        // Intruder tries to complete the task → 403
        mockMvc.perform(patch("/tasks/{id}/complete", taskId)
                        .header("Authorization", "Bearer " + intruderJwt))
                .andExpect(status().isForbidden());
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


