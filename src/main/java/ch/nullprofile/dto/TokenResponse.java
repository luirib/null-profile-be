package ch.nullprofile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TokenResponse {
    
    @JsonProperty("id_token")
    private String idToken;
    
    @JsonProperty("token_type")
    private String tokenType = "Bearer";
    
    @JsonProperty("expires_in")
    private int expiresIn = 3600;

    public TokenResponse() {
    }

    public TokenResponse(String idToken) {
        this.idToken = idToken;
    }

    // Getters and Setters

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(int expiresIn) {
        this.expiresIn = expiresIn;
    }
}
