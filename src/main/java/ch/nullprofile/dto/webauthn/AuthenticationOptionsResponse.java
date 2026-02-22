package ch.nullprofile.dto.webauthn;

public record AuthenticationOptionsResponse(
        String challenge,
        Long timeout,
        String rpId,
        String userVerification
) {}
