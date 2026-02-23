package ch.nullprofile.controller;

import ch.nullprofile.dto.*;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.RelyingPartyService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/relying-parties")
public class RelyingPartyController {

    private static final Logger logger = LoggerFactory.getLogger(RelyingPartyController.class);

    private final RelyingPartyService relyingPartyService;
    private final OidcSessionTransactionService sessionService;

    public RelyingPartyController(
            RelyingPartyService relyingPartyService,
            OidcSessionTransactionService sessionService) {
        this.relyingPartyService = relyingPartyService;
        this.sessionService = sessionService;
    }

    /**
     * Get all relying parties for the authenticated user
     */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RelyingPartySummary>> getAllRelyingParties(HttpSession session) {
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to list relying parties");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        List<RelyingPartySummary> relyingParties = relyingPartyService.getAllRelyingParties(userId);
        
        logger.info("Retrieved {} relying parties for userId={}", relyingParties.size(), userId);
        return ResponseEntity.ok(relyingParties);
    }

    /**
     * Get single relying party by ID
     */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RelyingPartyDetail> getRelyingPartyById(
            @PathVariable UUID id,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to get relying party id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        
        return relyingPartyService.getRelyingPartyById(id, userId)
                .map(rp -> {
                    logger.info("Retrieved relying party id={} for userId={}", id, userId);
                    return ResponseEntity.ok(rp);
                })
                .orElseGet(() -> {
                    logger.warn("Relying party id={} not found or unauthorized for userId={}", id, userId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                });
    }

    /**
     * Create a new relying party
     */
    @PostMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RelyingPartyDetail> createRelyingParty(
            @RequestBody CreateRelyingPartyRequest request,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to create relying party");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Validate request
        if (request.name() == null || request.name().isBlank()) {
            logger.warn("Invalid create request: name is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (request.redirectUris() == null || request.redirectUris().isEmpty()) {
            logger.warn("Invalid create request: at least one redirect URI is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));

        try {
            RelyingPartyDetail created = relyingPartyService.createRelyingParty(request, userId);
            logger.info("Created relying party id={} for userId={}", created.id(), userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            logger.error("Failed to create relying party for userId={}", userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Update an existing relying party
     */
    @PutMapping(
            value = "/{id}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RelyingPartyDetail> updateRelyingParty(
            @PathVariable UUID id,
            @RequestBody UpdateRelyingPartyRequest request,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to update relying party id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));

        try {
            return relyingPartyService.updateRelyingParty(id, request, userId)
                    .map(rp -> {
                        logger.info("Updated relying party id={} for userId={}", id, userId);
                        return ResponseEntity.ok(rp);
                    })
                    .orElseGet(() -> {
                        logger.warn("Cannot update relying party id={} - not found or unauthorized for userId={}", id, userId);
                        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
                    });
        } catch (Exception e) {
            logger.error("Failed to update relying party id={} for userId={}", id, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a relying party
     */
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<Void> deleteRelyingParty(
            @PathVariable UUID id,
            HttpSession session) {
        
        if (!sessionService.isAuthenticated(session)) {
            logger.warn("Unauthorized access attempt to delete relying party id={}", id);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));

        try {
            boolean deleted = relyingPartyService.deleteRelyingParty(id, userId);
            
            if (deleted) {
                logger.info("Deleted relying party id={} for userId={}", id, userId);
                return ResponseEntity.noContent().build();
            } else {
                logger.warn("Cannot delete relying party id={} - not found or unauthorized for userId={}", id, userId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
        } catch (Exception e) {
            logger.error("Failed to delete relying party id={} for userId={}", id, userId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
