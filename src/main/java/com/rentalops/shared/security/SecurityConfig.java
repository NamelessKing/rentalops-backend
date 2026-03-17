package com.rentalops.shared.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Central Spring Security configuration for the MVP foundation setup.
 *
 * <p>This class does not contain business authorization rules such as
 * "only admins can create properties" or "only the assignee can complete a task".
 * Those checks belong to the application/service layer, as required by the
 * project documents. Here we only define the HTTP security perimeter:
 * which routes are public, which routes require authentication and which
 * filter is responsible for reading the JWT token.
 *
 * <p>The project uses stateless JWT authentication instead of server-side
 * sessions. In practice this means:
 * - the backend does not keep login state in memory or in an HTTP session
 * - every protected request must bring its own bearer token
 * - the token is parsed on each request and transformed into an application principal
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    /*
     * Only a very small set of routes is public in Slice 0:
     * - /auth/** for future login/register endpoints
     * - Swagger/OpenAPI endpoints so the API remains inspectable during development
     * - /error so framework-generated errors are still reachable
     *
     * Everything else is protected by default. This is safer than trying to
     * remember to protect each new endpoint one by one in later slices.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/auth/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            JwtAuthenticationFilter jwtAuthenticationFilter,
                                            RestAuthenticationEntryPoint restAuthenticationEntryPoint) throws Exception {
        return http
                // Allow frontend preflight and cross-origin auth calls in local development.
                .cors(cors -> {
                })
                /*
                 * CSRF protection is mainly useful for browser session/cookie based apps.
                 * Here we are building a stateless REST API with bearer tokens, so CSRF
                 * would add noise without solving the main threat model of this MVP.
                 */
                .csrf(AbstractHttpConfigurer::disable)

                /*
                 * Disable Spring's default HTML login page and HTTP Basic popup because
                 * the frontend will authenticate through JSON endpoints and bearer tokens.
                 */
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                /*
                 * STATELESS is the key switch for JWT-based auth:
                 * Spring Security must not create or reuse server-side sessions.
                 * Every request is evaluated independently from its Authorization header.
                 */
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                /*
                 * If authentication fails before reaching a controller, Spring Security
                 * does not go through @RestControllerAdvice. This entry point makes those
                 * 401 responses still look like REST API errors instead of generic defaults.
                 */
                .exceptionHandling(ex -> ex.authenticationEntryPoint(restAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated()
                )

                /*
                 * This filter runs before Spring's username/password filter because
                 * our authentication source is the JWT bearer token, not a form submit.
                 *
                 * Important architectural rule:
                 * the filter only builds the authenticated principal from the token.
                 * It does NOT decide domain permissions such as tenant ownership
                 * or task lifecycle transitions.
                 */
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(
                "http://127.0.0.1:5173",
                "http://localhost:5173"
        ));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        /*
         * Password hashing is exposed as a bean so future auth use cases can reuse
         * the same strategy in a single place instead of instantiating encoders ad hoc.
         */
        return new BCryptPasswordEncoder();
    }
}
