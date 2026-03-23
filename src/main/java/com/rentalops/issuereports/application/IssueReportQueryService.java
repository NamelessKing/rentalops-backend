package com.rentalops.issuereports.application;

import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.issuereports.api.dto.IssueReportDetailResponse;
import com.rentalops.issuereports.api.dto.IssueReportListItemResponse;
import com.rentalops.issuereports.domain.model.IssueReport;
import com.rentalops.issuereports.infrastructure.persistence.IssueReportRepository;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only queries for issue reports.
 *
 * <p>Both list and detail are Admin-only: the operator creates reports but the
 * review interface belongs to the Admin side of the application.
 */
@Service
public class IssueReportQueryService {

    private final IssueReportRepository issueReportRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CurrentUserProvider currentUserProvider;

    public IssueReportQueryService(IssueReportRepository issueReportRepository,
                                   PropertyRepository propertyRepository,
                                   UserRepository userRepository,
                                   CurrentUserProvider currentUserProvider) {
        this.issueReportRepository = issueReportRepository;
        this.propertyRepository = propertyRepository;
        this.userRepository = userRepository;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Returns all issue reports for the authenticated Admin's tenant.
     *
     * <p>Property names and reporter names are batch-loaded to avoid N+1 queries.
     */
    @Transactional(readOnly = true)
    public List<IssueReportListItemResponse> listIssueReports() {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can list issue reports.");
        }

        UUID tenantId = currentUser.tenantId();
        List<IssueReport> reports = issueReportRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);

        // Batch-load properties and users to avoid N+1.
        Set<UUID> propertyIds = reports.stream().map(IssueReport::getPropertyId).collect(Collectors.toSet());
        Set<UUID> userIds     = reports.stream().map(IssueReport::getReportedByUserId).collect(Collectors.toSet());

        Map<UUID, String> propertyNames = propertyRepository.findAllById(propertyIds).stream()
                .collect(Collectors.toMap(Property::getId, Property::getName));
        Map<UUID, String> userNames = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getFullName));

        return reports.stream()
                .map(r -> new IssueReportListItemResponse(
                        r.getId(),
                        r.getPropertyId(),
                        propertyNames.getOrDefault(r.getPropertyId(), ""),
                        r.getReportedByUserId(),
                        userNames.getOrDefault(r.getReportedByUserId(), ""),
                        r.getDescription(),
                        r.getStatus().name(),
                        r.getCreatedAt()
                ))
                .toList();
    }

    /**
     * Returns the full detail of a single issue report for the authenticated Admin.
     */
    @Transactional(readOnly = true)
    public IssueReportDetailResponse getIssueReportDetail(UUID id) {
        AuthenticatedUser currentUser = currentUserProvider.getCurrentUser();
        if (!currentUser.isAdmin()) {
            throw new ForbiddenOperationException("Only ADMIN users can view issue report details.");
        }

        UUID tenantId = currentUser.tenantId();
        IssueReport report = issueReportRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Issue report not found: " + id));

        String propertyName = propertyRepository.findByIdAndTenantId(report.getPropertyId(), tenantId)
                .map(Property::getName)
                .orElse("");

        String reporterName = userRepository.findByIdAndTenantId(report.getReportedByUserId(), tenantId)
                .map(User::getFullName)
                .orElse("");

        return new IssueReportDetailResponse(
                report.getId(),
                report.getPropertyId(),
                propertyName,
                report.getReportedByUserId(),
                reporterName,
                report.getDescription(),
                report.getStatus().name(),
                report.getCreatedAt(),
                report.getReviewedByUserId(),
                report.getReviewedAt()
        );
    }
}

