package ch.nullprofile.controller;

import ch.nullprofile.dto.OidcErrorResponse;
import ch.nullprofile.dto.TokenResponse;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.service.JwtService;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.PairwiseSubjectService;
import ch.nullprofile.service.RelyingPartyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OidcTokenController {

    private final OidcSessionTransactionService sessionService;
    private final RelyingPartyService relyingPartyService;
    private final PairwiseSubjectService pairwiseSubjectService;
    private final JwtService jwtService;

    public OidcTokenController(
            OidcSessionTransactionService sessionService,
            RelyingPartyService relyingPartyService,
            PairwiseSubjectService pairwiseSubjectService,
            JwtService jwtService) {
        this.sessionService = sessionService;
        this.relyingPartyService = relyingPartyService;
        this.pairwiseSubjectService = pairwiseSubjectService;
        this.jwtService = jwtService;
    }

    @PostMapping(value = "/token", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam("code") String code,
            @RequestParam("client_id") String clientId,
            @RequestParam("code_verifier") String codeVerifier,
            @RequestParam("redirect_uri") String redirectUri,
            HttpSession session) {

        // Validate grant_type
        if (!"authorization_code".equals(grantType)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "unsupported_grant_type", 
                    "Only grant_type=authorization_code is supported");
        }

        // Validate and consume auth code (session-independent lookup)
        var txnOpt = sessionService.validateAndConsumeAuthCode(code);
        if (txnOpt.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid or expired authorization code");
        }

        var txn = txnOpt.get();

        // Validate client_id matches transaction
        if (!txn.rpId().equals(clientId)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid or expired authorization code");
        }

        // Validate PKCE
        if (!sessionService.validatePkce(txn, codeVerifier)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid code_verifier");
        }

        // Validate redirect_uri matches
        if (!redirectUri.equals(txn.redirectUri())) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "redirect_uri does not match");
        }

        // Check transaction is authenticated
        if (txn.authenticatedUserId() == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "User not authenticated");
        }

        // Get relying party for sectorId
        RelyingParty relyingParty = relyingPartyService.findByRpId(clientId)
                .orElse(null);
        if (relyingParty == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_client", 
                    "Unknown client_id");
        }

        // Generate pairwise sub
        String sub = pairwiseSubjectService.generatePairwiseSub(
                txn.authenticatedUserId(), 
                relyingParty.getSectorId());

        // Generate ID token with nonce from transaction
        String idToken = jwtService.generateIdToken(sub, clientId, txn.nonce());

        return ResponseEntity.ok(new TokenResponse(idToken));
    }

    private ResponseEntity<OidcErrorResponse> errorResponse(
            HttpStatus status, String error, String description) {
        return ResponseEntity.status(status)
                .body(new OidcErrorResponse(error, description));
    }
}
