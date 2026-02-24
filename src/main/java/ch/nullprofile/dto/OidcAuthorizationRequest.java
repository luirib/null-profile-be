package ch.nullprofile.dto;

/**
 * OIDC Authorization Request parameters
 * Represents the validated parameters from GET /authorize
 */
public record OidcAuthorizationRequest(
        String responseType,
        String clientId,
        String redirectUri,
        String scope,
        String state,
        String nonce,
        String codeChallenge,
        String codeChallengeMethod,
        String prompt
) {
    
    /**
     * Check if prompt=login is present
     */
    public boolean requiresLogin() {
        return prompt != null && prompt.contains("login");
    }
}
