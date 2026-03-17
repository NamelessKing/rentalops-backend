package com.rentalops.iam.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.api.dto.CreateOperatorRequest;
import com.rentalops.iam.api.dto.CreateOperatorResponse;
import com.rentalops.iam.application.OperatorApplicationService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
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
 * Web-layer tests for POST /users/operators.
 *
 * <p>These tests protect endpoint status codes and JSON shape expected by the frontend,
 * while mocking business logic in OperatorApplicationService.
 */
class OperatorCreationWebTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OperatorApplicationService operatorApplicationService = mock(OperatorApplicationService.class);

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new OperatorAdminController(operatorApplicationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void createOperator_shouldReturn201_withExpectedResponseShape() throws Exception {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        CreateOperatorResponse response = new CreateOperatorResponse(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Giulia Verdi",
                "giulia@example.com",
                "OPERATOR",
                "ACTIVE",
                "CLEANING"
        );

        when(operatorApplicationService.createOperator(any(CreateOperatorRequest.class))).thenReturn(response);

        mockMvc.perform(post("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$.fullName").value("Giulia Verdi"))
                .andExpect(jsonPath("$.email").value("giulia@example.com"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.specializationCategory").value("CLEANING"));
    }

    @Test
    void createOperator_shouldReturn400_whenValidationFails() throws Exception {
        String invalidPayload = """
                {
                  "fullName": "",
                  "email": "invalid-email",
                  "initialPassword": "123",
                  "specializationCategory": "CLEANING"
                }
                """;

        mockMvc.perform(post("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.initialPassword").exists());
    }

    @Test
    void createOperator_shouldReturn409_whenEmailAlreadyExists() throws Exception {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        when(operatorApplicationService.createOperator(any(CreateOperatorRequest.class)))
                .thenThrow(new BusinessConflictException("Email already in use."));

        mockMvc.perform(post("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"));
    }

    @Test
    void createOperator_shouldReturn403_whenCurrentUserIsNotAdmin() throws Exception {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        when(operatorApplicationService.createOperator(any(CreateOperatorRequest.class)))
                .thenThrow(new ForbiddenOperationException("Only admins can manage operators."));

        mockMvc.perform(post("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden operation"));
    }

    @Test
    void createOperator_shouldReturn400_whenSpecializationCategoryIsInvalid() throws Exception {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "INVALID_CATEGORY"
        );

        when(operatorApplicationService.createOperator(any(CreateOperatorRequest.class)))
                .thenThrow(new DomainValidationException("Invalid specializationCategory."));

        mockMvc.perform(post("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Domain validation failed"));
    }
}

