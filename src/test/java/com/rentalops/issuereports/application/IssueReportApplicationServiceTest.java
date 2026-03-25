package com.rentalops.issuereports.application;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskRequest;
import com.rentalops.issuereports.api.dto.ConvertIssueReportToTaskResponse;
import com.rentalops.issuereports.api.dto.CreateIssueReportRequest;
import com.rentalops.issuereports.api.dto.CreateIssueReportResponse;
import com.rentalops.issuereports.api.dto.DismissIssueReportResponse;
import com.rentalops.issuereports.domain.model.IssueReport;
import com.rentalops.issuereports.domain.model.IssueReportStatus;
import com.rentalops.issuereports.infrastructure.persistence.IssueReportRepository;
import com.rentalops.properties.domain.model.Property;
import com.rentalops.properties.infrastructure.persistence.PropertyRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.CurrentUserProvider;
import com.rentalops.tasks.infrastructure.persistence.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for issue report business rules: create, convert to task and dismiss.
 *
 * <p>Each test exercises a single business rule in isolation — no Spring context,
 * no database, no Docker required. Dependencies are replaced with mocks so that
 * assertions focus purely on the rule being protected.
 */
@ExtendWith(MockitoExtension.class)
class IssueReportApplicationServiceTest {

    @Mock
    private IssueReportRepository issueReportRepository;

    @Mock
    private PropertyRepository propertyRepository;

    // Required by IssueReportApplicationService constructor (used in DIRECT_ASSIGNMENT paths).
    // Declared here so @InjectMocks can satisfy the dependency; no explicit stub needed for POOL tests.
    @Mock
    @SuppressWarnings("unused")
    private UserRepository userRepository;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private IssueReportApplicationService issueReportApplicationService;

    private static final UUID TENANT_ID   = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OPERATOR_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_ID    = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID PROPERTY_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID REPORT_ID   = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    private AuthenticatedUser operatorPrincipal;
    private AuthenticatedUser adminPrincipal;

    @BeforeEach
    void setUp() {
        operatorPrincipal = new AuthenticatedUser(
                OPERATOR_ID, TENANT_ID, UserRole.OPERATOR, "op@example.com", UserStatus.ACTIVE);
        adminPrincipal = new AuthenticatedUser(
                ADMIN_ID, TENANT_ID, UserRole.ADMIN, "admin@example.com", UserStatus.ACTIVE);
    }

    // -----------------------------------------------------------------------
    // createIssueReport
    // -----------------------------------------------------------------------

