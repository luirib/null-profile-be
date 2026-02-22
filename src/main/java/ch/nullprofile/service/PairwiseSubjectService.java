package ch.nullprofile.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;

@Service
public class PairwiseSubjectService {

    private final String platformSalt;

    public PairwiseSubjectService(@Value("${platform.sub.salt}") String platformSalt) {
        if (platformSalt == null || platformSalt.isBlank()) {
            throw new IllegalArgumentException("PLATFORM_SUB_SALT must be set");
        }
        this.platformSalt = platformSalt;
    }

    /**
     * Generate pairwise subject identifier
     * sub = base64url(HMAC-SHA-256(platformSalt, userId + 0x1F + sectorId))
     */
    public String generatePairwiseSub(UUID userId, String sectorId) {
        try {
            // Concatenate userId + 0x1F + sectorId
            String input = userId.toString() + "\u001F" + sectorId;
            
            // Create HMAC-SHA-256
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                platformSalt.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            
            // Base64 URL encode without padding
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate pairwise subject", e);
        }
    }
}
