package com.rentalops.shared.api;

import com.rentalops.shared.exceptions.BusinessConflictException;
import com.rentalops.shared.exceptions.DomainValidationException;
import com.rentalops.shared.exceptions.ForbiddenOperationException;
import com.rentalops.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the ProblemDetail error contract exposed by ApiExceptionHandler.
 *
 * <p>The frontend relies on a predictable HTTP status code, title and detail for
 * every error category. These tests make regressions in that mapping visible
 * immediately without requiring an HTTP request or a running application context.
 *
 * <p>The handler has no dependencies, so it is instantiated directly —
 * no mocking framework is needed.
 */
class ApiExceptionHandlerTest {

    private ApiExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ApiExceptionHandler();
    }

    @Test
    void handleRequestValidation_shouldReturn400_withFieldErrorsMap() {
        // Simulate a bean-validation failure on the "email" field.
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "email", "must not be blank"));

        // MethodParameter requires a real Method reference; we use Object#toString as a
        // neutral placeholder since the handler only reads the BindingResult, not the parameter.
        MethodParameter stubParameter;
        try {
            stubParameter = new MethodParameter(Object.class.getMethod("toString"), -1);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Object#toString must exist", e);
        }
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(stubParameter, bindingResult);

        ProblemDetail problem = handler.handleRequestValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Validation failed");

        // The "errors" property is what the frontend reads to show per-field messages.
        assertThat(problem.getProperties()).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) problem.getProperties().get("errors");
        assertThat(errors).containsEntry("email", "must not be blank");
    }

    @Test
    void handleDomainValidation_shouldReturn400_withDomainMessage() {
        DomainValidationException ex = new DomainValidationException("assigneeId must be null for POOL tasks.");

        ProblemDetail problem = handler.handleDomainValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Domain validation failed");
        assertThat(problem.getDetail()).isEqualTo("assigneeId must be null for POOL tasks.");
    }

    @Test
    void handleAuthentication_shouldReturn401_withMessage() {
        // 401 = we don't know who you are (missing or invalid token).
        BadCredentialsException ex = new BadCredentialsException("Bad credentials");

        ProblemDetail problem = handler.handleAuthentication(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Authentication required");
    }

    @Test
    void handleForbidden_shouldReturn403_withMessage() {
        // 403 = we know who you are, but you are not allowed to do this.
        ForbiddenOperationException ex = new ForbiddenOperationException("Only ADMIN users can create tasks.");

        ProblemDetail problem = handler.handleForbidden(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Forbidden operation");
        assertThat(problem.getDetail()).isEqualTo("Only ADMIN users can create tasks.");
    }

    @Test
    void handleNotFound_shouldReturn404_withMessage() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Task not found: abc-123");

        ProblemDetail problem = handler.handleNotFound(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Task not found: abc-123");
    }

    @Test
    void handleConflict_shouldReturn409_withDomainMessage() {
        BusinessConflictException ex = new BusinessConflictException("Task already claimed by another operator.");

        ProblemDetail problem = handler.handleConflict(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Business conflict");
        assertThat(problem.getDetail()).isEqualTo("Task already claimed by another operator.");
    }

    @Test
    void handleOptimisticLocking_shouldReturn409_withRetryMessage() {
        // This handler is a safety net for optimistic lock failures that escape the
        // service-layer try/catch. The message is generic because the caller has no
        // additional context at this point.
        ObjectOptimisticLockingFailureException ex =
                new ObjectOptimisticLockingFailureException("Task", new RuntimeException());

        ProblemDetail problem = handler.handleOptimisticLocking(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Concurrent modification conflict");
        assertThat(problem.getDetail()).contains("Please retry");
    }
}



