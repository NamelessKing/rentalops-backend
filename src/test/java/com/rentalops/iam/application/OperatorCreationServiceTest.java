package com.rentalops.iam.application;

import com.rentalops.iam.api.dto.CreateOperatorRequest;
import com.rentalops.iam.api.dto.CreateOperatorResponse;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for operator creation business rules.
 *
 * <p>These tests protect the service-layer decisions without involving HTTP or DB.
 */
@ExtendWith(MockitoExtension.class)
class OperatorCreationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private OperatorApplicationService operatorApplicationService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
    }

    @Test
    void createOperator_shouldPersistInCurrentTenant_withHashedPassword() {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.initialPassword())).thenReturn("hashed-temp-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateOperatorResponse response = operatorApplicationService.createOperator(request);

        assertThat(response.fullName()).isEqualTo("Giulia Verdi");
        assertThat(response.email()).isEqualTo("giulia@example.com");
        assertThat(response.role()).isEqualTo("OPERATOR");
        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.specializationCategory()).isEqualTo("CLEANING");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getTenantId()).isEqualTo(tenantId);
        assertThat(savedUser.getRole()).isEqualTo(UserRole.OPERATOR);
        assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getPassword()).isEqualTo("hashed-temp-password");
    }

    @Test
    void createOperator_shouldThrowConflict_whenEmailAlreadyExists() {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> operatorApplicationService.createOperator(request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Email already in use.");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createOperator_shouldThrowForbidden_whenCurrentUserIsNotAdmin() {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "CLEANING"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.OPERATOR);

        assertThatThrownBy(() -> operatorApplicationService.createOperator(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Only admins can manage operators.");

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void createOperator_shouldThrowDomainValidation_whenSpecializationIsInvalid() {
        CreateOperatorRequest request = new CreateOperatorRequest(
                "Giulia Verdi",
                "giulia@example.com",
                "Temp123!",
                "INVALID_CATEGORY"
        );

        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
        when(currentUserProvider.getCurrentTenantId()).thenReturn(tenantId);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);

        assertThatThrownBy(() -> operatorApplicationService.createOperator(request))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Invalid specializationCategory.");

        verify(userRepository, never()).save(any(User.class));
    }
}

