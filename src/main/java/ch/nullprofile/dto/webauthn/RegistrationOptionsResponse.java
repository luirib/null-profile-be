package ch.nullprofile.dto.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationOptionsResponse(
        String challenge,
        RelyingPartyInfo rp,
        UserInfo user,
        @JsonProperty("pubKeyCredParams") List<PubKeyCredParam> pubKeyCredParams,
        Long timeout,
        String attestation,
        @JsonProperty("authenticatorSelection") AuthenticatorSelection authenticatorSelection,
        @JsonProperty("excludeCredentials") List<ExcludeCredential> excludeCredentials
) {
    public record RelyingPartyInfo(String id, String name) {}
    
    public record UserInfo(String id, String name, String displayName) {}
    
    public record PubKeyCredParam(String type, int alg) {}
    
    public record AuthenticatorSelection(
            String residentKey,
            String userVerification
    ) {}
    
    public record ExcludeCredential(String type, String id) {}
}
