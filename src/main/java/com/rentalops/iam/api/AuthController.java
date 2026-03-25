package com.rentalops.iam.api;

import com.rentalops.iam.api.dto.LoginRequest;
import com.rentalops.iam.api.dto.LoginResponse;
import com.rentalops.iam.api.dto.RegisterAdminRequest;
import com.rentalops.iam.api.dto.RegisterAdminResponse;
import com.rentalops.iam.application.AuthApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the authentication endpoints for admin registration and login.
 *
 * <p>This controller is intentionally thin — all business rules live in
 * AuthApplicationService. The controller only binds HTTP input, delegates
 * to the service, and returns the result.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Auth", description = "Authentication and initial admin onboarding")
public class AuthController {

    private final AuthApplicationService authApplicationService;

    public AuthController(AuthApplicationService authApplicationService) {
        this.authApplicationService = authApplicationService;
    }

    @Operation(
            summary = "Register admin",
            description = "Creates the initial admin user and tenant workspace."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Admin registered successfully",
                    content = @Content(schema = @Schema(implementation = RegisterAdminResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Email already in use",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/register-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterAdminResponse registerAdmin(@Valid @RequestBody RegisterAdminRequest request) {
        return authApplicationService.registerAdmin(request);
    }

    @Operation(
            summary = "Login",
            description = "Authenticates a user and returns a signed access token."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Login successful",
                    content = @Content(schema = @Schema(implementation = LoginResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation error",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Invalid credentials",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Account is disabled",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))
            )
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authApplicationService.login(request);
    }
}
