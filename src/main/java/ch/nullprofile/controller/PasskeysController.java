package ch.nullprofile.controller;

import ch.nullprofile.config.WebAuthnProperties;
import ch.nullprofile.dto.PasskeySummary;
import ch.nullprofile.dto.RenamePasskeyRequest;
import ch.nullprofile.dto.webauthn.RegistrationOptionsRequest;
import ch.nullprofile.dto.webauthn.RegistrationOptionsResponse;
import ch.nullprofile.dto.webauthn.RegistrationVerifyRequest;
import ch.nullprofile.dto.webauthn.WebAuthnResponse;
import ch.nullprofile.entity.WebAuthnCredential;
import ch.nullprofile.repository.WebAuthnCredentialRepository;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/passkeys")
public class PasskeysController {

    private static final Logger logger = LoggerFactory.getLogger(PasskeysController.class);

    private final WebAuthnCredentialRepository credentialRepository;
    private final OidcSessionTransactionService sessionService;
    private final WebAuthnProperties properties;
    private final ChallengeService challengeService;
    private final WebAuthnVerificationService verificationService;
    private final ObjectMapper objectMapper;

    public PasskeysController(
            WebAuthnCredentialRepository credentialRepository,
            OidcSessionTransactionService sessionService,
            WebAuthnProperties properties,
            ChallengeService challengeService,
            WebAuthnVerificationService verificationService,
            ObjectMapper objectMapper) {
        this.credentialRepository = credentialRepository;
        this.sessionService = sessionService;
        this.properties = properties;
        this.challengeService = challengeService;
        this.verificationService = verificationService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get all passkeys for the authenticated user
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PasskeySummary>> getAllPasskeys(HttpSession session) {
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to list passkeys");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        List<PasskeySummary> passkeys = credentialRepository.findByUserId(userId).stream()
                .map(credential -> new PasskeySummary(
                        credential.getId(),
                        credential.getName() != null ? credential.getName() : "Unnamed Passkey",
                        credential.getCreatedAt().toString()
                ))
                .collect(Collectors.toList());

        logger.info("Retrieved {} passkeys for userId={}", passkeys.size(), userId);
        return ResponseEntity.ok(passkeys);
    }

    /**
     * Generate passkey registration options for adding a new passkey to current user
     */
    @PostMapping(value = "/options", 
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RegistrationOptionsResponse> passkeyRegistrationOptions(
            @RequestBody RegistrationOptionsRequest request,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to get passkey registration options");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        logger.info("Passkey registration options requested for userId={}, displayName={}", userId, request.displayName());

        // Generate challenge
        String challenge = challengeService.generateAndStoreRegistrationChallenge(session, null);
        
        // Generate user handle (use existing user ID)
        String userHandle = userId.toString();

        // Use provided display name or default
        String displayName = request.displayName() != null && !request.displayName().isBlank() 
                ? request.displayName() 
                : "Passkey";
        
        // Generate random technical name
        String technicalName = "passkey_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        // Get existing credentials to exclude
        List<RegistrationOptionsResponse.ExcludeCredential> excludeCredentials = 
                credentialRepository.findByUserId(userId).stream()
                        .map(cred -> new RegistrationOptionsResponse.ExcludeCredential(
                                "public-key",
                                cred.getCredentialId()
                        ))
                        .collect(Collectors.toList());

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
                excludeCredentials
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Verify passkey registration and add it to current user
     */
    @PostMapping(value = "/verify",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<WebAuthnResponse> passkeyRegistrationVerify(
            @RequestBody RegistrationVerifyRequest request,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to verify passkey registration");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(WebAuthnResponse.error("unauthorized", "User not authenticated"));
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        logger.info("Passkey registration verify requested for userId={}, credentialId={}", userId, request.id());

        try {
            // Extract challenge from clientDataJSON
            JsonNode clientData = objectMapper.readTree(Base64UrlUtil.decode(request.response().clientDataJSON()));
            String challenge = clientData.get("challenge").asText();

            // Validate challenge
            if (!challengeService.validateAndConsumeRegistrationChallenge(session, challenge)) {
                logger.warn("Invalid or expired registration challenge for userId={}", userId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(WebAuthnResponse.error("challenge_expired", "Challenge is invalid or expired"));
            }

            // Verify and add credential
            WebAuthnCredential credential = verificationService.verifyRegistrationAndAddCredential(
                    userId,
                    challenge,
                    request.name(),
                    request.response().clientDataJSON(),
                    request.response().attestationObject(),
                    properties.getOrigin()
            );

            // Cleanup registration session data
            challengeService.cleanupRegistrationSession(session);

            logger.info("Passkey registration successful for userId={}, credentialId={}", userId, credential.getCredentialId());

            return ResponseEntity.ok(WebAuthnResponse.success());

        } catch (ValidationException e) {
            logger.error("Passkey registration verification failed for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("verification_failed", e.getMessage()));
        } catch (IllegalArgumentException e) {
            logger.warn("Passkey registration failed for userId={}: {}", userId, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(WebAuthnResponse.error("registration_failed", e.getMessage()));
        } catch (Exception e) {
            logger.error("Passkey registration error for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(WebAuthnResponse.error("internal_error", "An unexpected error occurred"));
        }
    }

    /**
     * Rename a passkey
     */
    @PutMapping(value = "/{id}",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> renamePasskey(
            @PathVariable UUID id,
            @RequestBody RenamePasskeyRequest request,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to rename passkey id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (request.name() == null || request.name().trim().isEmpty()) {
            logger.warn("Invalid passkey name provided for id={}", id);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));

        return credentialRepository.findById(id)
                .filter(credential -> credential.getUserId().equals(userId))
                .map(credential -> {
                    credential.setName(request.name().trim());
                    credentialRepository.save(credential);
                    logger.info("Renamed passkey id={} to '{}' for userId={}", id, request.name(), userId);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> {
                    logger.warn("Cannot rename passkey id={} - not found or unauthorized for userId={}", id, userId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    /**
     * Delete a passkey
     */
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deletePasskey(
            @PathVariable UUID id,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to delete passkey id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));

        // Check if this is the last passkey
        long passkeyCount = credentialRepository.findByUserId(userId).size();
        if (passkeyCount <= 1) {
            logger.warn("Cannot delete last passkey id={} for userId={}", id, userId);
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header("X-Error-Message", "Cannot delete your last passkey. You need at least one passkey to access your account.")
                    .build();
        }

        return credentialRepository.findById(id)
                .filter(credential -> credential.getUserId().equals(userId))
                .map(credential -> {
                    credentialRepository.delete(credential);
                    logger.info("Deleted passkey id={} for userId={}", id, userId);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElseGet(() -> {
                    logger.warn("Cannot delete passkey id={} - not found or unauthorized for userId={}", id, userId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }
}
