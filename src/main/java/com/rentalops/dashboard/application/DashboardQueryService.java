package com.rentalops.dashboard.application;

import com.rentalops.dashboard.api.dto.AdminDashboardResponse;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.issuereports.domain.model.IssueReportStatus;
import com.rentalops.issuereports.infrastructure.persistence.IssueReportRepository;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.domain.model.TaskStatus;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only service that assembles the admin dashboard summary for a tenant.
 *
 * <p>Each count is a single indexed COUNT query — no entity loading, no N+1 risk.
 * The six status-based task and issue-report counts are intentionally issued as
 * separate queries to keep the code straightforward; at MVP scale the overhead
 * is negligible compared to the complexity of a single multi-column aggregation query.
 */
@Service
public class DashboardQueryService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final IssueReportRepository issueReportRepository;
    private final CurrentUserProvider currentUserProvider;

    public DashboardQueryService(PropertyRepository propertyRepository,
                                 UserRepository userRepository,
                                 TaskRepository taskRepository,
                                 IssueReportRepository issueReportRepository,
                                 CurrentUserProvider currentUserProvider) {
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.issueReportRepository = issueReportRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Returns the admin summary for the authenticated user's tenant.
     * Only ADMIN users may call this endpoint.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse getAdminSummary() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can access the dashboard summary.");
        }

        UUID tenantId = currentUser.tenantId();

        long propertiesCount = propertyRepository.countByTenantId(tenantId);
        long operatorsCount  = userRepository.countByTenantIdAndRole(tenantId, UserRole.OPERATOR);

        AdminDashboardResponse.TaskCounts taskCounts = new AdminDashboardResponse.TaskCounts(
                taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.PENDING),
                taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.ASSIGNED),
                taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.IN_PROGRESS),
                taskRepository.countByTenantIdAndStatus(tenantId, TaskStatus.COMPLETED)
        );

        AdminDashboardResponse.IssueReportCounts issueReportCounts = new AdminDashboardResponse.IssueReportCounts(
                issueReportRepository.countByTenantIdAndStatus(tenantId, IssueReportStatus.OPEN),
                issueReportRepository.countByTenantIdAndStatus(tenantId, IssueReportStatus.CONVERTED),
                issueReportRepository.countByTenantIdAndStatus(tenantId, IssueReportStatus.DISMISSED)
        );

        return new AdminDashboardResponse(propertiesCount, operatorsCount, taskCounts, issueReportCounts);
    }
}

