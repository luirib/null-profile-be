package ch.nullprofile.dto;

import java.util.List;
import java.util.UUID;

public record RelyingPartyDetail(
        UUID id,
        String rpId,
        String name,
        String sectorId,
        List<String> redirectUris,
        BrandingInfo branding
) {
    public record BrandingInfo(
            String logoUrl,
            String primaryColor,
            String secondaryColor
    ) {
    }
}
