package ch.nullprofile.controller;

import ch.nullprofile.dto.OidcTransaction;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.repository.RelyingPartyRepository;
import ch.nullprofile.service.OidcSessionTransactionService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/oidc")
public class OidcBrandingController {

    private static final Logger logger = LoggerFactory.getLogger(OidcBrandingController.class);

    private final OidcSessionTransactionService sessionService;
    private final RelyingPartyRepository relyingPartyRepository;

    public OidcBrandingController(
            OidcSessionTransactionService sessionService,
            RelyingPartyRepository relyingPartyRepository) {
        this.sessionService = sessionService;
        this.relyingPartyRepository = relyingPartyRepository;
    }

    /**
     * Get branding information for an OIDC transaction
     * GET /api/oidc/branding?txn={txnId}
     */
    @GetMapping(value = "/branding", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getBranding(
            @RequestParam(value = "txn", required = false) String txnId,
            HttpSession session) {

        logger.info("Branding request: txnId={}", txnId);

        // Get transaction from session
        Optional<OidcTransaction> txnOpt = sessionService.getTransaction(session);
        if (txnOpt.isEmpty()) {
            logger.warn("No transaction in session for branding request");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        OidcTransaction txn = txnOpt.get();

        // Verify transaction ID matches (if provided)
        if (txnId != null && !txnId.equals(txn.txnId())) {
            logger.warn("Transaction ID mismatch: expected={}, provided={}", txn.txnId(), txnId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // Get relying party by client_id (rpId)
        Optional<RelyingParty> rpOpt = relyingPartyRepository.findByRpId(txn.rpId());
        if (rpOpt.isEmpty()) {
            logger.warn("Relying party not found: rpId={}", txn.rpId());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        RelyingParty rp = rpOpt.get();

        // Build branding response
        Map<String, String> branding = new HashMap<>();
        branding.put("rpName", rp.getRpName());
        branding.put("displayName", rp.getRpName()); // Use rpName as displayName
        
        if (rp.getBrandingPrimaryColor() != null && !rp.getBrandingPrimaryColor().isBlank()) {
            branding.put("primaryColor", rp.getBrandingPrimaryColor());
        }
        
        if (rp.getBrandingSecondaryColor() != null && !rp.getBrandingSecondaryColor().isBlank()) {
            branding.put("secondaryColor", rp.getBrandingSecondaryColor());
        }
        
        if (rp.getBrandingLogoUrl() != null && !rp.getBrandingLogoUrl().isBlank()) {
            branding.put("logoUrl", rp.getBrandingLogoUrl());
        }

        logger.info("Returning branding for RP: rpName={}", rp.getRpName());

        return ResponseEntity.ok(branding);
    }
}
