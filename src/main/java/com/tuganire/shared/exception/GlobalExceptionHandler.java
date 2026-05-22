package com.tuganire.shared.exception;

import com.tuganire.shared.service.FreemiumService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Global exception handler for the application. Handles all exceptions thrown by controllers and provides appropriate
 * error responses.
 */
@ControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final FreemiumService freemiumService;

    /**
     * Handles business logic exceptions. Returns a user-friendly error page with the localized error message.
     */
    @ExceptionHandler(BusinessException.class)
    public String handleBusinessException(BusinessException ex, Model model) {
        log.warn("Business exception: {}", ex.getMessageKey());

        String message = messageSource.getMessage(ex.getMessageKey(), new Object[]{freemiumService.getMaxItems()},
                ex.getMessageKey(), LocaleContextHolder.getLocale());

        return prepareErrorView(model, message);
    }

    /**
     * Handles illegal argument exceptions. Returns an error page with the error message.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public String handleIllegalArgumentException(IllegalArgumentException ex, Model model) {
        log.error("Invalid argument: {}", ex.getMessage());
        return prepareErrorView(model, ex.getMessage());
    }

    /**
     * Handles file size exceeded exceptions.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, Model model) {
        log.error("File size exceeded: {}", ex.getMessage());

        String message = messageSource.getMessage("error.upload.size-exceeded", null,
                "File size exceeded the maximum allowed size", LocaleContextHolder.getLocale());

        return prepareErrorView(model, message);
    }

    /**
     * Handles file upload exceptions.
     */
    @ExceptionHandler(FileUploadException.class)
    public String handleFileUploadException(FileUploadException ex, Model model) {
        log.error("File upload failed: {}", ex.getMessage(), ex);

        String baseMessage = messageSource.getMessage("error.upload.failed", null, "Failed to upload file",
                LocaleContextHolder.getLocale());

        return prepareErrorView(model, baseMessage + ": " + ex.getMessage());
    }

    /**
     * Handles validation errors from form submissions. Returns a 400 Bad Request error page.
     */
    @ExceptionHandler(BindException.class)
    public String handleValidationException(BindException ex, Model model) {
        log.warn("Validation failed: {}", ex.getBindingResult().getAllErrors());

        String message = messageSource.getMessage("validation.failed", null, "Validation failed",
                LocaleContextHolder.getLocale());

        model.addAttribute("error", message);
        model.addAttribute("errors", ex.getBindingResult().getAllErrors());
        return "error/400";
    }

    /**
     * Handles resource not found exceptions. Returns a 404 Not Found error page.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public String handleResourceNotFoundException(ResourceNotFoundException ex, Model model) {
        log.error("Resource not found: {}", ex.getMessage(), ex);
        model.addAttribute("error", ex.getMessage());
        return "error/404";
    }

    /**
     * Handles unauthorized access exceptions. Redirects to the login page with an error message.
     */
    @ExceptionHandler(UnauthorizedException.class)
    public String handleUnauthorizedException(UnauthorizedException ex, RedirectAttributes redirectAttributes) {
        log.error("Unauthorized access: {}", ex.getMessage(), ex);
        redirectAttributes.addFlashAttribute("error", ex.getMessage());
        return "redirect:/login";
    }

    /**
     * Handles data integrity violations (unique constraints, foreign keys).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<String> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body("error.conflict");
    }

    /**
     * Handles async request timeout exceptions from SSE connections.
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        log.warn("Async request timeout (SSE connection)", ex);
        return ResponseEntity.noContent().build();
    }

    /**
     * Handles rate limit exceeded exceptions. Returns HTTP 429 Too Many Requests with proper Retry-After header.
     * Returns HTML fragment for HTMX requests, JSON for API clients.
     */
    @ExceptionHandler(TooManyRequestException.class)
    public Object handleTooManyRequestException(TooManyRequestException ex, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        log.warn("Rate limit exceeded: {} {}", request.getMethod(), request.getRequestURI());

        response.setHeader("X-Rate-Limit-Limit", String.valueOf(ex.getMaxRequests()));
        response.setHeader("X-Rate-Limit-Remaining", String.valueOf(ex.getRemainingRequests()));
        response.setHeader("X-Rate-Limit-Reset", String.valueOf(ex.getResetTime()));

        long secondsUntilReset = Math.max(1, (ex.getResetTime() - System.currentTimeMillis()) / 1000);
        response.setHeader("Retry-After", String.valueOf(secondsUntilReset));

        boolean isHtmxRequest = "true".equals(request.getHeader("HX-Request"));

        if (isHtmxRequest) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            model.addAttribute("message", ex.getMessage());
            model.addAttribute("resetTime", ex.getResetTime());
            model.addAttribute("maxRequests", ex.getMaxRequests());
            return "fragments/rate-limit :: error";
        }

        String errorMessage = messageSource.getMessage("rate.limit.error.title", null, "Too Many Requests",
                LocaleContextHolder.getLocale());

        Map<String, Object> body = new HashMap<>();
        body.put("error", errorMessage);
        body.put("message", ex.getMessage());
        body.put("maxRequests", ex.getMaxRequests());
        body.put("resetTime", ex.getResetTime());
        body.put("retryAfter", secondsUntilReset);

        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    /**
     * Handles all unexpected exceptions. Returns a generic 500 Internal Server Error page.
     */
    @ExceptionHandler(Exception.class)
    public String handleGenericException(Exception ex, Model model) {
        log.error("Unexpected error", ex);

        String message = messageSource.getMessage("error.unexpected", null, "An unexpected error occurred",
                LocaleContextHolder.getLocale());

        model.addAttribute("error", message);
        return "error/500";
    }

    /**
     * Utility method to prepare an error view with an error message.
     */
    private String prepareErrorView(Model model, String errorMessage) {
        model.addAttribute("error", errorMessage);
        return "error/business-error";
    }
}
