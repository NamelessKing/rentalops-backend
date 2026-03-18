package com.rentalops.iam.api;

import com.rentalops.iam.api.dto.EnableOperatorResponse;
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
 * Web-layer tests for PATCH /users/operators/{id}/enable.
 *
 * <p>These tests protect the re-activation contract. The idempotency guarantee
 * (calling enable on an already-active operator returns 200) is enforced at the
 * service layer and visible here through the response shape.
 */
class OperatorEnableWebTest {

    private MockMvc mockMvc;

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
    void enableOperator_shouldReturn200_withActiveStatus() throws Exception {
        // Happy path: a disabled operator is re-activated.
        UUID operatorId = UUID.fromString("11111111-1111-1111-1111-111111111111");

        when(operatorApplicationService.enableOperator(operatorId))
                .thenReturn(new EnableOperatorResponse(operatorId, "ACTIVE"));

        mockMvc.perform(patch("/users/operators/{id}/enable", operatorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void enableOperator_shouldReturn200_whenAlreadyActive() throws Exception {
        // Idempotency: enabling an already-active operator must not throw — returns 200 invariato.
        UUID operatorId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        when(operatorApplicationService.enableOperator(operatorId))
                .thenReturn(new EnableOperatorResponse(operatorId, "ACTIVE"));

        mockMvc.perform(patch("/users/operators/{id}/enable", operatorId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void enableOperator_shouldReturn404_whenNotFoundInTenant() throws Exception {
        when(operatorApplicationService.enableOperator(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Operator not found in this tenant."));

        mockMvc.perform(patch("/users/operators/{id}/enable", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}

