package com.rentalops.tasks.domain.model;

/**
 * Lifecycle states for a task.
 *
 * <p>Valid transitions (enforced by the service layer, not by this enum):
 *   PENDING -> ASSIGNED  (via claim or direct assignment)
 *   ASSIGNED -> IN_PROGRESS  (operator starts the task)
 *   IN_PROGRESS -> COMPLETED  (operator completes the task)
 *
 * <p>COMPLETED is terminal — no further transitions are allowed in the MVP.
 */
public enum TaskStatus {
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED
}

