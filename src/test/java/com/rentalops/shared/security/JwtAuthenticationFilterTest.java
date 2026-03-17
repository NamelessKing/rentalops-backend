package com.rentalops.shared.security;

import com.rentalops.iam.domain.model.UserRole;
import com.rentalops.iam.domain.model.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for JWT filter behavior on incoming requests.
 *
 * <p>These tests protect authentication reconstruction from bearer tokens,
 * including the expected fallback path when tokens are invalid.
 */
class JwtAuthenticationFilterTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilter_shouldPopulateSecurityContext_whenBearerTokenIsValid() throws ServletException, IOException {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        Claims claims = mock(Claims.class);

        when(claims.getSubject()).thenReturn("11111111-1111-1111-1111-111111111111");
        when(claims.get("tenantId", String.class)).thenReturn("22222222-2222-2222-2222-222222222222");
        when(claims.get("role", String.class)).thenReturn("ADMIN");
        when(claims.get("email", String.class)).thenReturn("admin@example.com");
        when(claims.get("status", String.class)).thenReturn("ACTIVE");
        when(jwtTokenService.extractAllClaims("valid-token")).thenReturn(claims);

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isInstanceOfSatisfying(AuthenticatedUser.class, principal -> {
                    assertThat(principal.userId()).isEqualTo(UUID.fromString("11111111-1111-1111-1111-111111111111"));
                    assertThat(principal.tenantId()).isEqualTo(UUID.fromString("22222222-2222-2222-2222-222222222222"));
                    assertThat(principal.role()).isEqualTo(UserRole.ADMIN);
                    assertThat(principal.status()).isEqualTo(UserStatus.ACTIVE);
                });
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    @Test
    void doFilter_shouldKeepContextEmpty_whenAuthorizationHeaderIsMissing() throws ServletException, IOException {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilter_shouldClearContext_whenTokenIsInvalid() throws ServletException, IOException {
        JwtTokenService jwtTokenService = mock(JwtTokenService.class);
        when(jwtTokenService.extractAllClaims("bad-token")).thenThrow(new MalformedJwtException("Invalid"));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("old-principal", null)
        );

        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer bad-token");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

