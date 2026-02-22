package ch.nullprofile.controller;

import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.RelyingPartyService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

@Controller
public class OidcAuthorizationController {

    @Value("${oidc.issuer}")
    private String issuer;

    private final RelyingPartyService relyingPartyService;
    private final OidcSessionTransactionService sessionService;

    public OidcAuthorizationController(
            RelyingPartyService relyingPartyService,
            OidcSessionTransactionService sessionService) {
        this.relyingPartyService = relyingPartyService;
        this.sessionService = sessionService;
    }

    @GetMapping("/authorize")
    public RedirectView authorize(
            @RequestParam("response_type") String responseType,
            @RequestParam("client_id") String clientId,
            @RequestParam("redirect_uri") String redirectUri,
            @RequestParam("scope") String scope,
            @RequestParam("state") String state,
            @RequestParam("nonce") String nonce,
            @RequestParam(value = "code_challenge", required = false) String codeChallenge,
            @RequestParam(value = "code_challenge_method", required = false) String codeChallengeMethod,
            HttpSession session) {

        // Validate response_type
        if (!"code".equals(responseType)) {
            return redirectError(redirectUri, "unsupported_response_type", 
                    "Only response_type=code is supported", state);
        }

        // Validate scope
        if (!"openid".equals(scope)) {
            return redirectError(redirectUri, "invalid_scope", 
                    "Only scope=openid is supported", state);
        }

        // Validate code_challenge is present
        if (codeChallenge == null || codeChallenge.isBlank()) {
            return redirectError(redirectUri, "invalid_request", 
                    "code_challenge is required", state);
        }

        // Validate code_challenge_method
        if (!"S256".equals(codeChallengeMethod)) {
            return redirectError(redirectUri, "invalid_request", 
                    "Only code_challenge_method=S256 is supported", state);
        }

        // Lookup relying party (client)
        RelyingParty relyingParty = relyingPartyService.findByRpId(clientId)
                .orElse(null);

        if (relyingParty == null) {
            return redirectError(redirectUri, "unauthorized_client", 
                    "Unknown client_id", state);
        }

        // Validate redirect_uri (exact match)
        if (!relyingPartyService.isRedirectUriValid(relyingParty, redirectUri)) {
            return redirectError(redirectUri, "invalid_request", 
                    "Invalid redirect_uri", state);
        }

        // Store transaction in session
        sessionService.storeAuthorizationRequest(
                session, clientId, redirectUri, codeChallenge, 
                codeChallengeMethod, nonce, state);

        // Check if user is already authenticated
        if (sessionService.isAuthenticated(session)) {
            // Generate auth code and redirect
            String authCode = sessionService.generateAndStoreAuthCode(session);
            return new RedirectView(redirectUri + "?code=" + authCode + "&state=" + state);
        }

        // Redirect to login
        return new RedirectView("/login");
    }

    private RedirectView redirectError(String redirectUri, String error, 
                                      String errorDescription, String state) {
        String url = redirectUri + "?error=" + error + 
                     "&error_description=" + errorDescription.replace(" ", "+") +
                     "&state=" + state;
        return new RedirectView(url);
    }
}
