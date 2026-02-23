package ch.nullprofile.dto;

import java.util.UUID;

public record PasskeySummary(
        UUID id,
        String name,
        String createdAt
) {
}
