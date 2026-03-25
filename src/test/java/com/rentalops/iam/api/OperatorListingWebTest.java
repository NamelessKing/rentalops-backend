package com.rentalops.iam.api;

import com.rentalops.iam.application.OperatorApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.rentalops.iam.api.dto.OperatorListItemResponse;

import java.util.Collections;
import java.util.List;
import java.util.UUID;


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
        OperatorListItemResponse operator = new OperatorListItemResponse(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                "Alice Smith",
                "alice@example.com",
                "ACTIVE",
                "CLEANING"
        );
        when(operatorApplicationService.listOperators()).thenReturn(List.of(operator));

        mockMvc.perform(get("/users/operators")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(jsonPath("$[0].fullName").value("Alice Smith"))
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].specializationCategory").value("CLEANING"));
    }
}



