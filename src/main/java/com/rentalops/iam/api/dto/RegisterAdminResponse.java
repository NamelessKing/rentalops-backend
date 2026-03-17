package com.rentalops.iam.api.dto;

import java.util.UUID;

/**
 * Response body for the initial admin registration endpoint.
 *
 * <p>Returns the created user and tenant so the client can
 * confirm the workspace was set up correctly. No token is
 * included — the client must call login to obtain one.
 */
public record RegisterAdminResponse(
        UserPayload user,
        TenantPayload tenant
) {

    /**
     * The admin user created during registration.
     */
    public record UserPayload(
            UUID id,
            String fullName,
            String email,
            String role
    ) {
    }

    /**
     * The tenant workspace created during registration.
     */
    public record TenantPayload(
            UUID id,
            String name
    ) {
    }
}
