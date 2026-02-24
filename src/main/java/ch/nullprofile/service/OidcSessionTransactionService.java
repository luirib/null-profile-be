package ch.nullprofile.service;

import ch.nullprofile.config.OidcProperties;
import ch.nullprofile.dto.OidcTransaction;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing OIDC transactions in HTTP session
 * Authorization codes are stored in-memory indexed by code for session-independent token exchange
 */
@Service
public class OidcSessionTransactionService {

    private static final Logger logger = LoggerFactory.getLogger(OidcSessionTransactionService.class);
    private static final String ATTR_OIDC_TXN = "OIDC_TXN";
    private static final String ATTR_USER_ID = "USER_ID"; // Global session auth state

    private final SecureRandom secureRandom = new SecureRandom();
    private final OidcProperties oidcProperties;
    
    // Store authorization codes independently of sessions
    private final ConcurrentHashMap<String, AuthCodeEntry> authCodeStore = new ConcurrentHashMap<>();
    
    /**
     * Entry for authorization code store
     */
    private record AuthCodeEntry(
            String codeHash,
            OidcTransaction transaction,
            Instant expiresAt,
            boolean consumed
    ) {}

    public OidcSessionTransactionService(OidcProperties oidcProperties) {
        this.oidcProperties = oidcProperties;
    }

    /**
     * Create and store a new OIDC transaction in session
     */
    public OidcTransaction createTransaction(
            HttpSession session,
            String rpId,
            String redirectUri,
            String scope,
            String state,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            boolean authnRequired) {

        OidcTransaction txn = OidcTransaction.createNew(
                rpId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                authnRequired
        );

        session.setAttribute(ATTR_OIDC_TXN, txn);
        
        logger.info("Created OIDC transaction: txnId={}, rpId={}, authnRequired={}", 
                txn.txnId(), rpId, authnRequired);
        
        return txn;
    }

    /**
     * Get current transaction from session
     */
    public Optional<OidcTransaction> getTransaction(HttpSession session) {
        OidcTransaction txn = (OidcTransaction) session.getAttribute(ATTR_OIDC_TXN);
        return Optional.ofNullable(txn);
    }

    /**
     * Update transaction in session
     */
    public void updateTransaction(HttpSession session, OidcTransaction txn) {
        session.setAttribute(ATTR_OIDC_TXN, txn);
    }

    /**
     * Clear transaction from session
     */
    public void clearTransaction(HttpSession session) {
        session.removeAttribute(ATTR_OIDC_TXN);
        logger.debug("Cleared OIDC transaction from session");
    }

    /**
     * Mark transaction with authenticated user
     */
    public OidcTransaction authenticateTransaction(HttpSession session, UUID userId) {
        OidcTransaction txn = getTransaction(session)
                .orElseThrow(() -> new IllegalStateException("No transaction in session"));

        OidcTransaction authenticatedTxn = txn.withAuthenticatedUser(userId);
        updateTransaction(session, authenticatedTxn);
        
        logger.info("Authenticated OIDC transaction: txnId={}, userId={}", 
                authenticatedTxn.txnId(), userId);
        
        return authenticatedTxn;
    }

    /**
     * Generate authorization code and store in transaction
     * Returns the plaintext code to be sent to client
     */
    public String generateAndStoreAuthCode(HttpSession session) {
        OidcTransaction txn = getTransaction(session)
                .orElseThrow(() -> new IllegalStateException("No transaction in session"));

        // Generate random auth code (32 bytes = 256 bits)
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String authCode = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

        // Hash the code
        String authCodeHash = sha256Hash(authCode);

        // Calculate expiry
        int validitySeconds = oidcProperties.getSecurity().getAuthCodeValiditySeconds();
        Instant expiresAt = Instant.now().plusSeconds(validitySeconds);

        // Store in auth code store (indexed by plaintext code for lookup during token exchange)
        AuthCodeEntry entry = new AuthCodeEntry(authCodeHash, txn, expiresAt, false);
        authCodeStore.put(authCode, entry);

        // Update transaction with auth code
        OidcTransaction updatedTxn = txn.withAuthCode(authCodeHash, expiresAt);
        updateTransaction(session, updatedTxn);

        logger.info("Generated authorization code: txnId={}, expiresAt={}", 
                txn.txnId(), expiresAt);

        return authCode;
    }

    /**
     * Validate and consume authorization code (one-time use)
     * Returns true if valid, false otherwise
     */
    public boolean validateAndConsumeAuthCode(HttpSession session, String authCode) {
        Optional<OidcTransaction> txnOpt = getTransaction(session);
        if (txnOpt.isEmpty()) {
            logger.warn("No transaction in session for auth code validation");
            return false;
        }

        OidcTransaction txn = txnOpt.get();

        // Check if auth code exists
        if (txn.authCodeHash() == null || txn.authCodeExpiresAt() == null) {
            logger.warn("No auth code in transaction: txnId={}", txn.txnId());
            return false;
        }

        // Check expiry
        if (Instant.now().isAfter(txn.authCodeExpiresAt())) {
            logger.warn("Auth code expired: txnId={}, expiresAt={}", 
                    txn.txnId(), txn.authCodeExpiresAt());
            return false;
        }

        // Verify hash
        String providedHash = sha256Hash(authCode);
        if (!txn.authCodeHash().equals(providedHash)) {
            logger.warn("Auth code hash mismatch: txnId={}", txn.txnId());
            return false;
        }

        // Consume the code (one-time use)
        OidcTransaction consumedTxn = txn.consumeAuthCode();
        updateTransaction(session, consumedTxn);

        logger.info("Auth code validated and consumed: txnId={}", txn.txnId());

        return true;
    }

