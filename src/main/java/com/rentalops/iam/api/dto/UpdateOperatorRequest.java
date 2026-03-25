package com.rentalops.iam.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for PUT /users/operators/{id}.
 *
 * <p>All core identity fields are required so the update is a full replacement
 * (PUT semantics). {@code newPassword} is intentionally optional: when null or
 * blank the operator's existing password is preserved, which means the admin
 * does not need to re-enter credentials every time they fix a name or category.
 *
 * <p>Password length is validated in the service layer rather than here so the
 * "blank = no change" shortcut can be applied before validation runs.
 */
public record UpdateOperatorRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid email address")
        String email,

        // Null or blank means "keep the existing password". A non-blank value
        // replaces the current password — admin communicates the new one manually.
        String newPassword,

        @NotBlank(message = "Specialization category is required")
        String specializationCategory
) {}

