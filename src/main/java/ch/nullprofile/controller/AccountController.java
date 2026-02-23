package ch.nullprofile.controller;

import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.repository.RedirectUriRepository;
import ch.nullprofile.repository.RelyingPartyRepository;
import ch.nullprofile.repository.UserRepository;
import ch.nullprofile.repository.WebAuthnCredentialRepository;
import ch.nullprofile.service.OidcSessionTransactionService;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private static final Logger logger = LoggerFactory.getLogger(AccountController.class);

    private final OidcSessionTransactionService sessionService;
    private final UserRepository userRepository;
    private final WebAuthnCredentialRepository credentialRepository;
    private final RelyingPartyRepository relyingPartyRepository;
    private final RedirectUriRepository redirectUriRepository;

    public AccountController(
            OidcSessionTransactionService sessionService,
            UserRepository userRepository,
            WebAuthnCredentialRepository credentialRepository,
            RelyingPartyRepository relyingPartyRepository,
            RedirectUriRepository redirectUriRepository) {
        this.sessionService = sessionService;
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.relyingPartyRepository = relyingPartyRepository;
        this.redirectUriRepository = redirectUriRepository;
    }

    /**
     * Delete the current user's account permanently.
     * This removes all passkeys, relying parties, and user data.
     */
    @DeleteMapping
    @Transactional
    public ResponseEntity<Void> deleteAccount(HttpSession session) {
        // Check authentication
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized account deletion attempt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        logger.info("Account deletion requested for userId={}", userId);

        try {
            // Verify user exists
            if (!userRepository.existsById(userId)) {
                logger.warn("Attempted to delete non-existent user userId={}", userId);
                return ResponseEntity.notFound().build();
            }

            // 1. Delete all passkeys (WebAuthn credentials)
            int credentialsDeleted = credentialRepository.findByUserId(userId).size();
            credentialRepository.deleteByUserId(userId);
            logger.info("Deleted {} passkeys for userId={}", credentialsDeleted, userId);

            // 2. Delete all relying parties and their redirect URIs
            List<RelyingParty> relyingParties = relyingPartyRepository.findByCreatedByUserId(userId);
            int relyingPartiesDeleted = relyingParties.size();
            int redirectUrisDeleted = 0;
            
            for (RelyingParty rp : relyingParties) {
                // Delete redirect URIs for this relying party
                int uris = redirectUriRepository.findByRelyingPartyId(rp.getId()).size();
                redirectUriRepository.deleteByRelyingPartyId(rp.getId());
                redirectUrisDeleted += uris;
            }
            
            // Delete relying parties
            relyingPartyRepository.deleteByCreatedByUserId(userId);
            logger.info("Deleted {} relying parties and {} redirect URIs for userId={}", 
                    relyingPartiesDeleted, redirectUrisDeleted, userId);

            // 3. Delete the user
            userRepository.deleteById(userId);
            logger.info("Deleted user account userId={}", userId);

            // 4. Invalidate the session
            session.invalidate();
            logger.info("Session invalidated after account deletion for userId={}", userId);

            // Return 204 No Content
            return ResponseEntity.noContent().build();

        } catch (Exception e) {
            logger.error("Failed to delete account for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header("X-Error-Message", "Failed to delete account. Please try again later.")
                    .build();
        }
    }
}
