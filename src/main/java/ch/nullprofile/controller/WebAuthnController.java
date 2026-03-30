package ch.nullprofile.controller;

import ch.nullprofile.config.WebAuthnProperties;
import ch.nullprofile.dto.webauthn.*;
import ch.nullprofile.entity.User;
import ch.nullprofile.filter.TraceIdFilter;
import ch.nullprofile.service.ChallengeService;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.WebAuthnVerificationService;
import ch.nullprofile.util.SensitiveDataMasker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.util.Base64UrlUtil;
import com.webauthn4j.validator.exception.ValidationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/webauthn")
public class WebAuthnController {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnController.class);

    @Value("${oidc.issuer}")
    private String issuer;

    private final WebAuthnProperties properties;
    private final ChallengeService challengeService;
    private final WebAuthnVerificationService verificationService;
    private final OidcSessionTransactionService sessionService;
    private final ObjectMapper objectMapper;

    public WebAuthnController(
            WebAuthnProperties properties,
            ChallengeService challengeService,
            WebAuthnVerificationService verificationService,
            OidcSessionTransactionService sessionService,
            ObjectMapper objectMapper) {
        this.properties = properties;
        this.challengeService = challengeService;
        this.verificationService = verificationService;
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Generate WebAuthn registration options
     */
    @PostMapping(value = "/registration/options", 
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationOptionsResponse> registrationOptions(
            @RequestBody RegistrationOptionsRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        String sessionIdMasked = SensitiveDataMasker.maskSessionId(session.getId());
        
        // === COMPREHENSIVE DIAGNOSTICS FOR DEBUGGING RENDER DEPLOYMENT ===
        logger.info("[REG-OPTIONS] ========== START ==========");
        logger.info("[REG-OPTIONS] Trace ID: {}", traceId);
        logger.info("[REG-OPTIONS] Request: method={}, uri={}, contentType={}", 
            httpRequest.getMethod(), httpRequest.getRequestURI(), httpRequest.getContentType());
        
        // Log headers (safe ones)
        logger.info("[REG-OPTIONS] Headers:");
        logger.info("[REG-OPTIONS]   Origin: {}", httpRequest.getHeader("Origin"));
        logger.info("[REG-OPTIONS]   Referer: {}", httpRequest.getHeader("Referer"));
        logger.info("[REG-OPTIONS]   User-Agent: {}", 
            SensitiveDataMasker.truncate(httpRequest.getHeader("User-Agent"), 50));
        logger.info("[REG-OPTIONS]   Content-Type: {}", httpRequest.getHeader("Content-Type"));
        logger.info("[REG-OPTIONS]   Cookie header present: {}", 
            httpRequest.getHeader("Cookie") != null ? "YES" : "NO");
        
        // Log cookies
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null && cookies.length > 0) {
            logger.info("[REG-OPTIONS] Cookies received: {}", 
                Arrays.stream(cookies)
                    .map(Cookie::getName)
                    .collect(Collectors.joining(", ")));
        } else {
            logger.warn("[REG-OPTIONS] NO COOKIES received - this is normal for first request");
        }
        
        // Session diagnostics
        logger.info("[REG-OPTIONS] Session:");
        logger.info("[REG-OPTIONS]   ID (masked): {}", sessionIdMasked);
        logger.info("[REG-OPTIONS]   Is new: {}", session.isNew());
        logger.info("[REG-OPTIONS]   Creation time: {}", session.getCreationTime());
        logger.info("[REG-OPTIONS]   Max inactive interval: {} seconds", session.getMaxInactiveInterval());
        
        // Log request payload
        logger.info("[REG-OPTIONS] Request payload: txn={}, displayName={}", 
            request.txn(), request.displayName());

        // Generate challenge
        String challenge = challengeService.generateAndStoreRegistrationChallenge(session, request.txn());
        
        logger.info("[REG-OPTIONS] Challenge generated: length={}, stored in session={}", 
            challenge.length(), sessionIdMasked);
        
        // Generate user handle
        String userHandle = challengeService.generateAndStoreUserHandle(session);
        
        logger.info("[REG-OPTIONS] User handle generated and stored in session");
        logger.info("[REG-OPTIONS] Response prepared: rpId={}, origin={}, timeout={}ms", 
            properties.getRp().getId(), properties.getOrigin(), 
            (long) properties.getChallenge().getTimeout() * 1000);
        logger.info("[REG-OPTIONS] ========== END ==========");

        
        // Use provided display name or default
        String displayName = request.displayName() != null && !request.displayName().isBlank() 
                ? request.displayName() 
                : "User";
        
        // Generate random technical name
        String technicalName = "passkey_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Build response
        RegistrationOptionsResponse response = new RegistrationOptionsResponse(
                challenge,
                new RegistrationOptionsResponse.RelyingPartyInfo(
                        properties.getRp().getId(),
                        properties.getRp().getName()
                ),
                new RegistrationOptionsResponse.UserInfo(
                        userHandle,
                        technicalName,  // Random technical name
                        displayName     // User-provided display name
                ),
                List.of(
                        new RegistrationOptionsResponse.PubKeyCredParam("public-key", -7),  // ES256
                        new RegistrationOptionsResponse.PubKeyCredParam("public-key", -257) // RS256
                ),
                (long) properties.getChallenge().getTimeout() * 1000, // Convert to milliseconds
                "none",
                new RegistrationOptionsResponse.AuthenticatorSelection(
                        "required",
                        "preferred"
                ),
                null // No credentials to exclude for initial registration
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Verify WebAuthn registration
     */
    @PostMapping("/registration/verify")
    public ResponseEntity<?> verifyRegistration(@RequestBody RegistrationVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpSession session) {
        
        String traceId = TraceIdFilter.getCurrentTraceId();
        String sessionIdMasked = SensitiveDataMasker.maskSessionId(session.getId());
        
        // === COMPREHENSIVE DIAGNOSTICS FOR DEBUGGING RENDER DEPLOYMENT ===
        logger.info("[REG-VERIFY] ========== START ==========");
        logger.info("[REG-VERIFY] Trace ID: {}", traceId);
        logger.info("[REG-VERIFY] Request: method={}, uri={}, contentType={}", 
            httpRequest.getMethod(), httpRequest.getRequestURI(), httpRequest.getContentType());
        
        // Log headers (safe ones)
        logger.info("[REG-VERIFY] Headers:");
        logger.info("[REG-VERIFY]   Origin: {}", httpRequest.getHeader("Origin"));
        logger.info("[REG-VERIFY]   Referer: {}", httpRequest.getHeader("Referer"));
        logger.info("[REG-VERIFY]   Cookie header present: {}", 
            httpRequest.getHeader("Cookie") != null ? "YES" : "NO");
        
        // Log cookies - CRITICAL FOR DEBUGGING SESSION ISSUES
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null && cookies.length > 0) {
            logger.info("[REG-VERIFY] Cookies received: {}", 
                Arrays.stream(cookies)
                    .map(c -> c.getName() + "=" + 
                        (c.getName().equals("JSESSIONID") ? 
                            SensitiveDataMasker.maskSessionId(c.getValue()) : "***"))
                    .collect(Collectors.joining(", ")));
            
            // Check specifically for JSESSIONID
            boolean hasJsessionid = Arrays.stream(cookies)
                .anyMatch(c -> c.getName().equals("JSESSIONID"));
            if (hasJsessionid) {
                logger.info("[REG-VERIFY] ✓ JSESSIONID cookie IS PRESENT");
            } else {
                logger.error("[REG-VERIFY] ✗ JSESSIONID cookie MISSING - session will not be maintained!");
                logger.error("[REG-VERIFY] This typically means:");
                logger.error("[REG-VERIFY]   1. Browser rejected cookie due to SameSite/Secure mismatch");
                logger.error("[REG-VERIFY]   2. CORS not allowing credentials");
                logger.error("[REG-VERIFY]   3. Frontend not sending 'credentials: include'");
            }
        } else {
            logger.error("[REG-VERIFY] ✗ NO COOKIES received - session CANNOT be maintained!");
            logger.error("[REG-VERIFY] Challenge validation WILL FAIL");
        }
        
        // Session diagnostics - CRITICAL
        logger.info("[REG-VERIFY] Session:");
        logger.info("[REG-VERIFY]   ID (masked): {}", sessionIdMasked);
        logger.info("[REG-VERIFY]   Is new: {} (MUST be false for session continuity)", session.isNew());
        logger.info("[REG-VERIFY]   Creation time: {}", session.getCreationTime());
        logger.info("[REG-VERIFY]   Age: {}ms", System.currentTimeMillis() - session.getCreationTime());
        
        if (session.isNew()) {
            logger.error("[REG-VERIFY] ✗ Session is NEW - this means session from /options was NOT maintained");
            logger.error("[REG-VERIFY] This is THE ROOT CAUSE of 'Invalid or expired challenge' errors");
        }
        
        // Check session attributes
        boolean hasRegistrationChallenge = session.getAttribute("webauthn.regChallenge") != null;
        boolean hasExpiresAt = session.getAttribute("webauthn.regChallengeExpiresAt") != null;
        logger.info("[REG-VERIFY] Session attributes:");
        logger.info("[REG-VERIFY]   Has challenge: {} (MUST be true)", hasRegistrationChallenge);
        logger.info("[REG-VERIFY]   Has expiresAt: {} (MUST be true)", hasExpiresAt);
        logger.info("[REG-VERIFY]   Txn: {}", request.txn());
        
        if (!hasRegistrationChallenge || !hasExpiresAt) {
            logger.error("[REG-VERIFY] ✗ Challenge or timestamp MISSING from session");
            logger.error("[REG-VERIFY] Expected attributes not found - session was not maintained");
        }
        
        // Log request payload
        logger.info("[REG-VERIFY] Request payload: txn={}, id={}, name={}", 
            request.txn(), request.id(), request.name());

        try {
            // Extract challenge from clientDataJSON
            JsonNode clientData = objectMapper.readTree(Base64UrlUtil.decode(request.response().clientDataJSON()));
            String challenge = clientData.get("challenge").asText();
            String clientOrigin = clientData.get("origin").asText();
            String clientType = clientData.get("type").asText();
            
            logger.info("[REG-VERIFY] Client data extracted:");
            logger.info("[REG-VERIFY]   Challenge: {}", SensitiveDataMasker.maskChallenge(challenge));
            logger.info("[REG-VERIFY]   Origin: {}", clientOrigin);
            logger.info("[REG-VERIFY]   Type: {}", clientType);
            logger.info("[REG-VERIFY]   Expected origin: {}", properties.getOrigin());
            
            // Origin validation
            if (!clientOrigin.equals(properties.getOrigin())) {
                logger.error("[REG-VERIFY] ✗ ORIGIN MISMATCH!");
                logger.error("[REG-VERIFY]   Client sent: {}", clientOrigin);
                logger.error("[REG-VERIFY]   Server expects: {}", properties.getOrigin());
                logger.error("[REG-VERIFY]   Check WEBAUTHN_ORIGIN environment variable");
            }

            // Validate challenge
            logger.info("[REG-VERIFY] Validating challenge against stored value...");
            if (!challengeService.validateAndConsumeRegistrationChallenge(session, challenge)) {
                logger.error("[REG-VERIFY] ✗ CHALLENGE VALIDATION FAILED");
                logger.error("[REG-VERIFY] Session: id={}, isNew={}, hasChallenge={}", 
                    sessionIdMasked, session.isNew(), hasRegistrationChallenge);
                logger.error("[REG-VERIFY] Trace ID for debugging: {}", traceId);
                logger.error("[REG-VERIFY] ========== END (FAILED) ==========");
                
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(WebAuthnResponse.error("challenge_expired", 
                            "Challenge is invalid or expired. Session was not maintained between requests. " +
                            "Trace ID: " + traceId + ". " +
                            "Check: 1) Browser cookies enabled, 2) CORS credentials allowed, 3) Session cookie SameSite/Secure settings."));
            }
            
            logger.info("[REG-VERIFY] ✓ Challenge validated successfully");

            // Verify and create user
            logger.info("[REG-VERIFY] Verifying attestation with WebAuthn4J...");
            User user = verificationService.verifyRegistrationAndCreateUser(
                    challenge,
                    request.name(),
                    request.response().clientDataJSON(),
                    request.response().attestationObject(),
                    properties.getOrigin()
            );

            logger.info("[REG-VERIFY] ✓ Attestation verified, user created: userId={}", user.getId());

            // Set authenticated user in session
            sessionService.setAuthenticatedUserId(session, user.getId());
            logger.info("[REG-VERIFY] ✓ User authenticated in session");

            // Cleanup registration session data
            challengeService.cleanupRegistrationSession(session);

            logger.info("[REG-VERIFY] ✓ Registration successful for userId={}", user.getId());

            // Check if there's an OIDC transaction to continue
            // Only redirect if this registration was initiated for an OIDC flow (txn provided)
            var txn = sessionService.getTransaction(session);
            logger.info("[REG-VERIFY] Checking OIDC transaction: present={}, authenticated={}, requestTxn={}", 
                    txn.isPresent(), sessionService.isAuthenticated(session), request.txn());
            
            // Only redirect to OIDC flow if the registration was initiated WITH a txn parameter
            // This prevents redirecting to stale OIDC transactions from other tabs
            if (sessionService.isAuthenticated(session) && txn.isPresent() && request.txn() != null && !request.txn().isEmpty()) {
                logger.info("[REG-VERIFY] OIDC transaction found, redirecting to /authorize/resume: txnId={}", 
                        txn.get().txnId());
                String resumeUrl = issuer + "/authorize/resume";
                logger.info("[REG-VERIFY] ✓ Returning redirect URL: {}", resumeUrl);
                logger.info("[REG-VERIFY] ========== END (SUCCESS WITH REDIRECT) ==========");
                return ResponseEntity.ok(WebAuthnResponse.successWithRedirect(resumeUrl));
            }
            
            logger.info("[REG-VERIFY] ✓ Registration completed without OIDC redirect (direct login)");
            logger.info("[REG-VERIFY] ========== END (SUCCESS) ==========");

            return ResponseEntity.ok(WebAuthnResponse.success());

        } catch (ValidationException e) {
            logger.error("[REG-VERIFY] ✗ Attestation verification FAILED: {}", e.getMessage());
            logger.error("[REG-VERIFY] Exception type: {}", e.getClass().getSimpleName());
            logger.error("[REG-VERIFY] Trace ID: {}", traceId);
            logger.error("[REG-VERIFY] Full exception:", e);
            
            // Clean up session state on failure to allow retry
            challengeService.cleanupRegistrationSession(session);
            
            logger.info("[REG-VERIFY] ========== END (VERIFICATION FAILED) ==========");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("verification_failed", 
                        "WebAuthn verification failed: " + e.getMessage() + ". Trace ID: " + traceId));
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            // Clean up session state on failure to allow retry
            challengeService.cleanupRegistrationSession(session);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("registration_failed", e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration verification error", e);
            // Clean up session state on failure to allow retry
            challengeService.cleanupRegistrationSession(session);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebAuthnResponse.error("internal_error", "An unexpected error occurred"));
        }
    }

    /**
     * Generate WebAuthn authentication options
     */
    @PostMapping(value = "/authentication/options",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AuthenticationOptionsResponse> authenticationOptions(
            @RequestBody AuthenticationOptionsRequest request,
            HttpSession session) {
        
        logger.info("Authentication options requested, txn={}", request.txn());

        // Generate challenge
        String challenge = challengeService.generateAndStoreAuthenticationChallenge(session, request.txn());

        // Build response (no allowCredentials for discoverable credentials)
        AuthenticationOptionsResponse response = new AuthenticationOptionsResponse(
                challenge,
                (long) properties.getChallenge().getTimeout() * 1000, // Convert to milliseconds
                properties.getRp().getId(),
                "preferred"
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Verify WebAuthn authentication
     */
    @PostMapping(value = "/authentication/verify",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnResponse> authenticationVerify(
            @RequestBody AuthenticationVerifyRequest request,
            HttpSession session) {
        
        logger.info("Authentication verify requested, txn={}, credentialId={}", request.txn(), request.id());

        try {
            // Extract challenge from clientDataJSON
            JsonNode clientData = objectMapper.readTree(Base64UrlUtil.decode(request.response().clientDataJSON()));
            String challenge = clientData.get("challenge").asText();

            // Validate challenge
            if (!challengeService.validateAndConsumeAuthenticationChallenge(session, challenge)) {
                logger.warn("Invalid or expired authentication challenge");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(WebAuthnResponse.error("challenge_expired", "Challenge is invalid or expired"));
            }

            // Verify authentication
            User user = verificationService.verifyAuthentication(
                    challenge,
                    request.id(),
                    request.response().clientDataJSON(),
                    request.response().authenticatorData(),
                    request.response().signature(),
                    properties.getOrigin()
            );

            // Set authenticated user in session
            sessionService.setAuthenticatedUserId(session, user.getId());

            // Cleanup authentication session data
            challengeService.cleanupAuthenticationSession(session);

            logger.info("Authentication successful for userId={}", user.getId());

            // Check if there's an OIDC transaction to continue
            // Only redirect if this authentication was initiated for an OIDC flow (txn provided)
            var txn = sessionService.getTransaction(session);
            logger.info("Checking for OIDC transaction: present={}, authenticated={}, requestTxn={}", 
                    txn.isPresent(), sessionService.isAuthenticated(session), request.txn());
            
            // Only redirect to OIDC flow if the authentication was initiated WITH a txn parameter
            // This prevents redirecting to stale OIDC transactions from other tabs
            if (sessionService.isAuthenticated(session) && txn.isPresent() && request.txn() != null && !request.txn().isEmpty()) {
                logger.info("OIDC transaction found and authentication initiated with txn, redirecting to /authorize/resume: txnId={}", 
                        txn.get().txnId());
                String resumeUrl = issuer + "/authorize/resume";
                logger.info("Returning redirect URL: {}", resumeUrl);
                return ResponseEntity.ok(WebAuthnResponse.successWithRedirect(resumeUrl));
            }
            
            logger.info("Authentication completed without OIDC redirect (direct login)");

            return ResponseEntity.ok(WebAuthnResponse.success());

        } catch (ValidationException e) {
            logger.error("Authentication verification failed", e);
            // Clean up session state on failure to allow retry
            challengeService.cleanupAuthenticationSession(session);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("verification_failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication failed: {}", e.getMessage());
            // Clean up session state on failure to allow retry
            challengeService.cleanupAuthenticationSession(session);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("authentication_failed", e.getMessage()));
        } catch (Exception e) {
            logger.error("Authentication verification error", e);
            // Clean up session state on failure to allow retry
            challengeService.cleanupAuthenticationSession(session);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebAuthnResponse.error("internal_error", "An unexpected error occurred"));
        }
    }
}
