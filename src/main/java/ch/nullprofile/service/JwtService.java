package ch.nullprofile.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    @Value("${oidc.issuer}")
    private String issuer;

    private RSAKey rsaKey;
    private RSASSASigner signer;

    @PostConstruct
    public void init() throws NoSuchAlgorithmException {
        // Generate RSA keypair at startup (dev mode)
        // TODO: In production, load from secure storage
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair keyPair = keyGen.generateKeyPair();

        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Create JWK with kid
        this.rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .algorithm(JWSAlgorithm.RS256)
                .build();

        this.signer = new RSASSASigner(privateKey);
    }

    /**
     * Generate ID token
     */
    public String generateIdToken(String sub, String audience, String nonce) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(3600); // 1 hour

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(sub)
                    .audience(audience)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .claim("nonce", nonce)
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate ID token", e);
        }
    }

    /**
     * Get public JWK for JWKS endpoint
     */
    public JWK getPublicJwk() {
        return rsaKey.toPublicJWK();
    }
}