    @Test
    void createIssueReport_shouldPersistOpenReport_whenOperatorCreates() {
        CreateIssueReportRequest request = new CreateIssueReportRequest(
                PROPERTY_ID, "Water leak on third floor");

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.of(stubProperty()));
        when(issueReportRepository.save(any(IssueReport.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CreateIssueReportResponse response =
                issueReportApplicationService.createIssueReport(request);

        assertThat(response.propertyId()).isEqualTo(PROPERTY_ID);
        assertThat(response.reportedByUserId()).isEqualTo(OPERATOR_ID);
        assertThat(response.status()).isEqualTo("OPEN");

        // Verify the persisted report is in OPEN status and belongs to the operator's tenant.
        ArgumentCaptor<IssueReport> captor = ArgumentCaptor.forClass(IssueReport.class);
        verify(issueReportRepository).save(captor.capture());
        IssueReport saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IssueReportStatus.OPEN);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
    }

    @Test
    void createIssueReport_shouldThrowForbidden_whenCallerIsAdmin() {
        // Admins review and convert; only Operators are in the field creating reports.
        CreateIssueReportRequest request = new CreateIssueReportRequest(
                PROPERTY_ID, "Water leak");

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);

        assertThatThrownBy(() -> issueReportApplicationService.createIssueReport(request))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void createIssueReport_shouldThrowNotFound_whenPropertyNotInTenant() {
        CreateIssueReportRequest request = new CreateIssueReportRequest(
                PROPERTY_ID, "Water leak");

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);
        when(propertyRepository.findByIdAndTenantId(PROPERTY_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueReportApplicationService.createIssueReport(request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(issueReportRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // convertToTask
    // -----------------------------------------------------------------------

    @Test
    void convertToTask_shouldCreateTaskAndMarkReportConverted_whenReportIsOpen() {
        IssueReport report = openReport();
        ConvertIssueReportToTaskRequest request = new ConvertIssueReportToTaskRequest(
                "GENERAL_MAINTENANCE", "MEDIUM", "Fix leak",
                "Replace the pipe under the sink", "POOL", null, 2);

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(report));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(issueReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ConvertIssueReportToTaskResponse response =
                issueReportApplicationService.convertToTask(REPORT_ID, request);

        // The report must be marked CONVERTED after the operation.
        assertThat(report.getStatus()).isEqualTo(IssueReportStatus.CONVERTED);
        assertThat(report.getReviewedByUserId()).isEqualTo(ADMIN_ID);
        assertThat(response.issueReport().status()).isEqualTo("CONVERTED");
        assertThat(response.task().sourceIssueReportId()).isEqualTo(REPORT_ID);
    }

    @Test
    void convertToTask_shouldThrowForbidden_whenCallerIsOperator() {
        // Only Admins perform the review. An Operator creating a report cannot also close it.
        ConvertIssueReportToTaskRequest request = new ConvertIssueReportToTaskRequest(
                "GENERAL_MAINTENANCE", "MEDIUM", "Fix", null, "POOL", null, null);

        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);

        assertThatThrownBy(() -> issueReportApplicationService.convertToTask(REPORT_ID, request))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(taskRepository, never()).save(any());
        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void convertToTask_shouldThrowConflict_whenReportIsAlreadyConverted() {
        // A report that was already reviewed must not be processed again.
        IssueReport report = alreadyConvertedReport();
        ConvertIssueReportToTaskRequest request = new ConvertIssueReportToTaskRequest(
                "GENERAL_MAINTENANCE", "MEDIUM", "Fix", null, "POOL", null, null);

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> issueReportApplicationService.convertToTask(REPORT_ID, request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already been reviewed");

        verify(taskRepository, never()).save(any());
    }

    @Test
    void convertToTask_shouldThrowConflict_whenReportIsAlreadyDismissed() {
        IssueReport report = alreadyDismissedReport();
        ConvertIssueReportToTaskRequest request = new ConvertIssueReportToTaskRequest(
                "GENERAL_MAINTENANCE", "MEDIUM", "Fix", null, "POOL", null, null);

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> issueReportApplicationService.convertToTask(REPORT_ID, request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already been reviewed");

        verify(taskRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------
    // dismiss
    // -----------------------------------------------------------------------

    @Test
    void dismiss_shouldMarkReportDismissed_whenReportIsOpen() {
        IssueReport report = openReport();

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(report));
        when(issueReportRepository.save(report)).thenReturn(report);

        DismissIssueReportResponse response =
                issueReportApplicationService.dismiss(REPORT_ID);

        assertThat(response.id()).isEqualTo(REPORT_ID);
        assertThat(response.status()).isEqualTo("DISMISSED");
        assertThat(report.getStatus()).isEqualTo(IssueReportStatus.DISMISSED);
        assertThat(report.getReviewedByUserId()).isEqualTo(ADMIN_ID);
    }

    @Test
    void dismiss_shouldThrowForbidden_whenCallerIsOperator() {
        when(currentUserProvider.getCurrentUser()).thenReturn(operatorPrincipal);

        assertThatThrownBy(() -> issueReportApplicationService.dismiss(REPORT_ID))
                .isInstanceOf(ForbiddenOperationException.class);

        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void dismiss_shouldThrowConflict_whenReportIsAlreadyDismissed() {
        IssueReport report = alreadyDismissedReport();

        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.of(report));

        assertThatThrownBy(() -> issueReportApplicationService.dismiss(REPORT_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("already been reviewed");

        verify(issueReportRepository, never()).save(any());
    }

    @Test
    void dismiss_shouldThrowNotFound_whenReportNotInTenant() {
        when(currentUserProvider.getCurrentUser()).thenReturn(adminPrincipal);
        when(issueReportRepository.findByIdAndTenantId(REPORT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> issueReportApplicationService.dismiss(REPORT_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -----------------------------------------------------------------------
    // Helpers — build minimal entity instances for each scenario
    // -----------------------------------------------------------------------

    private IssueReport openReport() {
        return new IssueReport(REPORT_ID, TENANT_ID, PROPERTY_ID, OPERATOR_ID, "Water leak");
    }

    private IssueReport alreadyConvertedReport() {
        IssueReport report = new IssueReport(
                REPORT_ID, TENANT_ID, PROPERTY_ID, OPERATOR_ID, "Water leak");
        report.setStatus(IssueReportStatus.CONVERTED);
        return report;
    }

    private IssueReport alreadyDismissedReport() {
        IssueReport report = new IssueReport(
                REPORT_ID, TENANT_ID, PROPERTY_ID, OPERATOR_ID, "Water leak");
        report.setStatus(IssueReportStatus.DISMISSED);
        return report;
    }

    private Property stubProperty() {
        // A minimal Property stub — the service only checks its presence, not its fields.
        return new Property(PROPERTY_ID, TENANT_ID, "Villa Rossi", "VR-001",
                "Via Roma 1", "Milano", "20100");
    }
}




