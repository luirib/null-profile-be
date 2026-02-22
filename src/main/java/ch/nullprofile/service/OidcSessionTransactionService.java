package ch.nullprofile.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
public class OidcSessionTransactionService {

    private static final String ATTR_RP_ID = "oidc.rpId";
    private static final String ATTR_REDIRECT_URI = "oidc.redirectUri";
    private static final String ATTR_CODE_CHALLENGE = "oidc.codeChallenge";
    private static final String ATTR_CODE_CHALLENGE_METHOD = "oidc.codeChallengeMethod";
    private static final String ATTR_NONCE = "oidc.nonce";
    private static final String ATTR_STATE = "oidc.state";
    private static final String ATTR_USER_ID = "oidc.userId";
    private static final String ATTR_AUTH_CODE_HASH = "oidc.authCodeHash";
    private static final String ATTR_AUTH_CODE_EXPIRES_AT = "oidc.authCodeExpiresAt";

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Store OIDC authorization transaction in session
     */
    public void storeAuthorizationRequest(
            HttpSession session,
            String rpId,
            String redirectUri,
            String codeChallenge,
            String codeChallengeMethod,
            String nonce,
            String state) {
        session.setAttribute(ATTR_RP_ID, rpId);
        session.setAttribute(ATTR_REDIRECT_URI, redirectUri);
        session.setAttribute(ATTR_CODE_CHALLENGE, codeChallenge);
        session.setAttribute(ATTR_CODE_CHALLENGE_METHOD, codeChallengeMethod);
        session.setAttribute(ATTR_NONCE, nonce);
        session.setAttribute(ATTR_STATE, state);
    }

    /**
     * Mark session as authenticated with userId
     */
    public void setAuthenticatedUser(HttpSession session, UUID userId) {
        session.setAttribute(ATTR_USER_ID, userId.toString());
    }

    /**
     * Generate authorization code, store hash in session
     * Returns the plaintext code to be sent to client
     */
    public String generateAndStoreAuthCode(HttpSession session) {
        // Generate random auth code
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String authCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Hash the code
        String authCodeHash = sha256Hash(authCode);

        // Store hash and expiry in session
        session.setAttribute(ATTR_AUTH_CODE_HASH, authCodeHash);
        session.setAttribute(ATTR_AUTH_CODE_EXPIRES_AT, Instant.now().plusSeconds(300).toString()); // 5 minutes

        return authCode;
    }

    /**
     * Validate and consume authorization code (one-time use)
     * Returns true if valid, false otherwise
     */
    public boolean validateAndConsumeAuthCode(HttpSession session, String authCode) {
        String storedHash = (String) session.getAttribute(ATTR_AUTH_CODE_HASH);
        String expiresAtStr = (String) session.getAttribute(ATTR_AUTH_CODE_EXPIRES_AT);

        if (storedHash == null || expiresAtStr == null) {
            return false;
        }

        // Check expiry
        Instant expiresAt = Instant.parse(expiresAtStr);
        if (Instant.now().isAfter(expiresAt)) {
            return false;
        }

        // Verify hash
        String providedHash = sha256Hash(authCode);
        if (!storedHash.equals(providedHash)) {
            return false;
        }

        // Consume (remove) the code
        session.removeAttribute(ATTR_AUTH_CODE_HASH);
        session.removeAttribute(ATTR_AUTH_CODE_EXPIRES_AT);

        return true;
    }

    /**
     * Validate PKCE code verifier
     */
    public boolean validatePkce(HttpSession session, String codeVerifier) {
        String storedChallenge = (String) session.getAttribute(ATTR_CODE_CHALLENGE);
        String storedMethod = (String) session.getAttribute(ATTR_CODE_CHALLENGE_METHOD);

        if (storedChallenge == null || !"S256".equals(storedMethod)) {
            return false;
        }

        // Compute SHA-256 of verifier
        String computedChallenge = sha256Base64Url(codeVerifier);
        return storedChallenge.equals(computedChallenge);
    }

    // Getters for session attributes

    public String getRpId(HttpSession session) {
        return (String) session.getAttribute(ATTR_RP_ID);
    }

    public String getRedirectUri(HttpSession session) {
        return (String) session.getAttribute(ATTR_REDIRECT_URI);
    }

    public String getNonce(HttpSession session) {
        return (String) session.getAttribute(ATTR_NONCE);
    }

    public String getState(HttpSession session) {
        return (String) session.getAttribute(ATTR_STATE);
    }

    public String getUserId(HttpSession session) {
        return (String) session.getAttribute(ATTR_USER_ID);
    }

    public boolean isAuthenticated(HttpSession session) {
        return session.getAttribute(ATTR_USER_ID) != null;
    }

    // Helper methods

    private String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private String sha256Base64Url(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
