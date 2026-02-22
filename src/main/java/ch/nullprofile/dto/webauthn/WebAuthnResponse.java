package ch.nullprofile.dto.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebAuthnResponse(
        @JsonProperty("success") boolean successful,
        String redirectUrl,
        String error,
        String errorDescription
) {
    public static WebAuthnResponse success() {
        return new WebAuthnResponse(true, null, null, null);
    }
    
    public static WebAuthnResponse successWithRedirect(String redirectUrl) {
        return new WebAuthnResponse(true, redirectUrl, null, null);
    }
    
    public static WebAuthnResponse error(String error, String errorDescription) {
        return new WebAuthnResponse(false, null, error, errorDescription);
    }
}
