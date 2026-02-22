package ch.nullprofile.service;

import ch.nullprofile.config.WebAuthnProperties;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class ChallengeService {

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
        byte[] challengeBytes = new byte[32];
        secureRandom.nextBytes(challengeBytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(challengeBytes);
        
        Instant expiresAt = Instant.now().plusSeconds(properties.getChallenge().getTimeout());
        
        session.setAttribute(ATTR_REG_CHALLENGE, challenge);
        session.setAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT, expiresAt.toString());
        
        if (txn != null) {
            session.setAttribute(ATTR_WEBAUTHN_TXN, txn);
        }
        
        return challenge;
    }

    /**
     * Generate and store user handle for registration
     */
    public String generateAndStoreUserHandle(HttpSession session) {
        byte[] userHandleBytes = new byte[32];
        secureRandom.nextBytes(userHandleBytes);
        String userHandle = Base64.getUrlEncoder().withoutPadding().encodeToString(userHandleBytes);
        
        session.setAttribute(ATTR_REG_USER_HANDLE, userHandle);
        return userHandle;
    }

    /**
     * Validate and consume registration challenge
     */
    public boolean validateAndConsumeRegistrationChallenge(HttpSession session, String challenge) {
        String storedChallenge = (String) session.getAttribute(ATTR_REG_CHALLENGE);
        String expiresAtStr = (String) session.getAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        
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
        session.removeAttribute(ATTR_REG_CHALLENGE);
        session.removeAttribute(ATTR_REG_CHALLENGE_EXPIRES_AT);
        
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
