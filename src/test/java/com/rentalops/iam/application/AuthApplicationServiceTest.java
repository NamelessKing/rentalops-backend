package com.rentalops.iam.application;

import com.rentalops.iam.api.dto.LoginRequest;
import com.rentalops.iam.api.dto.LoginResponse;
import com.rentalops.iam.api.dto.RegisterAdminRequest;
import com.rentalops.iam.api.dto.RegisterAdminResponse;
import com.rentalops.iam.domain.model.Tenant;
import com.rentalops.iam.domain.model.User;
import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import com.rentalops.iam.infrastructure.persistence.TenantRepository;
import com.rentalops.iam.infrastructure.persistence.UserRepository;
import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.security.JwtTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for auth business rules.
 *
 * <p>This class focuses on decision logic (conflict handling, credential checks,
 * status checks and payload shape) without involving HTTP, Spring MVC or a real DB.
 */
@ExtendWith(MockitoExtension.class)
class AuthApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenService jwtTokenService;

    @InjectMocks
    private AuthApplicationService authApplicationService;

    private UUID tenantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        userId = UUID.randomUUID();
    }

    @Test
    void registerAdmin_shouldCreateTenantAndAdmin_whenEmailIsAvailable() {
        RegisterAdminRequest request = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterAdminResponse response = authApplicationService.registerAdmin(request);

        assertThat(response.user().fullName()).isEqualTo("Mario Rossi");
        assertThat(response.user().email()).isEqualTo("mario@example.com");
        assertThat(response.user().role()).isEqualTo("ADMIN");
        assertThat(response.tenant().name()).isEqualTo("Mario Rentals");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("hashed-password");
        assertThat(userCaptor.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void registerAdmin_shouldThrowConflict_whenEmailAlreadyExists() {
        RegisterAdminRequest request = new RegisterAdminRequest(
                "Mario Rossi",
                "mario@example.com",
                "Secret123!",
                "Mario Rentals"
        );

        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authApplicationService.registerAdmin(request))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessage("Email already in use.");

        verify(tenantRepository, never()).save(any(Tenant.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_shouldReturnTokenAndUserPayload_whenCredentialsAreValid() {
        LoginRequest request = new LoginRequest("operator@example.com", "Secret123!");

        User user = new User(
                userId,
                tenantId,
                "Operator User",
                "operator@example.com",
                "stored-hash",
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);
        when(jwtTokenService.generateToken(any())).thenReturn("jwt-token");

        LoginResponse response = authApplicationService.login(request);

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.user().id()).isEqualTo(userId);
        assertThat(response.user().tenantId()).isEqualTo(tenantId);
        assertThat(response.user().role()).isEqualTo("OPERATOR");

        verify(passwordEncoder).matches(eq("Secret123!"), eq("stored-hash"));
        verify(jwtTokenService).generateToken(any());
    }

    @Test
    void login_shouldThrowBadCredentials_whenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest("missing@example.com", "Secret123!");
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authApplicationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials.");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenService, never()).generateToken(any());
    }

    @Test
    void login_shouldThrowBadCredentials_whenPasswordDoesNotMatch() {
        LoginRequest request = new LoginRequest("operator@example.com", "WrongPassword");
        User user = new User(
                userId,
                tenantId,
                "Operator User",
                "operator@example.com",
                "stored-hash",
                UserRole.OPERATOR,
                UserStatus.ACTIVE
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authApplicationService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid credentials.");

        verify(jwtTokenService, never()).generateToken(any());
    }

    @Test
    void login_shouldThrowForbidden_whenUserIsDisabled() {
        LoginRequest request = new LoginRequest("disabled@example.com", "Secret123!");
        User user = new User(
                userId,
                tenantId,
                "Disabled User",
                "disabled@example.com",
                "stored-hash",
                UserRole.OPERATOR,
                UserStatus.DISABLED
        );

        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(request.password(), user.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authApplicationService.login(request))
                .isInstanceOf(ForbiddenOperationException.class)
                .hasMessage("Account is disabled.");

        verify(jwtTokenService, never()).generateToken(any());
    }
}