    /**
     * Validate and consume authorization code WITHOUT requiring session
     * This allows token endpoint to work with just the auth code (standard OIDC)
     * Returns the transaction if valid, empty if invalid/expired/consumed
     */
    public Optional<OidcTransaction> validateAndConsumeAuthCode(String authCode) {
        AuthCodeEntry entry = authCodeStore.get(authCode);
        
        if (entry == null) {
            logger.warn("Auth code not found in store");
            return Optional.empty();
        }

        // Check if already consumed
        if (entry.consumed()) {
            logger.warn("Auth code already consumed: txnId={}", entry.transaction().txnId());
            authCodeStore.remove(authCode);
            return Optional.empty();
        }

        // Check expiry
        if (Instant.now().isAfter(entry.expiresAt())) {
            logger.warn("Auth code expired: txnId={}, expiresAt={}", 
                    entry.transaction().txnId(), entry.expiresAt());
            authCodeStore.remove(authCode);
            return Optional.empty();
        }

        // Mark as consumed (one-time use)
        AuthCodeEntry consumedEntry = new AuthCodeEntry(
                entry.codeHash(), 
                entry.transaction(), 
                entry.expiresAt(), 
                true
        );
        authCodeStore.put(authCode, consumedEntry);

        logger.info("Auth code validated and consumed: txnId={}", entry.transaction().txnId());

        // Remove from store after brief delay to prevent replay attacks
        authCodeStore.remove(authCode);

        return Optional.of(entry.transaction());
    }

    /**
     * Validate PKCE code verifier
     */
    public boolean validatePkce(HttpSession session, String codeVerifier) {
        Optional<OidcTransaction> txnOpt = getTransaction(session);
        if (txnOpt.isEmpty()) {
            logger.warn("No transaction in session for PKCE validation");
            return false;
        }

        OidcTransaction txn = txnOpt.get();

        if (txn.codeChallenge() == null || !"S256".equals(txn.codeChallengeMethod())) {
            logger.warn("Invalid PKCE method in transaction: txnId={}, method={}", 
                    txn.txnId(), txn.codeChallengeMethod());
            return false;
        }

        // Compute SHA-256 of verifier
        String computedChallenge = sha256Base64Url(codeVerifier);
        boolean valid = txn.codeChallenge().equals(computedChallenge);

        if (!valid) {
            logger.warn("PKCE validation failed: txnId={}", txn.txnId());
        }

        return valid;
    }

    /**
     * Validate PKCE code verifier for a transaction (without session)
     */
    public boolean validatePkce(OidcTransaction txn, String codeVerifier) {
        if (txn.codeChallenge() == null || !"S256".equals(txn.codeChallengeMethod())) {
            logger.warn("Invalid PKCE method in transaction: txnId={}, method={}", 
                    txn.txnId(), txn.codeChallengeMethod());
            return false;
        }

        // Compute SHA-256 of verifier
        String computedChallenge = sha256Base64Url(codeVerifier);
        boolean valid = txn.codeChallenge().equals(computedChallenge);

        if (!valid) {
            logger.warn("PKCE validation failed: txnId={}", txn.txnId());
        }

        return valid;
    }

    /**
     * Cleanup expired authorization codes
     * Runs every minute
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredAuthCodes() {
        Instant now = Instant.now();
        int removed = 0;
        
        for (var entry : authCodeStore.entrySet()) {
            if (now.isAfter(entry.getValue().expiresAt())) {
                authCodeStore.remove(entry.getKey());
                removed++;
            }
        }
        
        if (removed > 0) {
            logger.info("Cleaned up {} expired authorization codes", removed);
        }
    }

    /**
     * Cleanup on shutdown
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Clearing {} authorization codes on shutdown", authCodeStore.size());
        authCodeStore.clear();
    }

    /**
     * Get authenticated user ID from session (global session state)
     */
    public Optional<UUID> getAuthenticatedUserId(HttpSession session) {
        String userIdStr = (String) session.getAttribute(ATTR_USER_ID);
        if (userIdStr == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(userIdStr));
        } catch (IllegalArgumentException e) {
            logger.error("Invalid user ID in session: {}", userIdStr);
            return Optional.empty();
        }
    }

    /**
     * Set authenticated user in session (global session state)
     */
    public void setAuthenticatedUserId(HttpSession session, UUID userId) {
        session.setAttribute(ATTR_USER_ID, userId.toString());
        logger.info("Set authenticated user in session: userId={}", userId);
    }

    /**
     * Clear authenticated user from session (for prompt=login)
     */
    public void clearAuthenticatedUser(HttpSession session) {
        session.removeAttribute(ATTR_USER_ID);
        logger.debug("Cleared authenticated user from session");
    }

    /**
     * Check if session has an authenticated user
     */
    public boolean isAuthenticated(HttpSession session) {
        return getAuthenticatedUserId(session).isPresent();
    }

    /**
     * Get user ID as string (for backward compatibility with existing controllers)
     */
    public String getUserId(HttpSession session) {
        return getAuthenticatedUserId(session)
                .map(UUID::toString)
                .orElse(null);
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
