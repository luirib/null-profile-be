package ch.nullprofile.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for masking sensitive data in logs.
 * 
 * Guidelines:
 * - Never log passwords, secret keys, tokens, or raw authentication data
 * - For connection strings: mask password, keep host/database for debugging
 * - For IDs: show length or truncated version
 * - For URLs: keep structure, mask sensitive query params if any
 */
public class SensitiveDataMasker {

    private static final Pattern JDBC_URL_PATTERN = 
        Pattern.compile("jdbc:postgresql://([^:]+):([^@]+)@([^/]+)/(.+)");
    
    private static final Pattern JDBC_URL_NO_AUTH_PATTERN = 
        Pattern.compile("jdbc:postgresql://([^/]+)/(.+)");

    /**
     * Mask a database JDBC URL, hiding the password but keeping host/db for debugging
     * 
     * @param jdbcUrl the JDBC URL
     * @return masked URL like "jdbc:postgresql://[USER]:[MASKED]@host:5432/dbname"
     *         or "jdbc:postgresql://host:5432/dbname" if no embedded auth
     */
    public static String maskDatabaseUrl(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            return "[NOT_SET]";
        }
        
        // Pattern: jdbc:postgresql://user:password@host:port/database
        Matcher matcher = JDBC_URL_PATTERN.matcher(jdbcUrl);
        if (matcher.find()) {
            String user = matcher.group(1);
            String host = matcher.group(3);
            String database = matcher.group(4);
            return String.format("jdbc:postgresql://[%s]:[MASKED]@%s/%s", 
                truncate(user, 8), host, database);
        }
        
        // Pattern: jdbc:postgresql://host:port/database (no embedded auth)
        Matcher noAuthMatcher = JDBC_URL_NO_AUTH_PATTERN.matcher(jdbcUrl);
        if (noAuthMatcher.find()) {
            String host = noAuthMatcher.group(1);
            String database = noAuthMatcher.group(2);
            return String.format("jdbc:postgresql://%s/%s", host, database);
        }
        
        // Fallback: just show it's set
        return "[DATABASE_URL_SET_BUT_UNPARSEABLE]";
    }

    /**
     * Mask a sensitive value completely
     */
    public static String maskSecret(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "[NOT_SET]";
        }
        return "[REDACTED:" + secret.length() + "_chars]";
    }

    /**
     * Show only presence/absence of a value
     */
    public static String maskPresence(String value) {
        return value != null && !value.isEmpty() ? "[SET]" : "[NOT_SET]";
    }

    /**
     * Truncate and show partial value (useful for non-secret IDs)
     */
    public static String truncate(String value, int maxLength) {
        if (value == null || value.isEmpty()) {
            return "[EMPTY]";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...[" + value.length() + "_chars]";
    }

    /**
     * Mask a session ID - show first 8 chars for correlation, hide rest
     */
    public static String maskSessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return "[NO_SESSION]";
        }
        if (sessionId.length() <= 8) {
            return sessionId.substring(0, sessionId.length() / 2) + "***";
        }
        return sessionId.substring(0, 8) + "***[" + sessionId.length() + "_chars]";
    }

    /**
     * Mask a challenge - just show length and first few chars
     */
    public static String maskChallenge(String challenge) {
        if (challenge == null || challenge.isEmpty()) {
            return "[EMPTY_CHALLENGE]";
        }
        if (challenge.length() <= 6) {
            return "[CHALLENGE:" + challenge.length() + "_chars]";
        }
        return challenge.substring(0, 6) + "...[" + challenge.length() + "_chars_total]";
    }

    /**
     * Sanitize URL for logging - keep structure, mask auth if present
     */
    public static String sanitizeUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "[NOT_SET]";
        }
        // Simple approach: if it contains @, mask the user:pass part
        if (url.contains("@")) {
            return url.replaceAll("://[^@]+@", "://[MASKED]@");
        }
        return url;
    }
}
