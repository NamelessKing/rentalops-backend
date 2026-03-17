package com.rentalops.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for JWT generation and parsing.
 *
 * <p>These tests guard token payload consistency because later slices derive
 * tenant and role context from JWT claims, not from client-provided fields.
 */
class JwtTokenServiceTest {

    @Test
    void generateToken_shouldIncludeExpectedClaims_andBeParsable() {
        JwtTokenService service = new JwtTokenService(new JwtProperties(
                "01234567890123456789012345678901",
                Duration.ofHours(8)
        ));

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                com.rentalops.iam.domain.model.UserRole.ADMIN,
                "admin@example.com",
                com.rentalops.iam.domain.model.UserStatus.ACTIVE
        );

        String token = service.generateToken(user);
        Claims claims = service.extractAllClaims(token);

        assertThat(claims.getSubject()).isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(claims.get("tenantId", String.class)).isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(claims.get("role", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("email", String.class)).isEqualTo("admin@example.com");
        assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    void extractAllClaims_shouldFail_whenTokenIsSignedWithDifferentSecret() {
        JwtTokenService signer = new JwtTokenService(new JwtProperties(
                "01234567890123456789012345678901",
                Duration.ofHours(8)
        ));
        JwtTokenService verifier = new JwtTokenService(new JwtProperties(
                "abcdefghijklmnopqrstuvwxyz123456",
                Duration.ofHours(8)
        ));

        AuthenticatedUser user = new AuthenticatedUser(
                UUID.randomUUID(),
                UUID.randomUUID(),
                com.rentalops.iam.domain.model.UserRole.OPERATOR,
                "operator@example.com",
                com.rentalops.iam.domain.model.UserStatus.ACTIVE
        );

        String token = signer.generateToken(user);

        assertThatThrownBy(() -> verifier.extractAllClaims(token))
                .isInstanceOf(JwtException.class);
    }
}

