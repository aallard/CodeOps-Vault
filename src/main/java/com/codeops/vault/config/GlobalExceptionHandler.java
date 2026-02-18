package com.codeops.vault.config;

import com.codeops.vault.dto.response.ErrorResponse;
import com.codeops.vault.exception.AuthorizationException;
import com.codeops.vault.exception.CodeOpsVaultException;
import com.codeops.vault.exception.NotFoundException;
import com.codeops.vault.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralized exception handler for all REST controllers in the Vault API.
 *
 * <p>Catches application-specific exceptions ({@link NotFoundException}, {@link ValidationException},
 * {@link AuthorizationException}), Spring Security exceptions ({@link AccessDeniedException}),
 * and general uncaught exceptions. Each handler returns a structured {@link ErrorResponse} with the
 * appropriate HTTP status code.</p>
 *
 * <p>Internal error details are never exposed to clients.</p>
 *
 * @see ErrorResponse
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles application-level not-found exceptions.
     *
     * @param ex the exception
     * @return 404 response with the exception message
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse(ex.getMessage(), Instant.now().toString()));
    }

    /**
     * Handles application-level validation exceptions.
     *
     * @param ex the exception
     * @return 400 response with the exception message
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse(ex.getMessage(), Instant.now().toString()));
    }

    /**
     * Handles application-level authorization exceptions.
     *
     * @param ex the exception
     * @return 403 response with the exception message
     */
    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<ErrorResponse> handleAuthorization(AuthorizationException ex) {
        log.warn("Authorization denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse(ex.getMessage(), Instant.now().toString()));
    }

    /**
     * Handles Spring Security access denied exceptions.
     *
     * @param ex the exception
     * @return 403 response with generic access denied message
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(403).body(new ErrorResponse("Access denied", Instant.now().toString()));
    }

    /**
     * Handles bean validation failures on request DTOs.
     *
     * @param ex the exception containing field errors
     * @return 400 response with concatenated field validation messages
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", msg);
        return ResponseEntity.status(400).body(new ErrorResponse(msg, Instant.now().toString()));
    }

    /**
     * Handles malformed request bodies (invalid JSON, bad enum values, etc.).
     *
     * @param ex the exception
     * @return 400 response with generic malformed body message
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(400).body(new ErrorResponse("Malformed request body", Instant.now().toString()));
    }

    /**
     * Handles requests to unmapped paths.
     *
     * @param ex the exception
     * @return 404 response with generic resource not found message
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("No resource found: {}", ex.getMessage());
        return ResponseEntity.status(404).body(new ErrorResponse("Resource not found", Instant.now().toString()));
    }

    /**
     * Catches any unhandled Vault application exception.
     *
     * @param ex the exception
     * @return 500 response with generic error message (details logged server-side)
     */
    @ExceptionHandler(CodeOpsVaultException.class)
    public ResponseEntity<ErrorResponse> handleCodeOpsVault(CodeOpsVaultException ex) {
        log.error("Application exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(500).body(new ErrorResponse("An unexpected error occurred", Instant.now().toString()));
    }

    /**
     * Catches all other unhandled exceptions as a safety net.
     *
     * @param ex the exception
     * @return 500 response with generic error message (details logged server-side)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(new ErrorResponse("An unexpected error occurred", Instant.now().toString()));
    }
}
