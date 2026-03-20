package com.rentalops.tasks.domain.model;

/**
 * Business priority level for a task.
 *
 * <p>Used by the Admin at task creation to communicate urgency.
 * The frontend renders this to help operators prioritise their work.
 */
public enum TaskPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

