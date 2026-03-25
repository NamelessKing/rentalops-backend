package com.rentalops.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for the login endpoint.
 *
 * <p>Validation here only checks that fields are present and well-formed.
 * Wrong credentials are a business error handled in the service layer,
 * not a validation error.
 */
public record LoginRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @NotBlank(message = "Password is required")
        String password
) {
}
