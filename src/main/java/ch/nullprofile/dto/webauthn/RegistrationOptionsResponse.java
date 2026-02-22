package ch.nullprofile.dto.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RegistrationOptionsResponse(
        String challenge,
        RelyingPartyInfo rp,
        UserInfo user,
        @JsonProperty("pubKeyCredParams") List<PubKeyCredParam> pubKeyCredParams,
        Long timeout,
        String attestation,
        @JsonProperty("authenticatorSelection") AuthenticatorSelection authenticatorSelection
) {
    public record RelyingPartyInfo(String id, String name) {}
    
    public record UserInfo(String id, String name, String displayName) {}
    
    public record PubKeyCredParam(String type, int alg) {}
    
    public record AuthenticatorSelection(
            String residentKey,
            String userVerification
    ) {}
}
