package com.rentalops.iam.api;

import com.rentalops.iam.api.dto.CreateOperatorRequest;
import com.rentalops.iam.api.dto.CreateOperatorResponse;
import com.rentalops.iam.api.dto.OperatorListItemResponse;
import com.rentalops.iam.application.OperatorApplicationService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Exposes operator management endpoints for admins.
 *
 * <p>This controller is intentionally thin — all business rules live in
 * OperatorApplicationService. The controller only binds HTTP input, delegates
 * to the service, and returns the result.
 *
 * <p>All endpoints require authentication and the ADMIN role.
 */
@RestController
@RequestMapping("/users/operators")
@Tag(name = "Operators", description = "Operator management for admin team setup")
public class OperatorAdminController {

    private final OperatorApplicationService operatorApplicationService;

    public OperatorAdminController(OperatorApplicationService operatorApplicationService) {
        this.operatorApplicationService = operatorApplicationService;
    }

    @Operation(
            summary = "List operators in current tenant",
            description = "Returns all operators (role=OPERATOR) in the authenticated admin's tenant"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "List of operators",
                    content = @Content(schema = @Schema(implementation = OperatorListItemResponse.class))
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
            )
    })
    @GetMapping
    public List<OperatorListItemResponse> listOperators() {
        return operatorApplicationService.listOperators();
    }

    @Operation(
            summary = "Create operator in current tenant",
            description = "Creates a new operator. Credentials are communicated out-of-platform."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Operator created",
                    content = @Content(schema = @Schema(implementation = CreateOperatorResponse.class))
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
                    description = "Email already in use",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOperatorResponse createOperator(@Valid @RequestBody CreateOperatorRequest request) {
        return operatorApplicationService.createOperator(request);
    }

    @Operation(
            summary = "Disable operator",
            description = "Sets operator status to DISABLED (soft-delete). Optional for MVP."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Operator disabled"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthenticated"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Not an admin"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Operator not found in this tenant"
            )
    })
    @PatchMapping("/{id}/disable")
    public void disableOperator(@PathVariable UUID id) {
        operatorApplicationService.disableOperator(id);
    }
}

