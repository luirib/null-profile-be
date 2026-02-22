package ch.nullprofile.controller;

import ch.nullprofile.config.WebAuthnProperties;
import ch.nullprofile.dto.webauthn.*;
import ch.nullprofile.entity.User;
import ch.nullprofile.service.ChallengeService;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.WebAuthnVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webauthn4j.util.Base64UrlUtil;
import com.webauthn4j.validator.exception.ValidationException;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/webauthn")
public class WebAuthnController {

    private static final Logger logger = LoggerFactory.getLogger(WebAuthnController.class);

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
            HttpSession session) {
        
        logger.info("Registration options requested, txn={}, displayName={}", request.txn(), request.displayName());

        // Generate challenge
        String challenge = challengeService.generateAndStoreRegistrationChallenge(session, request.txn());
        
        // Generate user handle
        String userHandle = challengeService.generateAndStoreUserHandle(session);

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
                )
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Verify WebAuthn registration
     */
    @PostMapping(value = "/registration/verify",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnResponse> registrationVerify(
            @RequestBody RegistrationVerifyRequest request,
            HttpSession session) {
        
        logger.info("Registration verify requested, txn={}, credentialId={}", request.txn(), request.id());

        try {
            // Extract challenge from clientDataJSON
            JsonNode clientData = objectMapper.readTree(Base64UrlUtil.decode(request.response().clientDataJSON()));
            String challenge = clientData.get("challenge").asText();

            // Validate challenge
            if (!challengeService.validateAndConsumeRegistrationChallenge(session, challenge)) {
                logger.warn("Invalid or expired registration challenge");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(WebAuthnResponse.error("challenge_expired", "Challenge is invalid or expired"));
            }

            // Verify and create user
            User user = verificationService.verifyRegistrationAndCreateUser(
                    challenge,
                    request.response().clientDataJSON(),
                    request.response().attestationObject(),
                    properties.getOrigin()
            );

            // Set authenticated user in session
            sessionService.setAuthenticatedUser(session, user.getId());

            // Cleanup registration session data
            challengeService.cleanupRegistrationSession(session);

            logger.info("Registration successful for userId={}", user.getId());

            // Check if there's an OIDC transaction to continue
            if (sessionService.isAuthenticated(session) && sessionService.getRpId(session) != null) {
                return ResponseEntity.ok(WebAuthnResponse.successWithRedirect("/authorize/resume"));
            }

            return ResponseEntity.ok(WebAuthnResponse.success());

        } catch (ValidationException e) {
            logger.error("Registration verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("verification_failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("registration_failed", e.getMessage()));
        } catch (Exception e) {
            logger.error("Registration verification error", e);
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
            sessionService.setAuthenticatedUser(session, user.getId());

            // Cleanup authentication session data
            challengeService.cleanupAuthenticationSession(session);

            logger.info("Authentication successful for userId={}", user.getId());

            // Check if there's an OIDC transaction to continue
            if (sessionService.isAuthenticated(session) && sessionService.getRpId(session) != null) {
                return ResponseEntity.ok(WebAuthnResponse.successWithRedirect("/authorize/resume"));
            }

            return ResponseEntity.ok(WebAuthnResponse.success());

        } catch (ValidationException e) {
            logger.error("Authentication verification failed", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("verification_failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Authentication failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("authentication_failed", e.getMessage()));
        } catch (Exception e) {
            logger.error("Authentication verification error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebAuthnResponse.error("internal_error", "An unexpected error occurred"));
        }
    }
}
