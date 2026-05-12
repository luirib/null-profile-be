package ch.nullprofile.service;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
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

    @Value("${oidc.token.id-token-ttl-seconds:3600}")
    private int idTokenTtlSeconds;

    @Value("${oidc.token.access-token-ttl-seconds:1800}")
    private int accessTokenTtlSeconds;

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
     * Generate ID token. Lifetime is controlled by {@code oidc.token.id-token-ttl-seconds}.
     */
    public String generateIdToken(String sub, String audience, String nonce) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(idTokenTtlSeconds);

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
     * Generate access token (RFC 9068 JWT). Lifetime is controlled by
     * {@code oidc.token.access-token-ttl-seconds}.
     * Contains only minimal OAuth 2.0 claims — no user profile data.
     */
    public String generateAccessToken(String sub, String clientId) {
        try {
            Instant now = Instant.now();
            Instant expiry = now.plusSeconds(accessTokenTtlSeconds);

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .subject(sub)
                    .audience(clientId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiry))
                    .claim("scope", "openid")
                    .claim("client_id", clientId)
                    .jwtID(UUID.randomUUID().toString())
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(rsaKey.getKeyID())
                    .type(new JOSEObjectType("at+jwt"))
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claims);
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Failed to generate access token", e);
        }
    }

    /**
     * Get public JWK for JWKS endpoint
     */
    public JWK getPublicJwk() {
        return rsaKey.toPublicJWK();
    }

    /**
     * Access token lifetime in seconds; used as {@code expires_in} in the token response.
     */
    public int getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }
}
