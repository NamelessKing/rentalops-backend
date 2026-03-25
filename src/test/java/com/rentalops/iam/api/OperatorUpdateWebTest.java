package com.rentalops.iam.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.iam.api.dto.UpdateOperatorRequest;
import com.rentalops.iam.api.dto.UpdateOperatorResponse;
import com.rentalops.iam.application.OperatorApplicationService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for PUT /users/operators/{id}.
 *
 * <p>These tests protect the HTTP contract — status codes, response shape and
 * error mapping — without exercising real business logic (mocked service).
 */
class OperatorUpdateWebTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OperatorApplicationService operatorApplicationService =
            mock(OperatorApplicationService.class);

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
    void updateOperator_shouldReturn200_withUpdatedShape() throws Exception {
        // Verifies the happy-path contract: all fields updated, password left blank (no change).
        UUID operatorId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        UpdateOperatorRequest request = new UpdateOperatorRequest(
                "Giulia Bianchi",
                "giulia.bianchi@example.com",
                null,
                "PLUMBING"
        );

        UpdateOperatorResponse response = new UpdateOperatorResponse(
                operatorId,
                "Giulia Bianchi",
                "giulia.bianchi@example.com",
                "OPERATOR",
                "ACTIVE",
                "PLUMBING"
        );

        when(operatorApplicationService.updateOperator(eq(operatorId), any(UpdateOperatorRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(operatorId.toString()))
                .andExpect(jsonPath("$.fullName").value("Giulia Bianchi"))
                .andExpect(jsonPath("$.email").value("giulia.bianchi@example.com"))
                .andExpect(jsonPath("$.role").value("OPERATOR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.specializationCategory").value("PLUMBING"));
    }

    @Test
    void updateOperator_shouldReturn200_withPasswordReset() throws Exception {
        // Verifies that a non-blank newPassword is accepted and the response shape is correct.
        UUID operatorId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

        UpdateOperatorRequest request = new UpdateOperatorRequest(
                "Marco Rossi",
                "marco@example.com",
                "NewSecret123!",
                "CLEANING"
        );

        UpdateOperatorResponse response = new UpdateOperatorResponse(
                operatorId, "Marco Rossi", "marco@example.com", "OPERATOR", "ACTIVE", "CLEANING"
        );

        when(operatorApplicationService.updateOperator(eq(operatorId), any(UpdateOperatorRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Marco Rossi"));
    }

    @Test
    void updateOperator_shouldReturn404_whenNotFoundInTenant() throws Exception {
        UUID operatorId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        when(operatorApplicationService.updateOperator(eq(operatorId), any(UpdateOperatorRequest.class)))
                .thenThrow(new ResourceNotFoundException("Operator not found in this tenant."));

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Any Name",
                                  "email": "any@example.com",
                                  "specializationCategory": "CLEANING"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void updateOperator_shouldReturn409_whenEmailAlreadyInUse() throws Exception {
        UUID operatorId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");

        when(operatorApplicationService.updateOperator(eq(operatorId), any(UpdateOperatorRequest.class)))
                .thenThrow(new BusinessConflictException("Email already in use."));

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Any Name",
                                  "email": "taken@example.com",
                                  "specializationCategory": "CLEANING"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"));
    }

    @Test
    void updateOperator_shouldReturn400_whenValidationFails() throws Exception {
        // Empty fullName and invalid email must produce a 400 with field-level errors.
        UUID operatorId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "",
                                  "email": "not-an-email",
                                  "specializationCategory": "CLEANING"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"));
    }

    @Test
    void updateOperator_shouldReturn400_whenInvalidSpecializationCategory() throws Exception {
        UUID operatorId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");

        when(operatorApplicationService.updateOperator(eq(operatorId), any(UpdateOperatorRequest.class)))
                .thenThrow(new DomainValidationException("Invalid specializationCategory."));

        mockMvc.perform(put("/users/operators/{id}", operatorId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Name",
                                  "email": "valid@example.com",
                                  "specializationCategory": "INVALID_CATEGORY"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Domain validation failed"));
    }
}


