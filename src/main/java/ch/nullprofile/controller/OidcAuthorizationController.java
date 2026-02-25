package ch.nullprofile.controller;

import ch.nullprofile.dto.*;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.service.OidcAuthorizationValidationService;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.RelyingPartyService;
import ch.nullprofile.service.UsageMeteringService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * OIDC Authorization Endpoint
 * Implements OAuth 2.0 Authorization Code Flow with PKCE
 */
@Controller
public class OidcAuthorizationController {

    private static final Logger logger = LoggerFactory.getLogger(OidcAuthorizationController.class);

    @Value("${oidc.issuer}")
    private String issuer;

    @Value("${webauthn.origin:http://localhost:3000}")
    private String frontendOrigin;

    private final OidcAuthorizationValidationService validationService;
    private final OidcSessionTransactionService sessionService;
    private final RelyingPartyService relyingPartyService;
    private final UsageMeteringService usageMeteringService;

    public OidcAuthorizationController(
            OidcAuthorizationValidationService validationService,
            OidcSessionTransactionService sessionService,
            RelyingPartyService relyingPartyService,
            UsageMeteringService usageMeteringService) {
        this.validationService = validationService;
        this.sessionService = sessionService;
        this.relyingPartyService = relyingPartyService;
        this.usageMeteringService = usageMeteringService;
    }

