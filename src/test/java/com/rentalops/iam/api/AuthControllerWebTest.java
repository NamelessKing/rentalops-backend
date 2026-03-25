package com.rentalops.iam.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.api.dto.LoginRequest;
import com.rentalops.iam.api.dto.LoginResponse;
import com.rentalops.iam.api.dto.RegisterAdminRequest;
import com.rentalops.iam.api.dto.RegisterAdminResponse;
import com.rentalops.iam.application.AuthApplicationService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for auth HTTP contract.
 *
 * <p>These tests protect endpoint status codes and JSON shape expected by the frontend,
 * while mocking out business logic handled by AuthApplicationService.
 */
class AuthControllerWebTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AuthApplicationService authApplicationService = mock(AuthApplicationService.class);

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authApplicationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void registerAdmin_shouldReturn201WithNestedUserAndTenant() throws Exception {
        RegisterAdminRequest request = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        RegisterAdminResponse response = new RegisterAdminResponse(
                new RegisterAdminResponse.UserPayload(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "Mario Rossi",
                        "mario@example.com",
                        "ADMIN"
                ),
                new RegisterAdminResponse.TenantPayload(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "Mario Rentals"
                )
        );

        when(authApplicationService.registerAdmin(any(RegisterAdminRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.tenant.id").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.tenant.name").value("Mario Rentals"));
    }

    @Test
    void registerAdmin_shouldReturn400_whenValidationFails() throws Exception {
        // Invalid payload: blank values and malformed email should trigger Bean Validation.
        String invalidPayload = """
                {
                  "fullName": "",
                  "email": "not-an-email",
                  "password": "123",
                  "workspaceName": ""
                }
                """;

        mockMvc.perform(post("/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerAdmin_shouldReturn409_whenEmailConflictOccurs() throws Exception {
        RegisterAdminRequest request = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        when(authApplicationService.registerAdmin(any(RegisterAdminRequest.class)))
                .thenThrow(new BusinessConflictException("Email already in use."));

        mockMvc.perform(post("/auth/register-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"));
    }

    @Test
    void login_shouldReturn200WithAccessTokenAndNestedUser() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Secret123!");

        LoginResponse response = new LoginResponse(
                "jwt-token",
                new LoginResponse.UserPayload(
                        UUID.fromString("33333333-3333-3333-3333-333333333333"),
                        "User Name",
                        "user@example.com",
                        "ADMIN",
                        UUID.fromString("44444444-4444-4444-4444-444444444444")
                )
        );

        when(authApplicationService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.user.id").value("33333333-3333-3333-3333-333333333333"))
                .andExpect(jsonPath("$.user.tenantId").value("44444444-4444-4444-4444-444444444444"));
    }

    @Test
    void login_shouldReturn400_whenValidationFails() throws Exception {
        String invalidPayload = """
                {
                  "email": "invalid-email",
                  "password": ""
                }
                """;

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void login_shouldReturn401_whenCredentialsAreInvalid() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "WrongPassword");

        when(authApplicationService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials."));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    @Test
    void login_shouldReturn403_whenAccountIsDisabled() throws Exception {
        LoginRequest request = new LoginRequest("user@example.com", "Secret123!");

        when(authApplicationService.login(any(LoginRequest.class)))
                .thenThrow(new ForbiddenOperationException("Account is disabled."));

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden operation"));
    }
}



