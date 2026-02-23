package ch.nullprofile.dto;

import java.util.List;

public record UpdateRelyingPartyRequest(
        String name,
        List<String> redirectUris,
        String sectorId,
        String logoUrl,
        String primaryColor,
        String secondaryColor
) {
}
