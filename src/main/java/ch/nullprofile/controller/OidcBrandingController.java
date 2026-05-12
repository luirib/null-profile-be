package ch.nullprofile.controller;

import ch.nullprofile.dto.OidcTransaction;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.repository.RelyingPartyRepository;
import ch.nullprofile.service.OidcSessionTransactionService;
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
     * Session-independent: looks up transaction by txnId from in-memory cache.
     */
    @GetMapping(value = "/branding", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> getBranding(
            @RequestParam(value = "txn") String txnId) {

        logger.info("Branding request: txnId={}", txnId);

        // Look up transaction by txnId (no session cookie required)
        Optional<OidcTransaction> txnOpt = sessionService.getTransactionByTxnId(txnId);
        if (txnOpt.isEmpty()) {
            logger.warn("Transaction not found in cache for branding request: txnId={}", txnId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        OidcTransaction txn = txnOpt.get();

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
