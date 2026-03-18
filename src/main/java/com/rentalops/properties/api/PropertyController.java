package com.rentalops.properties.api;

import com.rentalops.properties.api.dto.CreatePropertyRequest;
import com.rentalops.properties.api.dto.DeactivatePropertyResponse;
import com.rentalops.properties.api.dto.PropertyDetailResponse;
import com.rentalops.properties.api.dto.PropertyListItemResponse;
import com.rentalops.properties.api.dto.UpdatePropertyRequest;
import com.rentalops.properties.application.PropertyApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes property endpoints for Slice 2 foundation.
 *
 * <p>The controller stays thin and delegates all tenant and role logic to
 * PropertyApplicationService.
 */
@RestController
@RequestMapping("/properties")
@Tag(name = "Properties", description = "Property management for Slice 2")
public class PropertyController {

    private final PropertyApplicationService propertyApplicationService;

    public PropertyController(PropertyApplicationService propertyApplicationService) {
        this.propertyApplicationService = propertyApplicationService;
    }

    @Operation(summary = "List tenant properties")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Property list"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping
    public List<PropertyListItemResponse> listProperties() {
        return propertyApplicationService.listProperties();
    }

    @Operation(summary = "Create property")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Property created",
                    content = @Content(schema = @Schema(implementation = PropertyDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate property code",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PropertyDetailResponse createProperty(@Valid @RequestBody CreatePropertyRequest request) {
        return propertyApplicationService.createProperty(request);
    }

    @Operation(summary = "Get property detail")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Property detail",
                    content = @Content(schema = @Schema(implementation = PropertyDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Property not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @GetMapping("/{id}")
    public PropertyDetailResponse getPropertyDetail(@PathVariable UUID id) {
        return propertyApplicationService.getPropertyDetail(id);
    }

    @Operation(summary = "Update property")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Property updated",
                    content = @Content(schema = @Schema(implementation = PropertyDetailResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Property not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate property code",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PutMapping("/{id}")
    public PropertyDetailResponse updateProperty(@PathVariable UUID id,
                                                 @Valid @RequestBody UpdatePropertyRequest request) {
        return propertyApplicationService.updateProperty(id, request);
    }

    @Operation(summary = "Deactivate property")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Property deactivated",
                    content = @Content(schema = @Schema(implementation = DeactivatePropertyResponse.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an admin",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Property not found in tenant",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PatchMapping("/{id}/deactivate")
    public DeactivatePropertyResponse deactivateProperty(@PathVariable UUID id) {
        return propertyApplicationService.deactivateProperty(id);
    }
}
