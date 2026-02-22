package ch.nullprofile.controller;

import ch.nullprofile.dto.OidcDiscoveryResponse;
import ch.nullprofile.service.JwtService;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
public class OidcDiscoveryController {

    @Value("${oidc.issuer}")
    private String issuer;

    private final JwtService jwtService;

    public OidcDiscoveryController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GetMapping(value = "/.well-known/openid-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public OidcDiscoveryResponse discovery() {
        OidcDiscoveryResponse response = new OidcDiscoveryResponse();
        response.setIssuer(issuer);
        response.setAuthorizationEndpoint(issuer + "/authorize");
        response.setTokenEndpoint(issuer + "/token");
        response.setJwksUri(issuer + "/jwks.json");
        return response;
    }

    @GetMapping(value = "/jwks.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        JWK publicJwk = jwtService.getPublicJwk();
        return Collections.singletonMap("keys", Collections.singletonList(publicJwk.toJSONObject()));
    }
}
