package com.rentalops.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /users/operators endpoint.
 *
 * <p>Used by Admin to create a new operator directly in the tenant.
 * No invitation flow in MVP — credentials are communicated out-of-platform.
 */
public record CreateOperatorRequest(

        @NotBlank(message = "Full name is required")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Initial password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        String initialPassword,

        // nullable: operator may not have a specialization assigned yet
        String specializationCategory
) {
}

