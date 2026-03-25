package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
/**
 * Reads the bearer token from the HTTP request and turns it into an
 * AuthenticatedUser stored inside Spring Security's context.
 *
 * <p>If you are new to Spring Security, the important idea is this:
 * once the SecurityContext is populated, the rest of the backend can ask
 * "who is the current user?" without parsing the token again.
 *
 * <p>This filter stays intentionally small:
 * - it reads the Authorization header
 * - it validates/parses the token
 * - it creates the principal object used by the application
 * - it stores that principal in the SecurityContext
 *
 * It does not call repositories and it does not apply domain rules.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        /*
         * The standard REST/JWT convention is:
         * Authorization: Bearer <token>
         *
         * If the header is missing, this filter does not fail immediately.
         * It simply lets the request continue. Protected endpoints will later
         * be rejected by Spring Security as unauthenticated.
         */
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);

        try {
            Claims claims = jwtTokenService.extractAllClaims(token);

            /*
             * The token contains the minimum context needed by the backend:
             * - subject   -> userId
             * - tenantId  -> current workspace boundary
             * - role      -> ADMIN / OPERATOR
             * - email     -> useful identity detail
             * - status    -> current user status
             *
             * The tenant is reconstructed from the token on purpose.
             * According to the API draft, tenantId must not be passed in the path
             * or request body for tenant-scoped operations.
             */
            AuthenticatedUser principal = new AuthenticatedUser(
                    UUID.fromString(claims.getSubject()),
                    UUID.fromString(claims.get("tenantId", String.class)),
                    UserRole.valueOf(claims.get("role", String.class)),
                    claims.get("email", String.class),
                    UserStatus.valueOf(claims.get("status", String.class))
            );

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()))
            );

            /*
             * This is the moment where Spring Security starts "knowing" the user.
             * From here on, services can access the principal through
             * CurrentUserProvider instead of reading headers or parsing JWTs again.
             */
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            /*
             * A malformed, expired or otherwise invalid token is treated as
             * "no valid authentication". We clear the context and let the request
             * continue; protected routes will then produce a clean 401.
             *
             * This keeps token parsing errors out of domain/business handling.
             */
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
