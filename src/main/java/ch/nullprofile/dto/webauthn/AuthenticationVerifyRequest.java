package ch.nullprofile.dto.webauthn;

public record AuthenticationVerifyRequest(
        String txn,
        String id,
        String rawId,
        String type,
        AssertionResponse response
) {
    public record AssertionResponse(
            String clientDataJSON,
            String authenticatorData,
            String signature,
            String userHandle
    ) {}
}
