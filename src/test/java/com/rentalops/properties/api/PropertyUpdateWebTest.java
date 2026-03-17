package com.rentalops.properties.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.properties.api.dto.CreatePropertyRequest;
import com.rentalops.properties.api.dto.DeactivatePropertyResponse;
import com.rentalops.properties.api.dto.PropertyDetailResponse;
import com.rentalops.properties.application.PropertyApplicationService;
import com.rentalops.shared.api.ApiExceptionHandler;
import com.rentalops.shared.exceptions.BusinessConflictException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for property update and deactivate endpoints.
 */
class PropertyUpdateWebTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final PropertyApplicationService propertyApplicationService = mock(PropertyApplicationService.class);

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new PropertyController(propertyApplicationService))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void updateProperty_shouldReturn200_whenUpdateSucceeds() throws Exception {
        UUID propertyId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        CreatePropertyRequest request = new CreatePropertyRequest(
                "apt-001",
                "Milano Centrale Loft Updated",
                "Via Roma 10",
                "Milano",
                "Updated note"
        );

        when(propertyApplicationService.updateProperty(eq(propertyId), any(CreatePropertyRequest.class)))
                .thenReturn(new PropertyDetailResponse(
                        propertyId,
                        "APT-001",
                        "Milano Centrale Loft Updated",
                        "Via Roma 10",
                        "Milano",
                        "Updated note",
                        true
                ));

        mockMvc.perform(put("/properties/{id}", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("55555555-5555-5555-5555-555555555555"))
                .andExpect(jsonPath("$.propertyCode").value("APT-001"))
                .andExpect(jsonPath("$.name").value("Milano Centrale Loft Updated"));
    }

    @Test
    void updateProperty_shouldReturn404_whenPropertyNotFoundInTenant() throws Exception {
        UUID propertyId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        CreatePropertyRequest request = new CreatePropertyRequest(
                "APT-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                null
        );

        when(propertyApplicationService.updateProperty(eq(propertyId), any(CreatePropertyRequest.class)))
                .thenThrow(new ResourceNotFoundException("Property not found in this tenant."));

        mockMvc.perform(put("/properties/{id}", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }

    @Test
    void updateProperty_shouldReturn409_whenPropertyCodeConflictsWithAnotherRecord() throws Exception {
        UUID propertyId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        CreatePropertyRequest request = new CreatePropertyRequest(
                "APT-002",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                null
        );

        when(propertyApplicationService.updateProperty(eq(propertyId), any(CreatePropertyRequest.class)))
                .thenThrow(new BusinessConflictException("Property code already exists in this tenant."));

        mockMvc.perform(put("/properties/{id}", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"));
    }

    @Test
    void deactivateProperty_shouldReturn200_withActiveFalse() throws Exception {
        UUID propertyId = UUID.fromString("88888888-8888-8888-8888-888888888888");

        when(propertyApplicationService.deactivateProperty(propertyId))
                .thenReturn(new DeactivatePropertyResponse(propertyId, false));

        mockMvc.perform(patch("/properties/{id}/deactivate", propertyId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("88888888-8888-8888-8888-888888888888"))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void updateProperty_shouldReturn400_whenValidationFails() throws Exception {
        UUID propertyId = UUID.fromString("99999999-9999-9999-9999-999999999999");

        String invalidPayload = """
                {
                  "propertyCode": "",
                  "name": "",
                  "address": "",
                  "city": "",
                  "notes": "Invalid"
                }
                """;

        mockMvc.perform(put("/properties/{id}", propertyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.propertyCode").exists())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.address").exists())
                .andExpect(jsonPath("$.errors.city").exists());
    }
}

