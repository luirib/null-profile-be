package ch.nullprofile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OidcDiscoveryResponse {
    
    private String issuer;
    
    @JsonProperty("authorization_endpoint")
    private String authorizationEndpoint;
    
    @JsonProperty("token_endpoint")
    private String tokenEndpoint;
    
    @JsonProperty("jwks_uri")
    private String jwksUri;
    
    @JsonProperty("response_types_supported")
    private String[] responseTypesSupported = new String[]{"code"};
    
    @JsonProperty("subject_types_supported")
    private String[] subjectTypesSupported = new String[]{"pairwise"};
    
    @JsonProperty("id_token_signing_alg_values_supported")
    private String[] idTokenSigningAlgValuesSupported = new String[]{"RS256"};
    
    @JsonProperty("scopes_supported")
    private String[] scopesSupported = new String[]{"openid"};
    
    @JsonProperty("token_endpoint_auth_methods_supported")
    private String[] tokenEndpointAuthMethodsSupported = new String[]{"none"};
    
    @JsonProperty("claims_supported")
    private String[] claimsSupported = new String[]{"sub", "iss", "aud", "exp", "iat"};
    
    @JsonProperty("code_challenge_methods_supported")
    private String[] codeChallengeMethodsSupported = new String[]{"S256"};

    // Getters and Setters

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public void setAuthorizationEndpoint(String authorizationEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String[] getResponseTypesSupported() {
        return responseTypesSupported;
    }

    public void setResponseTypesSupported(String[] responseTypesSupported) {
        this.responseTypesSupported = responseTypesSupported;
    }

    public String[] getSubjectTypesSupported() {
        return subjectTypesSupported;
    }

    public void setSubjectTypesSupported(String[] subjectTypesSupported) {
        this.subjectTypesSupported = subjectTypesSupported;
    }

    public String[] getIdTokenSigningAlgValuesSupported() {
        return idTokenSigningAlgValuesSupported;
    }

    public void setIdTokenSigningAlgValuesSupported(String[] idTokenSigningAlgValuesSupported) {
        this.idTokenSigningAlgValuesSupported = idTokenSigningAlgValuesSupported;
    }

    public String[] getScopesSupported() {
        return scopesSupported;
    }

    public void setScopesSupported(String[] scopesSupported) {
        this.scopesSupported = scopesSupported;
    }

    public String[] getTokenEndpointAuthMethodsSupported() {
        return tokenEndpointAuthMethodsSupported;
    }

    public void setTokenEndpointAuthMethodsSupported(String[] tokenEndpointAuthMethodsSupported) {
        this.tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethodsSupported;
    }

    public String[] getClaimsSupported() {
        return claimsSupported;
    }

    public void setClaimsSupported(String[] claimsSupported) {
        this.claimsSupported = claimsSupported;
    }

    public String[] getCodeChallengeMethodsSupported() {
        return codeChallengeMethodsSupported;
    }

    public void setCodeChallengeMethodsSupported(String[] codeChallengeMethodsSupported) {
        this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
    }
}
