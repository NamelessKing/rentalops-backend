package com.rentalops.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed view of JWT-related configuration.
 *
 * <p>Instead of reading string values with scattered @Value annotations,
 * Spring binds the configuration once into this record. The result is easier
 * to test, easier to reason about and clearer for people reading the code.
 *
 * <p>The {@code @Validated} annotation causes Spring to fail fast at startup
 * with a clear message if the JWT secret is missing or too short, or if the
 * expiration duration is absent.
 */
@ConfigurationProperties(prefix = "app.jwt")
@Validated
public record JwtProperties(
        @NotBlank
        @Size(min = 32, message = "JWT secret must be at least 32 characters long to satisfy HMAC-SHA256 strength requirements")
        String secret,
        @NotNull
        Duration expiration
) {
}
