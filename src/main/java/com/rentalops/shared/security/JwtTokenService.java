package com.rentalops.shared.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
/**
 * Single place where JWT tokens are created and parsed.
 *
 * <p>The rest of the codebase should not know JWT library details.
 * Controllers and application services should deal with AuthenticatedUser,
 * not with low-level token APIs or cryptographic keys.
 */
public class JwtTokenService {

    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = Instant.now();

        return Jwts.builder()
                /*
                 * We deliberately keep the token payload small but sufficient.
                 * The backend needs:
                 * - who the user is
                 * - which tenant/workspace the user belongs to
                 * - which role the user has
                 *
                 * This avoids pushing tenantId through every HTTP contract and
                 * keeps later slices aligned with the API draft.
                 */
                .subject(user.userId().toString())
                .claim("tenantId", user.tenantId().toString())
                .claim("role", user.role().name())
                .claim("email", user.email())
                .claim("status", user.status().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(jwtProperties.expiration())))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims extractAllClaims(String token) {
        /*
         * verifyWith(...) is what makes the token trustworthy:
         * the signature must match the configured secret key.
         * If it does not, JJWT throws and the filter treats the request
         * as unauthenticated.
         */
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        /*
         * The secret is read from external configuration, not hardcoded in Java.
         * This is both a security requirement and a practical one:
         * each environment can provide its own secret without changing the code.
         */
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }
}
