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
import com.rentalops.shared.security.AuthenticatedUser;
import com.rentalops.shared.security.JwtTokenService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Handles the register-admin and login use cases.
 *
 * <p>This service is the single place where authentication business rules
 * are enforced: email uniqueness, password verification, account status
 * checks and token generation. Keeping these rules here prevents them
 * from leaking into the controller or the filter layer.
 */
@Service
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AuthApplicationService(UserRepository userRepository,
                                  TenantRepository tenantRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtTokenService jwtTokenService) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Creates the initial admin user and their tenant workspace.
     *
     * <p>Both tenant and user are persisted in a single transaction so a
     * failure in either step does not leave orphaned records in the database.
     */
    @Transactional
    public RegisterAdminResponse registerAdmin(RegisterAdminRequest request) {

        // Guard before persisting so the client gets a clean 409 rather than
        // a database constraint violation bubbling up as an unexpected error.
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessConflictException("Email already in use.");
        }

        Tenant tenant = tenantRepository.save(
                new Tenant(UUID.randomUUID(), request.workspaceName())
        );

        User admin = userRepository.save(
                new User(
                        UUID.randomUUID(),
                        tenant.getId(),
                        request.fullName(),
                        request.email(),
                        // Hash here, never before — the plain-text password
                        // must not be stored or logged anywhere in the system.
                        passwordEncoder.encode(request.password()),
                        UserRole.ADMIN,
                        UserStatus.ACTIVE
                )
        );

        return new RegisterAdminResponse(
                new RegisterAdminResponse.UserPayload(
                        admin.getId(),
                        admin.getFullName(),
                        admin.getEmail(),
                        admin.getRole().name()
                ),
                new RegisterAdminResponse.TenantPayload(
                        tenant.getId(),
                        tenant.getName()
                )
        );
    }

    /**
     * Authenticates a user and returns a signed JWT token.
     *
     * <p>The three checks happen in a deliberate order: existence first,
     * password second, status third. This avoids giving attackers information
     * about which specific check failed.
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials."));

        // Use constant-time comparison via PasswordEncoder to prevent
        // timing attacks on password verification.
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BadCredentialsException("Invalid credentials.");
        }

        // A disabled user must not receive a token, even with correct credentials.
        if (user.isDisabled()) {
            throw new ForbiddenOperationException("Account is disabled.");
        }

        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
                user.getId(),
                user.getTenantId(),
                user.getRole(),
                user.getEmail(),
                user.getStatus()
        );

        String token = jwtTokenService.generateToken(authenticatedUser);

        return new LoginResponse(
                token,
                new LoginResponse.UserPayload(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getTenantId()
                )
        );
    }
}
