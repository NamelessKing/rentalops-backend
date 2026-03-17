package com.rentalops.iam.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.api.dto.LoginRequest;
import com.rentalops.iam.api.dto.RegisterAdminRequest;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.TenantRepository;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.shared.security.JwtTokenService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth flow test using the real Spring context and PostgreSQL.
 *
 * <p>This test protects the most important Slice 1 path: a new admin registers,
 * the user and tenant are really persisted, and login returns a valid JWT with
 * the tenant-aware authenticated context expected by later slices.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
class AuthFlowIntegrationTest {

    @SuppressWarnings("unused") // Read by Testcontainers extension via reflection.
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtTokenService jwtTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        tenantRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void registerAdmin_thenLogin_shouldPersistAuthData_andReturnValidJwtContext() throws Exception {
        RegisterAdminRequest registerRequest = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        MvcResult registerResult = mockMvc.perform(post("/auth/register-admin")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.fullName").value("Mario Rossi"))
                .andExpect(jsonPath("$.user.email").value("mario@example.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenant.name").value("Mario Rentals"))
                .andReturn();

        JsonNode registerJson = objectMapper.readTree(registerResult.getResponse().getContentAsString());
        UUID createdUserId = UUID.fromString(registerJson.get("user").get("id").asText());
        UUID createdTenantId = UUID.fromString(registerJson.get("tenant").get("id").asText());

        User persistedUser = userRepository.findByEmail("mario@example.com").orElseThrow();

        assertThat(persistedUser.getId()).isEqualTo(createdUserId);
        assertThat(persistedUser.getTenantId()).isEqualTo(createdTenantId);
        assertThat(persistedUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(persistedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(persistedUser.getPassword()).isNotEqualTo("Secret123!");
        assertThat(passwordEncoder.matches("Secret123!", persistedUser.getPassword())).isTrue();
        assertThat(tenantRepository.findById(createdTenantId)).isPresent();

        LoginRequest loginRequest = new LoginRequest("mario@example.com", "Secret123!");

        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.id").value(createdUserId.toString()))
                .andExpect(jsonPath("$.user.email").value("mario@example.com"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.user.tenantId").value(createdTenantId.toString()))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String accessToken = loginJson.get("accessToken").asText();

        assertThat(accessToken).isNotBlank();

        Claims claims = jwtTokenService.extractAllClaims(accessToken);
        assertThat(claims.getSubject()).isEqualTo(createdUserId.toString());
        assertThat(claims.get("tenantId", String.class)).isEqualTo(createdTenantId.toString());
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("email", String.class)).isEqualTo("mario@example.com");
        assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
    }

    @Test
    void registerAdmin_shouldReturn409_whenEmailIsRegisteredTwice() throws Exception {
        RegisterAdminRequest request = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        mockMvc.perform(post("/auth/register-admin")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register-admin")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"))
                .andExpect(jsonPath("$.detail").value("Email already in use."));
    }
}



