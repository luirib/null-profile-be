package ch.nullprofile.dto.webauthn;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RegistrationOptionsRequest(
        String txn,
        String displayName
) {}
