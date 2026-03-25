package com.rentalops.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
/**
 * Produces the HTTP 401 response used by Spring Security before a request
 * reaches controllers.
 *
 * <p>This is necessary because exceptions thrown inside the security filter
 * chain do not always pass through the normal @RestControllerAdvice flow.
 * Without this component, unauthenticated requests could end up with less
 * consistent responses than the rest of the API.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        /*
         * The response is written manually because we are still inside the
         * security layer, before controller handling kicks in.
         *
         * The chosen payload mirrors ProblemDetail so clients still receive
         * a REST-friendly error format even at this early stage of the request.
         */
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {
                  "title": "Unauthorized",
                  "status": 401,
                  "detail": "Authentication is required to access this resource."
                }
                """);
    }
}
