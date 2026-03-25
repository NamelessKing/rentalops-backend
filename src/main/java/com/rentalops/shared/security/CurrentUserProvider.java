package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
/**
 * Small adapter around Spring Security's SecurityContext.
 *
 * <p>Without a class like this, every service would need to know how
 * Spring stores authentication internally. Centralizing that access:
 * - keeps business code cleaner
 * - avoids repeated casts
 * - makes tenant/role checks easier to express in later slices
 */
public class CurrentUserProvider {

    public AuthenticatedUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        /*
         * If this fails, something is wrong in the authentication flow:
         * the request reached code that expects a logged-in user, but no
         * AuthenticatedUser was placed in the SecurityContext.
         */
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authenticated user not available in security context"
            );
        }

        return authenticatedUser;
    }

    public UUID getCurrentTenantId() {
        // Convenience method used by tenant-scoped services so they do not have
        // to repeatedly navigate through the principal structure.
        return getCurrentUser().tenantId();
    }

    public UserRole getCurrentUserRole() {
        return getCurrentUser().role();
    }

    public UUID getCurrentUserId() {
        return getCurrentUser().userId();
    }
}
