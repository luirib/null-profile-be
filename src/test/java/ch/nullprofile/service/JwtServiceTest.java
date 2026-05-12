package ch.nullprofile.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;
import java.time.Instant;

class JwtServiceTest {

    private static final int ID_TOKEN_TTL = 3600;
    private static final int ACCESS_TOKEN_TTL = 1800;
    private static final String ISSUER = "https://test.example.com";
    private static final String CLIENT_ID = "test-client-id";
    private static final String SUB = "pairwise-sub-value";

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "issuer", ISSUER);
        ReflectionTestUtils.setField(jwtService, "idTokenTtlSeconds", ID_TOKEN_TTL);
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlSeconds", ACCESS_TOKEN_TTL);
        jwtService.init(); // trigger @PostConstruct RSA key generation
    }

    // ── Access token structure ──────────────────────────────────────────────

    @Test
    void accessToken_isThreePart() {
        String token = jwtService.generateAccessToken(SUB, CLIENT_ID);
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void accessToken_header_hasAlgRS256() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID));
        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
    }

    @Test
    void accessToken_header_hasTypAtJwt() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID));
        assertThat(jwt.getHeader().getType().getType()).isEqualTo("at+jwt");
    }

    @Test
    void accessToken_header_hasKidMatchingJwks() throws Exception {
        SignedJWT jwt = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID));
        String expectedKid = jwtService.getPublicJwk().getKeyID();
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(expectedKid);
    }

    // ── Access token claims ─────────────────────────────────────────────────

    @Test
    void accessToken_claim_iss() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
    }

    @Test
    void accessToken_claim_sub() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(SUB);
    }

    @Test
    void accessToken_claim_aud() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getAudience()).containsExactly(CLIENT_ID);
    }

    @Test
    void accessToken_claim_scope_isOpenid() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getStringClaim("scope")).isEqualTo("openid");
    }

    @Test
    void accessToken_claim_clientId() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getStringClaim("client_id")).isEqualTo(CLIENT_ID);
    }

    @Test
    void accessToken_claim_jti_isPresent() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    void accessToken_claim_jti_isUniquePerToken() throws Exception {
        String jti1 = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet().getJWTID();
        String jti2 = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet().getJWTID();
        assertThat(jti1).isNotEqualTo(jti2);
    }

    @Test
    void accessToken_claim_iat_isNow() throws Exception {
        Instant before = Instant.now().minusSeconds(2);
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getIssueTime()).isAfter(Date.from(before));
    }

    @Test
    void accessToken_claim_exp_usesAccessTokenTtl() throws Exception {
        Instant before = Instant.now();
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        Instant exp = claims.getExpirationTime().toInstant();
        long diffSeconds = ChronoUnit.SECONDS.between(before, exp);
        // Allow 5 second tolerance for test execution time
        assertThat(diffSeconds).isBetween((long) ACCESS_TOKEN_TTL - 5, (long) ACCESS_TOKEN_TTL + 5);
    }

    // ── Access token MUST NOT include user profile data ─────────────────────

    @Test
    void accessToken_doesNotContain_userId() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKey("userId");
        assertThat(claims.getClaims()).doesNotContainKey("user_id");
    }

    @Test
    void accessToken_doesNotContain_email() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKey("email");
    }

    @Test
    void accessToken_doesNotContain_profileAttributes() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKeys("name", "given_name", "family_name", "picture", "profile");
    }

    @Test
    void accessToken_doesNotContain_credentialDetails() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();
        assertThat(claims.getClaims()).doesNotContainKeys("credential", "passkey", "aaguid");
    }

    // ── ID token uses ID token TTL ──────────────────────────────────────────

    @Test
    void idToken_claim_exp_usesIdTokenTtl() throws Exception {
        Instant before = Instant.now();
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateIdToken(SUB, CLIENT_ID, "test-nonce")).getJWTClaimsSet();
        Instant exp = claims.getExpirationTime().toInstant();
        long diffSeconds = ChronoUnit.SECONDS.between(before, exp);
        assertThat(diffSeconds).isBetween((long) ID_TOKEN_TTL - 5, (long) ID_TOKEN_TTL + 5);
    }

    @Test
    void idToken_claim_nonce() throws Exception {
        JWTClaimsSet claims = SignedJWT.parse(jwtService.generateIdToken(SUB, CLIENT_ID, "my-nonce")).getJWTClaimsSet();
        assertThat(claims.getStringClaim("nonce")).isEqualTo("my-nonce");
    }

    // ── Token lifetimes are independently configurable ──────────────────────

    @Test
    void idTokenAndAccessToken_haveDistinctExpiries() throws Exception {
        Instant before = Instant.now();
        JWTClaimsSet idClaims = SignedJWT.parse(jwtService.generateIdToken(SUB, CLIENT_ID, "n")).getJWTClaimsSet();
        JWTClaimsSet atClaims = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID)).getJWTClaimsSet();

        long idExpSeconds = ChronoUnit.SECONDS.between(before, idClaims.getExpirationTime().toInstant());
        long atExpSeconds = ChronoUnit.SECONDS.between(before, atClaims.getExpirationTime().toInstant());

        // With TTLs 3600 and 1800 they must differ significantly
        assertThat(Math.abs(idExpSeconds - atExpSeconds)).isGreaterThan(1700);
    }

    @Test
    void getAccessTokenTtlSeconds_returnsConfiguredValue() {
        assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(ACCESS_TOKEN_TTL);
    }

    // ── Both tokens share the same signing key ──────────────────────────────

    @Test
    void idToken_and_accessToken_shareTheSameKid() throws Exception {
        SignedJWT idJwt = SignedJWT.parse(jwtService.generateIdToken(SUB, CLIENT_ID, "n"));
        SignedJWT atJwt = SignedJWT.parse(jwtService.generateAccessToken(SUB, CLIENT_ID));
        assertThat(idJwt.getHeader().getKeyID()).isEqualTo(atJwt.getHeader().getKeyID());
    }
}
