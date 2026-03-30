package ch.nullprofile.service;

import ch.nullprofile.config.WebAuthnProperties;
import ch.nullprofile.filter.TraceIdFilter;
import ch.nullprofile.util.SensitiveDataMasker;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class ChallengeService {

    private static final Logger logger = LoggerFactory.getLogger(ChallengeService.class);

    private static final String ATTR_REG_CHALLENGE = "webauthn.regChallenge";
    private static final String ATTR_REG_CHALLENGE_EXPIRES_AT = "webauthn.regChallengeExpiresAt";
    private static final String ATTR_REG_USER_HANDLE = "webauthn.regUserHandle";
    private static final String ATTR_AUTH_CHALLENGE = "webauthn.authChallenge";
    private static final String ATTR_AUTH_CHALLENGE_EXPIRES_AT = "webauthn.authChallengeExpiresAt";
    private static final String ATTR_WEBAUTHN_TXN = "webauthn.txn";

    private final SecureRandom secureRandom = new SecureRandom();
    private final WebAuthnProperties properties;

    public ChallengeService(WebAuthnProperties properties) {
        this.properties = properties;
    }

    /**
     * Generate and store registration challenge
     */
    public String generateAndStoreRegistrationChallenge(HttpSession session, String txn) {
        String traceId = TraceIdFilter.getCurrentTraceId();
        String sessionIdMasked = SensitiveDataMasker.maskSessionId(session.getId());
        
        logger.info("[CHALLENGE-STORE] Generating new challenge - traceId={}, session={}", traceId, sessionIdMasked);
        
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        Instant expiresAt = Instant.now().plusSeconds(properties.getChallenge().getTimeout());
        
        logger.info("[CHALLENGE-STORE] Challenge generated: length={}, expiresIn={}s, expiresAt={}", 
            challenge.length(), properties.getChallenge().getTimeout(), expiresAt);
        
        // Store in session
        session.setAttribute(ATTR_REG_CHALLENGE, challenge);
        session.setAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT, expiresAt.toString());
        
        logger.info("[CHALLENGE-STORE] Stored in session attributes: key={}, key={}", 
            ATTR_REG_CHALLENGE, ATTR_REG_CHALLENGE_EXPIRES_AT);
        
        if (txn != null) {
            session.setAttribute(ATTR_WEBAUTHN_TXN, txn);
            logger.info("[CHALLENGE-STORE] Stored OIDC txn={} in session", txn);
        }
        
        // Verify storage immediately (critical for debugging)
        String verifiedChallenge = (String) session.getAttribute(ATTR_REG_CHALLENGE);
        String verifiedExpiry = (String) session.getAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        boolean stored = verifiedChallenge != null && verifiedExpiry != null;
        
        if (stored) {
            logger.info("[CHALLENGE-STORE] ✓ Challenge verified in session immediately after storage");
            logger.info("[CHALLENGE-STORE] Challenge prefix: {}", SensitiveDataMasker.maskChallenge(challenge));
        } else {
            logger.error("[CHALLENGE-STORE] ✗ VERIFICATION FAILED - challenge not found immediately after storage!");
            logger.error("[CHALLENGE-STORE] This indicates a critical session storage issue");
        }
        
        return challenge;
    }

    /**
     * Validate and consume registration challenge
     */
    public boolean validateAndConsumeRegistrationChallenge(HttpSession session, String challenge) {
        String traceId = TraceIdFilter.getCurrentTraceId();
        String sessionIdMasked = SensitiveDataMasker.maskSessionId(session.getId());
        
        logger.info("[CHALLENGE-VALIDATE] Starting validation - traceId={}, session={}, challengePrefix={}", 
            traceId, sessionIdMasked, SensitiveDataMasker.maskChallenge(challenge));
        
        // Retrieve stored challenge
        String storedChallenge = (String) session.getAttribute(ATTR_REG_CHALLENGE);
        String expiresAtStr = (String) session.getAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        
        logger.info("[CHALLENGE-VALIDATE] Checking session for stored challenge:");
        logger.info("[CHALLENGE-VALIDATE]   Session attribute '{}' present: {}", 
            ATTR_REG_CHALLENGE, storedChallenge != null);
        logger.info("[CHALLENGE-VALIDATE]   Session attribute '{}' present: {}", 
            ATTR_REG_CHALLENGE_EXPIRES_AT, expiresAtStr != null);
        
        if (storedChallenge == null || expiresAtStr == null) {
            logger.error("[CHALLENGE-VALIDATE] ✗ CHALLENGE NOT FOUND IN SESSION");
            logger.error("[CHALLENGE-VALIDATE] Trace ID: {}", traceId);
            logger.error("[CHALLENGE-VALIDATE] Session: {}", sessionIdMasked);
            logger.error("[CHALLENGE-VALIDATE] Session is new: {}", session.isNew());
            logger.error("[CHALLENGE-VALIDATE] Session attributes present:");
            session.getAttributeNames().asIterator().forEachRemaining(name -> 
                logger.error("[CHALLENGE-VALIDATE]   - {}", name));
            
            logger.error("[CHALLENGE-VALIDATE] ========================================");
            logger.error("[CHALLENGE-VALIDATE] ROOT CAUSE DIAGNOSIS:");
            logger.error("[CHALLENGE-VALIDATE] Session was NOT maintained between OPTIONS and VERIFY requests");
            logger.error("[CHALLENGE-VALIDATE] ========================================");
            logger.error("[CHALLENGE-VALIDATE] Possible causes:");
            logger.error("[CHALLENGE-VALIDATE] 1. Browser rejected session cookie:");
            logger.error("[CHALLENGE-VALIDATE]    - SameSite=None requires Secure=true");
            logger.error("[CHALLENGE-VALIDATE]    - Check SESSION_COOKIE_SAME_SITE={} in env", "None");
            logger.error("[CHALLENGE-VALIDATE]    - Check SESSION_COOKIE_SECURE={} in env", "true");
            logger.error("[CHALLENGE-VALIDATE] 2. CORS not allowing credentials:");
            logger.error("[CHALLENGE-VALIDATE]    - Check allowCredentials=true in CORS config");
            logger.error("[CHALLENGE-VALIDATE]    - Check CORS_ALLOWED_ORIGINS matches frontend");
            logger.error("[CHALLENGE-VALIDATE] 3. Frontend not sending credentials:");
            logger.error("[CHALLENGE-VALIDATE]    - Check fetch() uses 'credentials: include'");
            logger.error("[CHALLENGE-VALIDATE] 4. Different domains without proper cookie config:");
            logger.error("[CHALLENGE-VALIDATE]    - Frontend and backend must be on HTTPS in production");
            logger.error("[CHALLENGE-VALIDATE]    - SameSite=None + Secure=true required for cross-domain");
            logger.error("[CHALLENGE-VALIDATE] ========================================");
            
            return false;
        }
        
        logger.info("[CHALLENGE-VALIDATE] ✓ Challenge found in session");
        logger.info("[CHALLENGE-VALIDATE] Stored challenge prefix: {}", 
            SensitiveDataMasker.maskChallenge(storedChallenge));
        
        // Check expiry
        Instant expiresAt = Instant.parse(expiresAtStr);
        Instant now = Instant.now();
        
        logger.info("[CHALLENGE-VALIDATE] Checking expiry:");
        logger.info("[CHALLENGE-VALIDATE]   Now: {}", now);
        logger.info("[CHALLENGE-VALIDATE]   Expires at: {}", expiresAt);
        
        if (now.isAfter(expiresAt)) {
            long secondsExpired = now.getEpochSecond() - expiresAt.getEpochSecond();
            logger.error("[CHALLENGE-VALIDATE] ✗ CHALLENGE EXPIRED");
            logger.error("[CHALLENGE-VALIDATE] Expired {} seconds ago", secondsExpired);
            logger.error("[CHALLENGE-VALIDATE] Challenge timeout configured: {} seconds", 
                properties.getChallenge().getTimeout());
            logger.error("[CHALLENGE-VALIDATE] User took too long or clock mismatch");
            return false;
        }
        
        long secondsRemaining = expiresAt.getEpochSecond() - now.getEpochSecond();
        logger.info("[CHALLENGE-VALIDATE] ✓ Challenge not expired - {} seconds remaining", secondsRemaining);
        
        // Verify challenge matches
        boolean matches = storedChallenge.equals(challenge);
        logger.info("[CHALLENGE-VALIDATE] Comparing challenges: match={}", matches);
        
        if (!matches) {
            logger.error("[CHALLENGE-VALIDATE] ✗ CHALLENGE MISMATCH");
            logger.error("[CHALLENGE-VALIDATE] Received: {}", SensitiveDataMasker.maskChallenge(challenge));
            logger.error("[CHALLENGE-VALIDATE] Expected: {}", SensitiveDataMasker.maskChallenge(storedChallenge));
            logger.error("[CHALLENGE-VALIDATE] This should not happen if session was maintained correctly");
            return false;
        }
        
        logger.info("[CHALLENGE-VALIDATE] ✓ Challenge matches stored value");
        
        // Consume (remove) the challenge to prevent replay
        session.removeAttribute(ATTR_REG_CHALLENGE);
        session.removeAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        
        logger.info("[CHALLENGE-VALIDATE] ✓ Challenge consumed (removed from session)");
        logger.info("[CHALLENGE-VALIDATE] ✓✓✓ VALIDATION SUCCESSFUL ✓✓✓");
        
        return true;
    }

    /**
     * Generate and store authentication challenge
     */
    public String generateAndStoreAuthenticationChallenge(HttpSession session, String txn) {
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        Instant expiresAt = Instant.now().plusSeconds(properties.getChallenge().getTimeout());
        
        session.setAttribute(ATTR_AUTH_CHALLENGE, challenge);
        session.setAttribute(ATTR_AUTH_CHALLENGE_EXPIRES_AT, expiresAt.toString());
        
        if (txn != null) {
            session.setAttribute(ATTR_WEBAUTHN_TXN, txn);
        }
        
        return challenge;
    }

    /**
     * Validate and consume authentication challenge
     */
    public boolean validateAndConsumeAuthenticationChallenge(HttpSession session, String challenge) {
        String storedChallenge = (String) session.getAttribute(ATTR_AUTH_CHALLENGE);
        String expiresAtStr = (String) session.getAttribute(ATTR_AUTH_CHALLENGE_EXPIRES_AT);
        
        if (storedChallenge == null || expiresAtStr == null) {
            return false;
        }
        
        // Check expiry
        Instant expiresAt = Instant.parse(expiresAtStr);
        if (Instant.now().isAfter(expiresAt)) {
            return false;
        }
        
        // Verify challenge matches
        if (!storedChallenge.equals(challenge)) {
            return false;
        }
        
        // Consume (remove) the challenge
        session.removeAttribute(ATTR_AUTH_CHALLENGE);
        session.removeAttribute(ATTR_AUTH_CHALLENGE_EXPIRES_AT);
        
        return true;
    }

    /**
     * Get stored user handle from registration session
     */
    public String getRegistrationUserHandle(HttpSession session) {
        return (String) session.getAttribute(ATTR_REG_USER_HANDLE);
    }

    /**
     * Generate and store user handle for registration
     */
    public String generateAndStoreUserHandle(HttpSession session) {
        String userHandle = java.util.UUID.randomUUID().toString();
        session.setAttribute(ATTR_REG_USER_HANDLE, userHandle);
        logger.debug("Generated and stored user handle in session: {}", userHandle);
        return userHandle;
    }

    /**
     * Clean up registration session data
     */
    public void cleanupRegistrationSession(HttpSession session) {
        session.removeAttribute(ATTR_REG_CHALLENGE);
        session.removeAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        session.removeAttribute(ATTR_REG_USER_HANDLE);
        session.removeAttribute(ATTR_WEBAUTHN_TXN);
    }

    /**
     * Clean up authentication session data
     */
    public void cleanupAuthenticationSession(HttpSession session) {
        session.removeAttribute(ATTR_AUTH_CHALLENGE);
        session.removeAttribute(ATTR_AUTH_CHALLENGE_EXPIRES_AT);
        session.removeAttribute(ATTR_WEBAUTHN_TXN);
    }

    /**
     * Get stored transaction ID
     */
    public String getTransactionId(HttpSession session) {
        return (String) session.getAttribute(ATTR_WEBAUTHN_TXN);
    }
}
