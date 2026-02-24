package ch.nullprofile.service;

import ch.nullprofile.config.OidcProperties;
import ch.nullprofile.dto.OidcAuthorizationRequest;
import ch.nullprofile.dto.OidcAuthorizationValidationResult;
import ch.nullprofile.entity.RelyingParty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for validating OIDC authorization requests
 */
@Service
public class OidcAuthorizationValidationService {

    private static final Logger logger = LoggerFactory.getLogger(OidcAuthorizationValidationService.class);

    private static final String SUPPORTED_RESPONSE_TYPE = "code";
    private static final String SUPPORTED_SCOPE = "openid";
    private static final String SUPPORTED_CODE_CHALLENGE_METHOD = "S256";
    private static final List<String> UNSUPPORTED_PROMPTS = Arrays.asList("none", "consent", "select_account");
    private static final Pattern BASE64URL_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final OidcProperties oidcProperties;
    private final RelyingPartyService relyingPartyService;

    public OidcAuthorizationValidationService(
            OidcProperties oidcProperties,
            RelyingPartyService relyingPartyService) {
        this.oidcProperties = oidcProperties;
        this.relyingPartyService = relyingPartyService;
    }

    /**
     * Validate OIDC authorization request according to spec
     */
    public OidcAuthorizationValidationResult validate(OidcAuthorizationRequest request) {
        
        // Validate response_type (must be "code")
        if (request.responseType() == null || !SUPPORTED_RESPONSE_TYPE.equals(request.responseType())) {
            return error("unsupported_response_type", 
                    "Only response_type=code is supported", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate scope (must be exactly "openid")
        if (request.scope() == null || !SUPPORTED_SCOPE.equals(request.scope())) {
            return error("invalid_scope", 
                    "Only scope=openid is supported", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate nonce is present (REQUIRED for implicit flow, but we also require it for code flow)
        if (request.nonce() == null || request.nonce().isBlank()) {
            return error("invalid_request", 
                    "nonce parameter is required", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate nonce length
        if (request.nonce().length() > oidcProperties.getSecurity().getMaxNonceLength()) {
            return error("invalid_request", 
                    "nonce parameter is too long", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate state length (if provided)
        if (request.state() != null && request.state().length() > oidcProperties.getSecurity().getMaxStateLength()) {
            return error("invalid_request", 
                    "state parameter is too long", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate code_challenge is present
        if (request.codeChallenge() == null || request.codeChallenge().isBlank()) {
            return error("invalid_request", 
                    "code_challenge is required", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate code_challenge length
        int challengeLength = request.codeChallenge().length();
        if (challengeLength < oidcProperties.getSecurity().getMinCodeChallengeLength() || 
            challengeLength > oidcProperties.getSecurity().getMaxCodeChallengeLength()) {
            return error("invalid_request", 
                    "code_challenge length is invalid", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate code_challenge is base64url
        if (!BASE64URL_PATTERN.matcher(request.codeChallenge()).matches()) {
            return error("invalid_request", 
                    "code_challenge must be base64url encoded", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate code_challenge_method (must be S256)
        if (!SUPPORTED_CODE_CHALLENGE_METHOD.equals(request.codeChallengeMethod())) {
            return error("invalid_request", 
                    "Only code_challenge_method=S256 is supported", 
                    request.redirectUri(), 
                    request.state());
        }

        // Validate client_id exists
        if (request.clientId() == null || request.clientId().isBlank()) {
            return error("invalid_request", 
                    "client_id is required", 
                    null, // Can't redirect without knowing the client
                    request.state());
        }

        RelyingParty relyingParty = relyingPartyService.findByRpId(request.clientId())
                .orElse(null);

        if (relyingParty == null) {
            logger.warn("Authorization request for unknown client_id: {}", request.clientId());
            return error("unauthorized_client", 
                    "Unknown client_id", 
                    null, // Can't redirect without valid client
                    request.state());
        }

        // Validate redirect_uri is present
        if (request.redirectUri() == null || request.redirectUri().isBlank()) {
            return error("invalid_request", 
                    "redirect_uri is required", 
                    null, // Can't redirect without redirect_uri
                    request.state());
        }

        // Validate redirect_uri is registered for this client (exact match)
        if (!relyingPartyService.isRedirectUriValid(relyingParty, request.redirectUri())) {
            logger.warn("Invalid redirect_uri for client {}: {}", request.clientId(), request.redirectUri());
            return error("invalid_request", 
                    "redirect_uri is not registered for this client", 
                    null, // Don't redirect to unregistered URI
                    request.state());
        }

        // Validate redirect_uri scheme
        OidcAuthorizationValidationResult schemeValidation = validateRedirectUriScheme(request.redirectUri(), request.state());
        if (schemeValidation instanceof OidcAuthorizationValidationResult.Invalid) {
            return schemeValidation;
        }

        // Validate prompt parameter
        if (request.prompt() != null && !request.prompt().isBlank()) {
            String[] prompts = request.prompt().split("\\s+");
            for (String prompt : prompts) {
                if (UNSUPPORTED_PROMPTS.contains(prompt)) {
                    return error("invalid_request", 
                            "Unsupported prompt value: " + prompt, 
                            request.redirectUri(), 
                            request.state());
                }
                if (!"login".equals(prompt)) {
                    logger.warn("Unknown prompt value (ignoring): {}", prompt);
                }
            }
        }

        // All validations passed
        return new OidcAuthorizationValidationResult.Valid(request);
    }

    /**
     * Validate redirect URI scheme according to security policy
     */
    private OidcAuthorizationValidationResult validateRedirectUriScheme(String redirectUri, String state) {
        try {
            URI uri = new URI(redirectUri);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null) {
                return error("invalid_request", 
                        "redirect_uri must have a scheme", 
                        null, 
                        state);
            }

            // Allow https always
            if ("https".equalsIgnoreCase(scheme)) {
                return null; // Valid
            }

            // Allow http for localhost in dev mode
            if ("http".equalsIgnoreCase(scheme)) {
                if (oidcProperties.getSecurity().isAllowHttpRedirectUrisForLocalhost() && 
                    isLocalhost(host)) {
                    return null; // Valid
                } else {
                    logger.warn("Rejecting http redirect_uri for non-localhost: {}", redirectUri);
                    return error("invalid_request", 
                            "redirect_uri must use https (except localhost in dev mode)", 
                            null, 
                            state);
                }
            }

            // Reject all other schemes
            return error("invalid_request", 
                    "redirect_uri scheme not supported", 
                    null, 
                    state);

        } catch (URISyntaxException e) {
            return error("invalid_request", 
                    "redirect_uri is malformed", 
                    null, 
                    state);
        }
    }

    /**
     * Check if host is localhost
     */
    private boolean isLocalhost(String host) {
        if (host == null) {
            return false;
        }
        return "localhost".equalsIgnoreCase(host) || 
               "127.0.0.1".equals(host) || 
               "::1".equals(host);
    }

    /**
     * Create an error result
     */
    private OidcAuthorizationValidationResult.Invalid error(
            String error, 
            String errorDescription, 
            String redirectUri, 
            String state) {
        return new OidcAuthorizationValidationResult.Invalid(
                error, 
                errorDescription, 
                redirectUri, 
                state);
    }
}
