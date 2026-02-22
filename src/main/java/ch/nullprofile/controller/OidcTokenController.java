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

import java.util.UUID;

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

        // Validate session has transaction data
        String sessionRpId = sessionService.getRpId(session);
        if (sessionRpId == null || !sessionRpId.equals(clientId)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid or expired authorization code");
        }

        // Validate and consume auth code
        if (!sessionService.validateAndConsumeAuthCode(session, code)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid or expired authorization code");
        }

        // Validate PKCE
        if (!sessionService.validatePkce(session, codeVerifier)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "Invalid code_verifier");
        }

        // Validate redirect_uri matches
        String sessionRedirectUri = sessionService.getRedirectUri(session);
        if (!redirectUri.equals(sessionRedirectUri)) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "redirect_uri does not match");
        }

        // Get user ID from session
        String userIdStr = sessionService.getUserId(session);
        if (userIdStr == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_grant", 
                    "User not authenticated");
        }
        UUID userId = UUID.fromString(userIdStr);

        // Get relying party for sectorId
        RelyingParty relyingParty = relyingPartyService.findByRpId(clientId)
                .orElse(null);
        if (relyingParty == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "invalid_client", 
                    "Unknown client_id");
        }

        // Generate pairwise sub
        String sub = pairwiseSubjectService.generatePairwiseSub(userId, relyingParty.getSectorId());

        // Get nonce from session
        String nonce = sessionService.getNonce(session);

        // Generate ID token
        String idToken = jwtService.generateIdToken(sub, clientId, nonce);

        return ResponseEntity.ok(new TokenResponse(idToken));
    }

    private ResponseEntity<OidcErrorResponse> errorResponse(
            HttpStatus status, String error, String description) {
        return ResponseEntity.status(status)
                .body(new OidcErrorResponse(error, description));
    }
}
