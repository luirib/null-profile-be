package ch.nullprofile.dto.webauthn;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record RegistrationVerifyRequest(
        String txn,
        String id,
        String rawId,
        String type,
        CredentialResponse response
) {
    public record CredentialResponse(
            String clientDataJSON,
            String attestationObject,
            List<String> transports
    ) {}
}
