package com.rentalops.tasks.infrastructure.persistence;

import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.tasks.domain.model.Task;
import com.rentalops.tasks.domain.model.TaskDispatchMode;
import com.rentalops.tasks.domain.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for Task entities.
 *
 * <p>Every query is tenant-scoped by design. No method returns tasks without
 * filtering by tenantId, which prevents accidental cross-tenant data leaks.
 *
 * <p>Spring Data JPA derives all SQL from method names — no manual queries needed
 * for these straightforward tenant-aware lookups.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Admin task list: all tasks for the tenant regardless of status or mode.
     */
    List<Task> findAllByTenantId(UUID tenantId);

    /**
     * Tenant-aware single task lookup.
     * Used for detail view and as a pre-check before any mutation (claim, start, complete).
     */
    Optional<Task> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Pool query for operators.
     * Returns only PENDING tasks in POOL mode that match the operator's specialization.
     * The combined filter prevents direct-assignment tasks and already-claimed tasks
     * from appearing in the public pool.
     */
    List<Task> findAllByTenantIdAndStatusAndDispatchModeAndCategory(
            UUID tenantId,
            TaskStatus status,
            TaskDispatchMode dispatchMode,
            TaskCategory category);

    /**
     * My-tasks query: all tasks assigned to a specific operator in the tenant.
     * Covers both direct-assignment tasks and pool tasks the operator has claimed.
     */
    List<Task> findAllByTenantIdAndAssigneeId(UUID tenantId, UUID assigneeId);

    /**
     * Count tasks by status within a tenant.
     * Used by the dashboard to build per-status aggregates without loading entities.
     */
    long countByTenantIdAndStatus(UUID tenantId, TaskStatus status);
}

