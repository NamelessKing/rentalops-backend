package com.rentalops.iam.domain.model;

/**
 * Categorizes both tasks and operator specializations.
 *
 * <p>The same enum serves two purposes: it classifies the type of work
 * a task requires, and it describes the specialization of an operator.
 * This allows the pool visibility filter to match operators to tasks
 * of their category.
 */
public enum TaskCategory {
    CLEANING,
    PLUMBING,
    ELECTRICAL,
    GENERAL_MAINTENANCE
}
