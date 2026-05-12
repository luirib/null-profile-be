package ch.nullprofile.controller;

import ch.nullprofile.dto.OidcTransaction;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.service.JwtService;
import ch.nullprofile.service.OidcSessionTransactionService;
import ch.nullprofile.service.PairwiseSubjectService;
import ch.nullprofile.service.RelyingPartyService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "platform.sub.salt=test-salt-for-testing-only",
        "oidc.issuer=https://test-issuer.example.com",
        "oidc.token.id-token-ttl-seconds=3600",
        "oidc.token.access-token-ttl-seconds=1800"
})
class OidcTokenEndpointSuccessTest {

    private static final String CLIENT_ID = "test-rp-client";
    private static final String NONCE = "test-nonce-value";
    private static final String REDIRECT_URI = "https://rp.example.com/callback";
    private static final String PAIRWISE_SUB = "pairwise-sub-abcdef";
    private static final int ACCESS_TOKEN_TTL = 1800;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private OidcSessionTransactionService sessionService;

    @MockBean
    private RelyingPartyService relyingPartyService;

    @MockBean
    private PairwiseSubjectService pairwiseSubjectService;

    private OidcTransaction validTransaction;
    private RelyingParty relyingParty;

    @BeforeEach
    void setUp() throws Exception {
        // Default: invalid code returns empty
        when(sessionService.validateAndConsumeAuthCode(anyString())).thenReturn(Optional.empty());

        // Build a fully-authenticated transaction
        validTransaction = OidcTransaction.createNew(
                CLIENT_ID, REDIRECT_URI, "openid", "state-xyz", NONCE,
                "challenge-abc", "S256", false
        ).withAuthenticatedUser(UUID.randomUUID());

        // Build a relying party stub
        relyingParty = new RelyingParty();
        relyingParty.setRpId(CLIENT_ID);
        relyingParty.setSectorId("example.com");

        when(relyingPartyService.findByRpId(CLIENT_ID)).thenReturn(Optional.of(relyingParty));
        when(pairwiseSubjectService.generatePairwiseSub(any(UUID.class), anyString()))
                .thenReturn(PAIRWISE_SUB);
        when(sessionService.validatePkce(any(OidcTransaction.class), anyString())).thenReturn(true);
    }

    private void configureValidCode(String code) {
        when(sessionService.validateAndConsumeAuthCode(code)).thenReturn(Optional.of(validTransaction));
    }

    // ── Successful token exchange ────────────────────────────────────────────

    @Test
    void successfulExchange_returnsBothAccessAndIdToken() throws Exception {
        configureValidCode("valid-code-001");
        mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-001")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.id_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber());
    }

    @Test
    void successfulExchange_expiresIn_equalsAccessTokenTtl() throws Exception {
        configureValidCode("valid-code-002");
        mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-002")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expires_in").value(ACCESS_TOKEN_TTL));
    }

    @Test
    void accessToken_isValidThreePartJwt() throws Exception {
        configureValidCode("valid-code-003");
        MvcResult result = mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-003")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        String accessToken = extractJsonField(body, "access_token");
        assertThat(accessToken.split("\\.")).hasSize(3);
    }

    @Test
    void accessToken_hasAlgRS256AndExpectedKid() throws Exception {
        configureValidCode("valid-code-004");
        MvcResult result = mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-004")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractJsonField(result.getResponse().getContentAsString(), "access_token");
        SignedJWT jwt = SignedJWT.parse(accessToken);

        assertThat(jwt.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(jwtService.getPublicJwk().getKeyID());
    }

    @Test
    void accessToken_claims_containRequiredFields() throws Exception {
        configureValidCode("valid-code-005");
        MvcResult result = mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-005")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractJsonField(result.getResponse().getContentAsString(), "access_token");
        JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();

        assertThat(claims.getIssuer()).isEqualTo("https://test-issuer.example.com");
        assertThat(claims.getSubject()).isEqualTo(PAIRWISE_SUB);
        assertThat(claims.getAudience()).containsExactly(CLIENT_ID);
        assertThat(claims.getIssueTime()).isNotNull();
        assertThat(claims.getExpirationTime()).isNotNull();
        assertThat(claims.getStringClaim("scope")).isEqualTo("openid");
        assertThat(claims.getStringClaim("client_id")).isEqualTo(CLIENT_ID);
        assertThat(claims.getJWTID()).isNotBlank();
    }

    @Test
    void accessToken_doesNotContain_userProfileData() throws Exception {
        configureValidCode("valid-code-006");
        MvcResult result = mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-006")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = extractJsonField(result.getResponse().getContentAsString(), "access_token");
        JWTClaimsSet claims = SignedJWT.parse(accessToken).getJWTClaimsSet();

        assertThat(claims.getClaims()).doesNotContainKeys(
                "userId", "user_id", "email", "name", "given_name", "family_name",
                "picture", "profile", "credential", "passkey", "aaguid");
    }

    @Test
    void idToken_hasDistinctExpiryFromAccessToken() throws Exception {
        configureValidCode("valid-code-007");
        MvcResult result = mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "valid-code-007")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        JWTClaimsSet idClaims = SignedJWT.parse(extractJsonField(body, "id_token")).getJWTClaimsSet();
        JWTClaimsSet atClaims = SignedJWT.parse(extractJsonField(body, "access_token")).getJWTClaimsSet();

        long idExpEpoch = idClaims.getExpirationTime().getTime();
        long atExpEpoch = atClaims.getExpirationTime().getTime();
        // ID token TTL=3600, access token TTL=1800 → difference ~1800 seconds
        assertThat(Math.abs(idExpEpoch - atExpEpoch)).isGreaterThan(1700_000L);
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @Test
    void invalidCode_returnsNoTokens() throws Exception {
        mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "invalid-or-expired-code")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"))
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.id_token").doesNotExist());
    }

    @Test
    void consumedCode_returnsNoTokens() throws Exception {
        // First call consumes the code
        when(sessionService.validateAndConsumeAuthCode("one-time-code"))
                .thenReturn(Optional.of(validTransaction))
                .thenReturn(Optional.empty()); // second call: already consumed

        mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "one-time-code")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isOk());

        // Second attempt with same code must fail
        mockMvc.perform(post("/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("code", "one-time-code")
                        .param("client_id", CLIENT_ID)
                        .param("code_verifier", "verifier-xyz")
                        .param("redirect_uri", REDIRECT_URI))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_grant"));
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /** Minimal JSON field extractor to avoid pulling in extra test deps. */
    private String extractJsonField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int start = json.indexOf(key);
        if (start == -1) throw new AssertionError("Field '" + field + "' not found in: " + json);
        start += key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}
