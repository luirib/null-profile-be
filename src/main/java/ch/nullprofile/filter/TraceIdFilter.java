package ch.nullprofile.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that adds a correlation/trace ID to every HTTP request for distributed tracing.
 * 
 * Flow:
 * 1. Check for incoming X-Trace-Id header
 * 2. If missing, generate new UUID
 * 3. Store in MDC (so it appears in all logs)
 * 4. Add to response header (so frontend can read it)
 * 5. Clean up MDC after request completes
 * 
 * This enables:
 * - Correlating all logs for a single request
 * - Frontend displaying trace ID in error messages
 * - Easy debugging by searching logs by trace ID
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TraceIdFilter.class);
    
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response, 
                                    FilterChain filterChain) throws ServletException, IOException {
        
        String traceId = extractOrGenerateTraceId(request);
        
        try {
            // Store in MDC so it appears in all logs
            MDC.put(TRACE_ID_MDC_KEY, traceId);
            
            // Add to response header so frontend can read it
            response.setHeader(TRACE_ID_HEADER, traceId);
            
            // Log basic request info with trace ID
            logger.debug("Request started: method={}, uri={}, origin={}, referer={}", 
                request.getMethod(), 
                request.getRequestURI(),
                request.getHeader("Origin"),
                request.getHeader("Referer"));
            
            filterChain.doFilter(request, response);
            
        } finally {
            // Always clean up MDC to prevent memory leaks
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private String extractOrGenerateTraceId(HttpServletRequest request) {
        // Check if client provided trace ID
        String traceId = request.getHeader(TRACE_ID_HEADER);
        
        if (traceId == null || traceId.trim().isEmpty()) {
            // Generate new trace ID
            traceId = UUID.randomUUID().toString();
            logger.trace("Generated new trace ID: {}", traceId);
        } else {
            logger.trace("Using client-provided trace ID: {}", traceId);
        }
        
        return traceId;
    }
    
    /**
     * Get the current trace ID from MDC (available during request processing)
     */
    public static String getCurrentTraceId() {
        String traceId = MDC.get(TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : "unknown";
    }
}
