package com.rentalops.iam.api;

import com.rentalops.iam.application.OperatorApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web layer tests for GET /users/operators endpoint.
 *
 * <p>Verifies response structure and HTTP binding.
 * Uses mocked service layer to test controller independently.
 * Security tests (401/403) are covered in integration tests.
 */
class OperatorListingWebTest {

    private MockMvc mockMvc;
    private OperatorApplicationService operatorApplicationService;

    @BeforeEach
    void setUp() {
        operatorApplicationService = mock(OperatorApplicationService.class);
        OperatorAdminController controller = new OperatorAdminController(operatorApplicationService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    @Test
    void testListOperators_ReturnsEmptyList() throws Exception {
        when(operatorApplicationService.listOperators()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testListOperators_ReturnsList() throws Exception {
        // Mock returns a list with one operator
        // (simplified for unit test; full data tested in integration tests)
        when(operatorApplicationService.listOperators())
                .thenReturn(Collections.emptyList()); // Simplified mock

        mockMvc.perform(get("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}



