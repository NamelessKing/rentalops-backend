package com.rentalops.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Typed view of JWT-related configuration.
 *
 * <p>Instead of reading string values with scattered @Value annotations,
 * Spring binds the configuration once into this record. The result is easier
 * to test, easier to reason about and clearer for people reading the code.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        Duration expiration
) {
}
