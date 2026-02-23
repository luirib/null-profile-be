package ch.nullprofile.controller;

import ch.nullprofile.dto.PasskeySummary;
import ch.nullprofile.entity.WebAuthnCredential;
import ch.nullprofile.repository.WebAuthnCredentialRepository;
import ch.nullprofile.service.OidcSessionTransactionService;
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

    public PasskeysController(
            WebAuthnCredentialRepository credentialRepository,
            OidcSessionTransactionService sessionService) {
        this.credentialRepository = credentialRepository;
        this.sessionService = sessionService;
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
