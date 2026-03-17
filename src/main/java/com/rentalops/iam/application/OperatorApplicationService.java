package com.rentalops.iam.application;

import com.rentalops.iam.api.dto.CreateOperatorRequest;
import com.rentalops.iam.api.dto.CreateOperatorResponse;
import com.rentalops.iam.api.dto.OperatorListItemResponse;
import com.rentalops.iam.domain.model.TaskCategory;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import com.rentalops.shared.security.CurrentUserProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Handles operator management use cases for admins.
 *
 * <p>This service enforces:
 * - tenant isolation (no user can list/create operators outside their tenant)
 * - role enforcement (only ADMIN can create operators)
 * - email uniqueness across the entire system
 * - proper password hashing before persistence
 *
 * <p>The service layer is the single place where these business rules live,
 * so they are never scattered across controllers or repositories.
 */
@Service
public class OperatorApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserProvider currentUserProvider;

    public OperatorApplicationService(UserRepository userRepository,
                                      PasswordEncoder passwordEncoder,
                                      CurrentUserProvider currentUserProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserProvider = currentUserProvider;
    }

    /**
     * Lists all operators in the authenticated admin's tenant.
     *
     * <p>Returns only users with role OPERATOR and matching tenantId.
     * Fails with 403 if the authenticated user is not an ADMIN.
     */
    @Transactional(readOnly = true)
    public List<OperatorListItemResponse> listOperators() {
        assertCurrentUserIsAdmin();

        UUID tenantId = currentUserProvider.getCurrentTenantId();

        List<User> operators = userRepository.findAllByTenantIdAndRole(tenantId, UserRole.OPERATOR);

        return operators.stream()
                .map(op -> new OperatorListItemResponse(
                        op.getId(),
                        op.getFullName(),
                        op.getEmail(),
                        op.getStatus().name(),
                        op.getSpecializationCategory() != null ? op.getSpecializationCategory().name() : null
                ))
                .toList();
    }

    /**
     * Creates a new operator in the authenticated admin's tenant.
     *
     * <p>Business rules:
     * - email must be unique across the entire system (409 if duplicate)
     * - role is always OPERATOR, status is always ACTIVE
     * - tenantId is derived from the authenticated context (never client-provided)
     * - password is hashed before persistence
     *
     * @throws ForbiddenOperationException if the authenticated user is not an ADMIN
     * @throws BusinessConflictException if the email is already in use
     */
    @Transactional
    public CreateOperatorResponse createOperator(CreateOperatorRequest request) {
        assertCurrentUserIsAdmin();

        // Guard before persistence to give a clean 409 instead of DB constraint error
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessConflictException("Email already in use.");
        }

        UUID tenantId = currentUserProvider.getCurrentTenantId();

        // Parse specialization category if provided
        TaskCategory specialization = null;
        if (request.specializationCategory() != null && !request.specializationCategory().isEmpty()) {
            try {
                specialization = TaskCategory.valueOf(request.specializationCategory());
            } catch (IllegalArgumentException ignored) {
                // Fail fast so clients fix the payload instead of silently persisting wrong data.
                throw new DomainValidationException("Invalid specializationCategory.");
            }
        }

        User operator = new User(
                UUID.randomUUID(),
                tenantId,
                request.fullName(),
                request.email(),
                // Hash the password before storing — never store plain text
                passwordEncoder.encode(request.initialPassword()),
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        );

        // Set specialization after construction
        if (specialization != null) {
            operator.setSpecializationCategory(specialization);
        }

        operator = userRepository.save(operator);

        return new CreateOperatorResponse(
                operator.getId(),
                operator.getFullName(),
                operator.getEmail(),
                operator.getRole().name(),
                operator.getStatus().name(),
                operator.getSpecializationCategory() != null ? operator.getSpecializationCategory().name() : null
        );
    }

    /**
     * Disables an operator in the authenticated admin's tenant.
     *
     * <p>The operator is not deleted; their status is set to DISABLED.
     * This preserves historical data and task assignments.
     *
     * @throws ForbiddenOperationException if the authenticated user is not an ADMIN
     * @throws ResourceNotFoundException if the operator does not exist in this tenant
     */
    @Transactional
    public void disableOperator(UUID operatorId) {
        assertCurrentUserIsAdmin();

        UUID tenantId = currentUserProvider.getCurrentTenantId();

        User operator = userRepository.findByIdAndTenantId(operatorId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Operator not found in this tenant."
                ));

        operator.setStatus(UserStatus.DISABLED);
        userRepository.save(operator);
    }

    /**
     * Helper to assert the current user has ADMIN role.
     * Throws ForbiddenOperationException if not.
     */
    private void assertCurrentUserIsAdmin() {
        if (currentUserProvider.getCurrentUserRole() != UserRole.ADMIN) {
            throw new ForbiddenOperationException("Only admins can manage operators.");
        }
    }
}

