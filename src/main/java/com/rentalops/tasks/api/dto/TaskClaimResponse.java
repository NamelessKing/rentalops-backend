package com.rentalops.tasks.api.dto;

import java.util.UUID;

/**
 * Response returned after a successful task claim.
 *
 * <p>Shape matches the Slice 4 contract in docs/08-api-draft.md:
 * {@code { "id": "uuid", "status": "ASSIGNED", "assigneeId": "uuid" }}
 */
public record TaskClaimResponse(
        UUID id,
        String status,
        UUID assigneeId
) {}

