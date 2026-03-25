package com.rentalops.iam.api;

import com.rentalops.iam.api.dto.DisableOperatorResponse;
import com.rentalops.iam.application.OperatorApplicationService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for PATCH /users/operators/{id}/disable contract.
 */
class OperatorDisableWebTest {

    private MockMvc mockMvc;

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
    void disableOperator_shouldReturn200WithBody() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(operatorApplicationService.disableOperator(id))
                .thenReturn(new DisableOperatorResponse(id, "DISABLED"));

        mockMvc.perform(patch("/users/operators/{id}/disable", id)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    void disableOperator_shouldReturn404WhenNotFound() throws Exception {
        when(operatorApplicationService.disableOperator(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Operator not found in this tenant."));

        mockMvc.perform(patch("/users/operators/{id}/disable", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}