    /**
     * OIDC Authorization Endpoint
     * GET /authorize
     * 
     * Validates authorization request and initiates authorization flow
     */
    @GetMapping("/authorize")
    public Object authorize(
            @RequestParam(value = "response_type", required = false) String responseType,
            @RequestParam(value = "client_id", required = false) String clientId,
            @RequestParam(value = "redirect_uri", required = false) String redirectUri,
            @RequestParam(value = "scope", required = false) String scope,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "nonce", required = false) String nonce,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            @RequestParam(value = "prompt", required = false) String prompt,
            HttpSession session) {

        logger.info("Authorization request: client_id={}, redirect_uri={}, prompt={}", 
                clientId, redirectUri != null ? getRedirectUriHost(redirectUri) : null, prompt);

        // Build request object
        OidcAuthorizationRequest request = new OidcAuthorizationRequest(
                responseType,
                clientId,
                redirectUri,
                scope,
                state,
                nonce,
                codeChallenge,
                codeChallengeMethod,
                prompt
        );

        // Validate request
        OidcAuthorizationValidationResult validationResult = validationService.validate(request);

        // Handle validation errors
        if (validationResult instanceof OidcAuthorizationValidationResult.Invalid invalid) {
            logger.warn("Authorization validation failed: error={}, description={}", 
                    invalid.error(), invalid.errorDescription());
            return handleValidationError(invalid);
        }

        // Validation successful
        OidcAuthorizationValidationResult.Valid valid = (OidcAuthorizationValidationResult.Valid) validationResult;
        OidcAuthorizationRequest validatedRequest = valid.request();

        // Check if prompt=login (force re-authentication)
        boolean forceLogin = validatedRequest.requiresLogin();
        if (forceLogin) {
            logger.info("prompt=login: clearing authenticated user from session");
            sessionService.clearAuthenticatedUser(session);
        }

        // Check if user is already authenticated
        Optional<UUID> authenticatedUserId = sessionService.getAuthenticatedUserId(session);
        boolean needsAuthentication = authenticatedUserId.isEmpty();

        // Create transaction in session
        OidcTransaction txn = sessionService.createTransaction(
                session,
                validatedRequest.clientId(),
                validatedRequest.redirectUri(),
                validatedRequest.scope(),
                validatedRequest.state(),
                validatedRequest.nonce(),
                validatedRequest.codeChallenge(),
                validatedRequest.codeChallengeMethod(),
                needsAuthentication || forceLogin
        );

        // If already authenticated and no force login, proceed directly
        if (!needsAuthentication && !forceLogin) {
            logger.info("User already authenticated, proceeding to code issuance: txnId={}, userId={}", 
                    txn.txnId(), authenticatedUserId.get());
            
            // Authenticate the transaction with the existing user
            sessionService.authenticateTransaction(session, authenticatedUserId.get());
            
            // Record successful authentication in usage metrics
            recordAuthenticationMetrics(validatedRequest.clientId(), authenticatedUserId.get());
            
            // Generate authorization code
            String authCode = sessionService.generateAndStoreAuthCode(session);
            
            // Redirect to redirect_uri with code
            return buildSuccessRedirect(validatedRequest.redirectUri(), authCode, validatedRequest.state());
        }

        // Need authentication - redirect to login
        logger.info("Redirecting to login: txnId={}, authnRequired={}", txn.txnId(), needsAuthentication);
        String loginUrl = UriComponentsBuilder.fromUriString(frontendOrigin + "/oidc/login")
                .queryParam("txn", txn.txnId())
                .toUriString();
        return new RedirectView(loginUrl);
    }

    /**
     * Resume authorization flow after authentication
     * GET /authorize/resume
     * 
     * Called after user completes WebAuthn authentication
     */
    @GetMapping("/authorize/resume")
    public Object authorizeResume(
            @RequestParam(value = "txn", required = false) String txnId,
            HttpSession session) {

        logger.info("Authorization resume requested: txnId={}", txnId);

        // Check if user is authenticated
        Optional<UUID> authenticatedUserId = sessionService.getAuthenticatedUserId(session);
        if (authenticatedUserId.isEmpty()) {
            logger.warn("Resume requested but session not authenticated");
            // Redirect back to login
            String loginUrl = frontendOrigin + "/oidc/login";
            if (txnId != null) {
                loginUrl += "?txn=" + txnId;
            }
            return new RedirectView(loginUrl);
        }

        // Get transaction from session
        Optional<OidcTransaction> txnOpt = sessionService.getTransaction(session);
        if (txnOpt.isEmpty()) {
            logger.error("Resume requested but no transaction in session");
            return handleServerError("No authorization request in session", null);
        }

        OidcTransaction txn = txnOpt.get();

        // Verify transaction ID matches (if provided)
        if (txnId != null && !txnId.equals(txn.txnId())) {
            logger.warn("Transaction ID mismatch: expected={}, provided={}", txn.txnId(), txnId);
            return handleServerError("Transaction ID mismatch", txn.redirectUri());
        }

        // Check transaction hasn't expired (session timeout)
        // (Session management handles this automatically, but we can add extra validation)

        // Authenticate the transaction
        OidcTransaction authenticatedTxn = sessionService.authenticateTransaction(session, authenticatedUserId.get());

        logger.info("Transaction authenticated, issuing code: txnId={}, userId={}", 
                authenticatedTxn.txnId(), authenticatedUserId.get());

        // Record successful authentication in usage metrics
        recordAuthenticationMetrics(authenticatedTxn.rpId(), authenticatedUserId.get());

        // Generate authorization code
        String authCode = sessionService.generateAndStoreAuthCode(session);

        // Redirect to redirect_uri with code
        return buildSuccessRedirect(
                authenticatedTxn.redirectUri(), 
                authCode, 
                authenticatedTxn.state());
    }

    /**
     * Handle validation error
     */
    private Object handleValidationError(OidcAuthorizationValidationResult.Invalid invalid) {
        // If we can safely redirect to the redirect_uri, do so with error
        if (invalid.canRedirect()) {
            return buildErrorRedirect(
                    invalid.redirectUri(), 
                    invalid.error(), 
                    invalid.errorDescription(), 
                    invalid.state());
        }

        // Cannot redirect safely - return error as plain response
        // (In production, this should show a user-friendly error page)
        OidcErrorResponse errorResponse = new OidcErrorResponse(
                invalid.error(), 
                invalid.errorDescription());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }

    /**
     * Handle server error
     */
    private Object handleServerError(String description, String redirectUri) {
        logger.error("Server error during authorization: {}", description);
        
        if (redirectUri != null) {
            return buildErrorRedirect(redirectUri, "server_error", 
                    "An internal error occurred", null);
        }

        OidcErrorResponse errorResponse = new OidcErrorResponse(
                "server_error", 
                "An internal error occurred");
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorResponse);
    }

    /**
     * Build successful redirect with authorization code
     */
    private RedirectView buildSuccessRedirect(String redirectUri, String code, String state) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("code", code);
        
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }

        logger.info("Authorization successful, redirecting to: {}", getRedirectUriHost(redirectUri));
        return new RedirectView(builder.toUriString());
    }

    /**
     * Build error redirect
     */
    private RedirectView buildErrorRedirect(
            String redirectUri, 
            String error, 
            String errorDescription, 
            String state) {
        
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam("error", error);
        
        if (errorDescription != null) {
            builder.queryParam("error_description", errorDescription);
        }
        
        if (state != null && !state.isBlank()) {
            builder.queryParam("state", state);
        }

        return new RedirectView(builder.toUriString());
    }

    /**
     * Extract host from redirect URI for logging (don't log full URI with sensitive params)
     */
    private String getRedirectUriHost(String redirectUri) {
        if (redirectUri == null) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromUriString(redirectUri).build().getHost();
        } catch (Exception e) {
            return "invalid-uri";
        }
    }

    /**
     * Record successful authentication in usage metrics
     * Tracks MAU and authentication count per relying party per month
     */
    private void recordAuthenticationMetrics(String rpId, UUID userId) {
        try {
            // Lookup relying party to get UUID
            RelyingParty relyingParty = relyingPartyService.findByRpId(rpId).orElse(null);
            if (relyingParty == null) {
                logger.warn("Cannot record metrics: relying party not found: rpId={}", rpId);
                return;
            }

            // Record the authentication event
            usageMeteringService.recordSuccessfulAuthentication(
                    relyingParty.getId(), 
                    userId, 
                    Instant.now());

            logger.debug("Recorded authentication metrics: rpId={}, userId={}", rpId, userId);

        } catch (Exception e) {
            // Log error but don't fail the authentication flow
            logger.error("Failed to record authentication metrics: rpId={}, userId={}", rpId, userId, e);
        }
    }
}
