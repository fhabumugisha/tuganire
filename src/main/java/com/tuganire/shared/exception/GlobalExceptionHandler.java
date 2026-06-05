package com.tuganire.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Centralised exception handler for all REST controllers.
 *
 * <p>
 * Returns RFC 9457 {@link ProblemDetail} responses so that every error has a consistent machine-readable shape. Typed
 * exceptions map to specific HTTP status codes; all others fall through to the 500 catch-all.
 *
 * <p>
 * Thymeleaf MVC controllers still work: returning a {@link ResponseEntity} from a {@link RestControllerAdvice} does not
 * prevent view-based controllers from rendering templates normally — Spring dispatches to the correct handler based on
 * the exception type and the requesting content-type.
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private static final URI TYPE_BUSINESS = URI.create("https://tuganire.com/errors/business-error");
    private static final URI TYPE_NOT_FOUND = URI.create("https://tuganire.com/errors/not-found");
    private static final URI TYPE_VALIDATION = URI.create("https://tuganire.com/errors/validation-failed");
    private static final URI TYPE_UNAUTHORIZED = URI.create("https://tuganire.com/errors/unauthorized");
    private static final URI TYPE_RATE_LIMIT = URI.create("https://tuganire.com/errors/rate-limit-exceeded");
    private static final URI TYPE_INTERNAL = URI.create("https://tuganire.com/errors/internal-error");

    private static final String PROP_TIMESTAMP = "timestamp";
    private static final String PROP_ERRORS = "errors";

    private final MessageSource messageSource;

    /**
     * {@link BusinessException} → 400 Bad Request.
     *
     * <p>
     * The message key is resolved from the {@link MessageSource} so that the user-facing text is locale-aware.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("Business exception: key={}", ex.getMessageKey());
        String message = messageSource.getMessage(ex.getMessageKey(), null, ex.getMessageKey(),
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setType(TYPE_BUSINESS);
        problem.setTitle("Business rule violation");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * {@link IllegalArgumentException} → 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex,
            HttpServletRequest request) {
        log.warn("Invalid argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(TYPE_BUSINESS);
        problem.setTitle("Invalid argument");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * {@link MethodArgumentNotValidException} → 400 Bad Request with per-field error list.
     *
     * <p>
     * This covers {@code @Valid} failures on {@code @RequestBody} parameters.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        log.warn("Validation failed: {}", ex.getBindingResult().getAllErrors());
        ProblemDetail problem = buildValidationProblem(ex, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * {@link BindException} → 400 Bad Request with per-field error list.
     *
     * <p>
     * This covers form-binding and query-parameter validation failures.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ProblemDetail> handleBindException(BindException ex, HttpServletRequest request) {
        log.warn("Bind validation failed: {}", ex.getBindingResult().getAllErrors());
        ProblemDetail problem = buildValidationProblem(ex, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /**
     * {@link ResourceNotFoundException} → 404 Not Found.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex,
            HttpServletRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(TYPE_NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * {@link UnauthorizedException} → 401 Unauthorized.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ProblemDetail> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        log.warn("Unauthorized access: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        problem.setType(TYPE_UNAUTHORIZED);
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
    }

    /**
     * {@link NoResourceFoundException} → 404 Not Found.
     *
     * <p>
     * Raised by Spring when a request maps to no static resource (e.g. {@code /favicon.ico} or an unresolved webjar
     * path). Without this handler the catch-all below would turn it into a misleading 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResourceFound(NoResourceFoundException ex,
            HttpServletRequest request) {
        log.debug("No static resource for {}", request.getRequestURI());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(TYPE_NOT_FOUND);
        problem.setTitle("Resource not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    /**
     * Catch-all → 500 Internal Server Error.
     *
     * <p>
     * Never throw {@link org.springframework.web.server.ResponseStatusException} from controllers: it would be caught
     * here and rendered as 500 (CLAUDE.md gotcha). Always throw a typed exception.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error at {}", request.getRequestURI(), ex);
        String message = messageSource.getMessage("error.unexpected", null, "An unexpected error occurred",
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, message);
        problem.setType(TYPE_INTERNAL);
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ProblemDetail buildValidationProblem(BindException ex, HttpServletRequest request) {
        String message = messageSource.getMessage("validation.failed", null, "Validation failed",
                LocaleContextHolder.getLocale());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, message);
        problem.setType(TYPE_VALIDATION);
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty(PROP_TIMESTAMP, Instant.now());
        problem.setProperty(PROP_ERRORS, ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage())).toList());
        return problem;
    }

    /** Carries field-level validation error detail within a {@link ProblemDetail} response. */
    public record FieldError(String field, String message) {
    }
}
