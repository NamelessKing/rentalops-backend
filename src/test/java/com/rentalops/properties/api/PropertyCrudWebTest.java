package com.rentalops.properties.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rentalops.properties.api.dto.CreatePropertyRequest;
import com.rentalops.properties.api.dto.PropertyDetailResponse;
import com.rentalops.properties.api.dto.PropertyListItemResponse;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for property read/create contract.
 */
class PropertyCrudWebTest {

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
    void listProperties_shouldReturn200_withCompactListShape() throws Exception {
        when(propertyApplicationService.listProperties())
                .thenReturn(List.of(new PropertyListItemResponse(
                        UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        "APT-001",
                        "Milano Centrale Loft",
                        "Milano",
                        true
                )));

        mockMvc.perform(get("/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("11111111-1111-1111-1111-111111111111"))
                .andExpect(jsonPath("$[0].propertyCode").value("APT-001"))
                .andExpect(jsonPath("$[0].name").value("Milano Centrale Loft"))
                .andExpect(jsonPath("$[0].city").value("Milano"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void createProperty_shouldReturn201_withFullDetailShape() throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "apt-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                "Check-in autonomo"
        );

        when(propertyApplicationService.createProperty(any(CreatePropertyRequest.class)))
                .thenReturn(new PropertyDetailResponse(
                        UUID.fromString("22222222-2222-2222-2222-222222222222"),
                        "APT-001",
                        "Milano Centrale Loft",
                        "Via Roma 1",
                        "Milano",
                        "Check-in autonomo",
                        true
                ));

        mockMvc.perform(post("/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("22222222-2222-2222-2222-222222222222"))
                .andExpect(jsonPath("$.propertyCode").value("APT-001"))
                .andExpect(jsonPath("$.name").value("Milano Centrale Loft"))
                .andExpect(jsonPath("$.address").value("Via Roma 1"))
                .andExpect(jsonPath("$.city").value("Milano"))
                .andExpect(jsonPath("$.notes").value("Check-in autonomo"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createProperty_shouldReturn400_whenPropertyCodeIsMissing() throws Exception {
        String invalidPayload = """
                {
                  "propertyCode": "",
                  "name": "Milano Centrale Loft",
                  "address": "Via Roma 1",
                  "city": "Milano",
                  "notes": "Check-in autonomo"
                }
                """;

        mockMvc.perform(post("/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.propertyCode").exists());
    }

    @Test
    void createProperty_shouldReturn409_whenPropertyCodeAlreadyExists() throws Exception {
        CreatePropertyRequest request = new CreatePropertyRequest(
                "APT-001",
                "Milano Centrale Loft",
                "Via Roma 1",
                "Milano",
                "Check-in autonomo"
        );

        when(propertyApplicationService.createProperty(any(CreatePropertyRequest.class)))
                .thenThrow(new BusinessConflictException("Property code already exists in this tenant."));

        mockMvc.perform(post("/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Business conflict"));
    }

    @Test
    void getPropertyDetail_shouldReturn200_whenPropertyExists() throws Exception {
        UUID propertyId = UUID.fromString("33333333-3333-3333-3333-333333333333");

        when(propertyApplicationService.getPropertyDetail(eq(propertyId)))
                .thenReturn(new PropertyDetailResponse(
                        propertyId,
                        "APT-001",
                        "Milano Centrale Loft",
                        "Via Roma 1",
                        "Milano",
                        "Check-in autonomo",
                        true
                ));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("33333333-3333-3333-3333-333333333333"))
                .andExpect(jsonPath("$.propertyCode").value("APT-001"));
    }

    @Test
    void getPropertyDetail_shouldReturn404_whenPropertyNotFoundInTenant() throws Exception {
        UUID propertyId = UUID.fromString("44444444-4444-4444-4444-444444444444");

        when(propertyApplicationService.getPropertyDetail(eq(propertyId)))
                .thenThrow(new ResourceNotFoundException("Property not found in this tenant."));

        mockMvc.perform(get("/properties/{id}", propertyId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
    }
}

