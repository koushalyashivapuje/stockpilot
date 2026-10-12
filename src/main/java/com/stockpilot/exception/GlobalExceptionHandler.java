package com.stockpilot.exception;

import com.stockpilot.dto.common.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates every exception into the same {@link ErrorResponse} shape and an appropriate
 * HTTP status, and makes sure nothing sensitive (stack traces, SQL, secrets) reaches the client.
 * Full details are logged server-side only.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fieldErrors.put(fe.getField(), fe.getDefaultMessage()));
        ErrorResponse body = ErrorResponse.withFieldErrors(400, "VALIDATION_FAILED",
                "One or more fields are invalid.", req.getRequestURI(), fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "BUSINESS_RULE_VIOLATION",
                ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "CONFLICT",
                ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({UnauthorizedException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.of(401, "UNAUTHORIZED",
                ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of(403, "FORBIDDEN",
                "You do not have permission to perform this action.", req.getRequestURI()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse.of(404, "NOT_FOUND",
                ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class})
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation at {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "DATA_INTEGRITY_VIOLATION",
                "The request conflicts with an existing record (e.g. a duplicate SKU).", req.getRequestURI()));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of(409, "CONCURRENT_UPDATE",
                "This record was modified concurrently. Please retry.", req.getRequestURI()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(ErrorResponse.of(400, "BAD_REQUEST",
                ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {}", req.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(ErrorResponse.of(500, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again later.", req.getRequestURI()));
    }
}
