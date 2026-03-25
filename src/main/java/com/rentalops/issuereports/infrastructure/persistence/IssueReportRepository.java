package com.rentalops.issuereports.infrastructure.persistence;

import com.rentalops.issuereports.domain.model.IssueReport;
import com.rentalops.issuereports.domain.model.IssueReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence adapter for IssueReport entities.
 *
 * <p>Every query is tenant-scoped to prevent cross-tenant data leaks.
 */
public interface IssueReportRepository extends JpaRepository<IssueReport, UUID> {

    /**
     * Admin list: all issue reports for the tenant, regardless of status.
     * Ordered by creation date descending so newest appear first.
     */
    List<IssueReport> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /**
     * Tenant-aware single lookup. Used for detail view and as a pre-check
     * before any mutation (convert, dismiss).
     */
    Optional<IssueReport> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Count issue reports by status within a tenant.
     * Used by the dashboard to build per-status aggregates without loading entities.
     */
    long countByTenantIdAndStatus(UUID tenantId, IssueReportStatus status);
}

