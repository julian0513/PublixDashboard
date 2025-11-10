package com.julian.publixai.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.Instant;
import java.util.*;

/**
 * ApiExceptionHandler
 *
 * Purpose: Provide a single, consistent error shape for all controllers.
 * Shape:
 * {
 *   "timestamp": "2025-11-03T22:41:12.345Z",
 *   "status": 400,
 *   "error": "bad_request",
 *   "message": "mode must be 'seed' or 'live'",
 *   "path": "/api/forecast",
 *   "details": { ... optional, e.g., fieldErrors ... }
 * }
 *
 * Notes:
 * - 4xx include specific messages to guide callers.
 * - 5xx hide internals but are logged server-side.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // ---------- Public error record ----------
    public record ApiError(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, Object> details
    ) {}

    // ---------- Helpers ----------
    private ResponseEntity<ApiError> build(HttpStatus status, String code, String message,
                                           HttpServletRequest req, Map<String, Object> details) {
        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                code,
                message,
                req != null ? req.getRequestURI() : "",
                (details == null || details.isEmpty()) ? null : details
        );
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }

    // ---------- 400: Bad Request family ----------
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", ex.getMessage(), req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", Optional.ofNullable(fe.getDefaultMessage()).orElse("invalid value")))
                .toList();
        Map<String, Object> details = Map.of("fieldErrors", fieldErrors);
        return build(HttpStatus.BAD_REQUEST, "validation_error", "One or more fields are invalid.", req, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        List<Map<String, String>> violations = ex.getConstraintViolations()
                .stream()
                .map(v -> Map.of(
                        "property", resolvePath(v),
                        "message", Optional.ofNullable(v.getMessage()).orElse("invalid value")))
                .toList();
        Map<String, Object> details = Map.of("violations", violations);
        return build(HttpStatus.BAD_REQUEST, "validation_error", "One or more constraints failed.", req, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleNotReadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "bad_request", "Malformed JSON or body.", req, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String msg = "Parameter '%s' has invalid value '%s'.".formatted(
                ex.getName(), String.valueOf(ex.getValue()));
        Map<String, Object> details = Map.of(
                "parameter", ex.getName(),
                "expectedType", ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown"
        );
        return build(HttpStatus.BAD_REQUEST, "bad_request", msg, req, details);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        String msg = "Missing required parameter '%s'.".formatted(ex.getParameterName());
        Map<String, Object> details = Map.of("parameter", ex.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "bad_request", msg, req, details);
    }

    // ---------- 404 ----------
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "not_found", ex.getMessage(), req, null);
    }

    // ---------- 405 / 415 ----------
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        if (ex.getSupportedHttpMethods() != null) {
            details.put("allowed", ex.getSupportedHttpMethods());
        }
        return build(HttpStatus.METHOD_NOT_ALLOWED, "method_not_allowed",
                "HTTP method not supported.", req, details);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        Map<String, Object> details = new HashMap<>();
        if (ex.getSupportedMediaTypes() != null && !ex.getSupportedMediaTypes().isEmpty()) {
            details.put("supported", ex.getSupportedMediaTypes());
        }
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported_media_type",
                "Content-Type not supported.", req, details);
    }

    // ---------- 500 fallback ----------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex, HttpServletRequest req) {
        // Only server-side log for 5xx; do not leak internal details to clients
        log.error("Unhandled exception on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.toString(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "server_error",
                "Unexpected server error.", req, null);
    }

    // ---------- Small utility ----------
    private static String resolvePath(ConstraintViolation<?> v) {
        return v.getPropertyPath() != null ? v.getPropertyPath().toString() : "";
    }
}
