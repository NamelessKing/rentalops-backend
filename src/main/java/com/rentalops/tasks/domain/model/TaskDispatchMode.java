package com.rentalops.tasks.domain.model;

/**
 * Determines how a task enters the system and its initial status.
 *
 * <p>POOL: task starts PENDING and is visible to compatible operators for claiming.
 * DIRECT_ASSIGNMENT: task starts ASSIGNED and is immediately linked to a specific operator.
 *
 * <p>The service layer enforces the correct initial status based on this value.
 * The client never sets the status directly.
 */
public enum TaskDispatchMode {
    POOL,
    DIRECT_ASSIGNMENT
}

