package com.rentalops.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for the initial admin registration endpoint.
 *
 * <p>Creating an admin also creates the tenant workspace, so both
 * user data and workspace name are required in a single request.
 */
public record RegisterAdminRequest(

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String password,

        @NotBlank(message = "Workspace name is required")
        String workspaceName
) {
}
