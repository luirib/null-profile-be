package ch.nullprofile.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class OidcErrorResponse {
    
    private String error;
    
    @JsonProperty("error_description")
    private String errorDescription;

    public OidcErrorResponse() {
    }

    public OidcErrorResponse(String error, String errorDescription) {
        this.error = error;
        this.errorDescription = errorDescription;
    }

    // Getters and Setters

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }
}
