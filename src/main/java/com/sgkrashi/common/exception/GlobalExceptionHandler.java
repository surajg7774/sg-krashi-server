package com.sgkrashi.common.exception;

import com.sgkrashi.common.dto.ApiErrorResponse;
import com.sgkrashi.cropdoctor.exception.AiServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Centralized exception handling for all REST controllers across every module.
 * Ensures clients always receive a consistent {@link ApiErrorResponse} JSON body
 * and never a raw stack trace / container error page.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("RESOURCE_NOT_FOUND", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("VALIDATION_ERROR", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException ex) {
        log.warn("Business rule violated: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("BUSINESS_RULE_VIOLATION", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleConflict(ConflictException ex) {
        log.warn("Conflict: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("CONFLICT", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateResource(DuplicateResourceException ex) {
        log.warn("Duplicate resource: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("DUPLICATE_RESOURCE", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimitExceeded(RateLimitExceededException ex) {
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("RATE_LIMIT_EXCEEDED", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(AiServiceUnavailableException.class)
    public ResponseEntity<ApiErrorResponse> handleAiServiceUnavailable(AiServiceUnavailableException ex) {
        log.warn("AI Crop Doctor service call failed: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("AI_SERVICE_UNAVAILABLE", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        log.warn("Invalid token: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of("INVALID_TOKEN", ex.getMessage(), List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Authentication failed: bad credentials");
        // Deliberately generic: never reveal whether the email or the password was wrong.
        ApiErrorResponse body = ApiErrorResponse.of(
                "INVALID_CREDENTIALS", "Invalid email or password", List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    /**
     * A deactivated account authenticating with an otherwise-correct password
     * throws this, not {@code BadCredentialsException} — caught separately
     * here (Module 14) purely because it was never exercised before this
     * module's deactivation flow, and without a handler it fell through to
     * the generic 500 below. Same generic message as bad credentials,
     * deliberately: this must not become a way to enumerate which accounts
     * exist versus which have been deactivated.
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiErrorResponse> handleDisabled(DisabledException ex) {
        log.warn("Authentication failed: account disabled");
        ApiErrorResponse body = ApiErrorResponse.of(
                "INVALID_CREDENTIALS", "Invalid email or password", List.of());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        ApiErrorResponse body = ApiErrorResponse.of(
                "ACCESS_DENIED", "You do not have permission to perform this action", List.of());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("Upload exceeded the servlet container's own size limit");
        ApiErrorResponse body = ApiErrorResponse.of(
                "VALIDATION_ERROR", "File exceeds the maximum allowed size of 5MB", List.of());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .toList();
        log.warn("Request validation failed: {}", details);
        ApiErrorResponse body = ApiErrorResponse.of("VALIDATION_ERROR", "Request validation failed", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error occurred", ex);
        ApiErrorResponse body = ApiErrorResponse.of(
                "INTERNAL_SERVER_ERROR", "An unexpected error occurred", List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
