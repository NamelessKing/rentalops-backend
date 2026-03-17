package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the real JWT security filter chain on a protected endpoint.
 *
 * <p>The auth endpoints are intentionally public, so this test adds a tiny
 * test-only controller to verify the real production path for protected routes:
 * bearer token -> JWT filter -> SecurityContext -> CurrentUserProvider -> controller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.config.import=")
@Testcontainers(disabledWithoutDocker = true)
@Import(ProtectedEndpointSecurityIntegrationTest.ProtectedEndpointTestConfiguration.class)
class ProtectedEndpointSecurityIntegrationTest {

    @SuppressWarnings("unused") // Read by Testcontainers extension via reflection.
    @Container
    @ServiceConnection
    private static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine")
    );

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtTokenService jwtTokenService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenAuthorizationHeaderIsMissing() throws Exception {
        mockMvc.perform(get("/test/protected/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."));
    }

    @Test
    void protectedEndpoint_shouldReturn401_whenTokenIsInvalid() throws Exception {
        mockMvc.perform(get("/test/protected/me")
                        .header("Authorization", "Bearer definitely-not-a-valid-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(APPLICATION_PROBLEM_JSON_VALUE))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication is required to access this resource."));
    }

    @Test
    void protectedEndpoint_shouldReturnAuthenticatedUser_whenJwtIsValid() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID tenantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        String token = jwtTokenService.generateToken(new AuthenticatedUser(
                userId,
                tenantId,
                UserRole.ADMIN,
                "admin@example.com",
                UserStatus.ACTIVE
        ));

        mockMvc.perform(get("/test/protected/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.email").value("admin@example.com"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    /**
     * Adds a minimal protected endpoint only for security integration testing.
     *
     * <p>This keeps production code untouched while still verifying the exact
     * runtime behavior of the real security configuration.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class ProtectedEndpointTestConfiguration {

        @Bean
        ProtectedEndpointTestController protectedEndpointTestController(CurrentUserProvider currentUserProvider) {
            return new ProtectedEndpointTestController(currentUserProvider);
        }
    }

    /**
     * Echo endpoint that exposes the resolved authenticated principal.
     *
     * <p>If the JWT filter or security context wiring breaks, this controller
     * fails immediately because CurrentUserProvider cannot resolve the user.
     */
    @RestController
    @RequestMapping("/test/protected")
    static class ProtectedEndpointTestController {

        private final CurrentUserProvider currentUserProvider;

        ProtectedEndpointTestController(CurrentUserProvider currentUserProvider) {
            this.currentUserProvider = currentUserProvider;
        }

        @GetMapping("/me")
        ProtectedUserPayload me() {
            AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
            return new ProtectedUserPayload(
                    currentUser.userId(),
                    currentUser.tenantId(),
                    currentUser.role().name(),
                    currentUser.email(),
                    currentUser.status().name()
            );
        }
    }

    /**
     * Response payload used only by the protected endpoint security test.
     */
    record ProtectedUserPayload(
            UUID userId,
            UUID tenantId,
            String role,
            String email,
            String status
    ) {
    }
}


