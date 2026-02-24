package ch.nullprofile.dto;

/**
 * Result of OIDC authorization request validation
 */
public sealed interface OidcAuthorizationValidationResult permits
        OidcAuthorizationValidationResult.Valid,
        OidcAuthorizationValidationResult.Invalid {

    /**
     * Validation succeeded
     */
    record Valid(OidcAuthorizationRequest request) implements OidcAuthorizationValidationResult {
    }

    /**
     * Validation failed with specific error
     */
    record Invalid(
            String error,
            String errorDescription,
            String redirectUri,
            String state
    ) implements OidcAuthorizationValidationResult {
        /**
         * Check if we can safely redirect to the redirect_uri with the error
         */
        public boolean canRedirect() {
            return redirectUri != null && !redirectUri.isBlank();
        }
    }
}
