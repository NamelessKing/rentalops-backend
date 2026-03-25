package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for access to the authenticated principal.
 *
 * <p>These checks ensure service-layer code gets a strongly-typed principal and
 * fails fast when security context is missing or malformed.
 */
class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUser_shouldReturnPrincipal_whenAuthenticatedUserExists() {
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UserRole.ADMIN,
                "admin@example.com",
                UserStatus.ACTIVE
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );

        AuthenticatedUser currentUser = provider.getCurrentUser();

        assertThat(currentUser).isEqualTo(principal);
    }

    @Test
    void getCurrentTenantId_shouldReturnTenantId_fromPrincipal() {
        UUID tenantId = UUID.randomUUID();
        AuthenticatedUser principal = new AuthenticatedUser(
                UUID.randomUUID(),
                tenantId,
                UserRole.OPERATOR,
                "operator@example.com",
                UserStatus.ACTIVE
        );

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null)
        );

        assertThat(provider.getCurrentTenantId()).isEqualTo(tenantId);
    }

    @Test
    void getCurrentUser_shouldThrow_whenAuthenticationIsMissing() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(provider::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated user not available in security context");
    }

    @Test
    void getCurrentUser_shouldThrow_whenPrincipalHasUnexpectedType() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("plain-string-principal", null)
        );

        assertThatThrownBy(provider::getCurrentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated user not available in security context");
    }
}

