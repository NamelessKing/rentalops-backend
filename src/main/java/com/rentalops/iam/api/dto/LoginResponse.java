package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response body for the login endpoint.
 *
 * <p>The accessToken must be sent as a Bearer token in the
 * Authorization header for all subsequent protected requests.
 * The user payload gives the client enough context to decide
 * which area of the application to show after login.
 */
public record LoginResponse(
        String accessToken,
        UserPayload user
) {

    /**
     * Authenticated user context returned after a successful login.
     * tenantId is included so the client can derive the workspace
     * without making an additional request.
     */
    public record UserPayload(
            UUID id,
            String fullName,
            String email,
            String role,
            UUID tenantId
    ) {
    }
}
