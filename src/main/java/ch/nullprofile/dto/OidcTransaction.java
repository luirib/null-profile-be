package ch.nullprofile.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * OIDC authorization transaction stored in HTTP session
 * Represents the state of an active authorization request
 */
public record OidcTransaction(
        String txnId,
        String rpId,
        String redirectUri,
        String scope,
        String state,
        String nonce,
        String codeChallenge,
        String codeChallengeMethod,
        Instant requestedAt,
        boolean authnRequired,
        UUID authenticatedUserId,
        String authCodeHash,
        Instant authCodeExpiresAt
) implements Serializable {

    /**
     * Create a new transaction for an authorization request (pre-authentication)
     */
    public static OidcTransaction createNew(
            String rpId,
            String redirectUri,
            String scope,
            String state,
            String nonce,
            String codeChallenge,
            String codeChallengeMethod,
            boolean authnRequired) {
        return new OidcTransaction(
                UUID.randomUUID().toString(),
                rpId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                Instant.now(),
                authnRequired,
                null,
                null,
                null
        );
    }

    /**
     * Mark transaction with authenticated user
     */
    public OidcTransaction withAuthenticatedUser(UUID userId) {
        return new OidcTransaction(
                txnId,
                rpId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                requestedAt,
                false, // auth requirement satisfied
                userId,
                authCodeHash,
                authCodeExpiresAt
        );
    }

    /**
     * Mark transaction with issued authorization code
     */
    public OidcTransaction withAuthCode(String authCodeHash, Instant expiresAt) {
        return new OidcTransaction(
                txnId,
                rpId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                requestedAt,
                authnRequired,
                authenticatedUserId,
                authCodeHash,
                expiresAt
        );
    }

    /**
     * Consume the authorization code (one-time use)
     */
    public OidcTransaction consumeAuthCode() {
        return new OidcTransaction(
                txnId,
                rpId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                requestedAt,
                authnRequired,
                authenticatedUserId,
                null, // Remove code after use
                null
        );
    }

    /**
     * Check if authorization code is valid and not expired
     */
    public boolean isAuthCodeValid(String authCode) {
        if (authCodeHash == null || authCodeExpiresAt == null) {
            return false;
        }

        if (Instant.now().isAfter(authCodeExpiresAt)) {
            return false;
        }

        // Verify hash (will be done by service using SHA-256)
        return true;
    }

    /**
     * Check if transaction is authenticated
     */
    public boolean isAuthenticated() {
        return authenticatedUserId != null && !authnRequired;
    }
}
