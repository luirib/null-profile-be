package ch.nullprofile.dto;

import java.util.UUID;

public record RelyingPartySummary(
        UUID id,
        String rpId,
        String name,
        String createdAt
) {
}
