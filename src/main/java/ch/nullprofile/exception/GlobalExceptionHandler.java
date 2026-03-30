package ch.nullprofile.exception;

import ch.nullprofile.filter.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler that ensures all errors include trace ID for debugging.
 * 
 * Provides structured error responses:
 * {
 *   "timestamp": "2026-03-30T...",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "message": "Invalid or expired registration challenge",
 *   "path": "/api/auth/webauthn/register/verify",
 *   "traceId": "abc-123-def"
 * }
 * 
 * This allows:
 * - Consistent error structure across all endpoints
 * - Frontend can display trace ID to users
 * - Easy correlation between frontend errors and backend logs
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle all generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception ex, WebRequest request) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        
        logger.error("[EXCEPTION] Unhandled exception occurred: type={}, message={}", 
            ex.getClass().getSimpleName(), 
            ex.getMessage(), 
            ex);
        
        Map<String, Object> body = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again or contact support with the trace ID.",
            request.getDescription(false).replace("uri=", ""),
            traceId
        );
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle IllegalArgumentException (validation errors, invalid inputs)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        
        logger.warn("[EXCEPTION] Invalid argument: message={}", ex.getMessage());
        
        Map<String, Object> body = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage() != null ? ex.getMessage() : "Invalid request parameters",
            request.getDescription(false).replace("uri=", ""),
            traceId
        );
        
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle IllegalStateException (invalid state, expired sessions, etc.)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        
        logger.warn("[EXCEPTION] Invalid state: message={}", ex.getMessage());
        
        Map<String, Object> body = buildErrorResponse(
            HttpStatus.BAD_REQUEST,
            ex.getMessage() != null ? ex.getMessage() : "Request cannot be processed in current state",
            request.getDescription(false).replace("uri=", ""),
            traceId
        );
        
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle NullPointerException with detailed diagnostics
     */
    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<Map<String, Object>> handleNullPointerException(
            NullPointerException ex, WebRequest request) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        
        // NPE often means a bug or unexpected null value
        logger.error("[EXCEPTION] NullPointerException - likely a configuration or logic issue: trace={}", 
            traceId, ex);
        
        Map<String, Object> body = buildErrorResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "A configuration or data error occurred. Please report this with trace ID: " + traceId,
            request.getDescription(false).replace("uri=", ""),
            traceId
        );
        
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Build standardized error response body
     */
    private Map<String, Object> buildErrorResponse(HttpStatus status, String message, 
                                                    String path, String traceId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", path);
        body.put("traceId", traceId);
        
        return body;
    }
}
