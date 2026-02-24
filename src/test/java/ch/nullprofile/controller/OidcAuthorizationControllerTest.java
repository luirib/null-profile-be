package ch.nullprofile.controller;

import ch.nullprofile.config.OidcProperties;
import ch.nullprofile.entity.RedirectUri;
import ch.nullprofile.entity.RelyingParty;
import ch.nullprofile.entity.User;
import ch.nullprofile.repository.RedirectUriRepository;
import ch.nullprofile.repository.RelyingPartyRepository;
import ch.nullprofile.repository.UserRepository;
import ch.nullprofile.service.OidcSessionTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false",
        "platform.sub.salt=test-salt-for-testing-only",
        "oidc.issuer=http://localhost:8080",
        "webauthn.origin=http://localhost:3000"
})
class OidcAuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RelyingPartyRepository relyingPartyRepository;

    @Autowired
    private RedirectUriRepository redirectUriRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OidcSessionTransactionService sessionService;

    @Autowired
    private OidcProperties oidcProperties;

    private RelyingParty testRelyingParty;
    private String testClientId;
    private String testRedirectUri;
    private User testUser;

    @BeforeEach
    void setUp() {
        // Create test user
        testUser = new User();
        testUser = userRepository.save(testUser);

        // Create test relying party
        testRelyingParty = new RelyingParty();
        testRelyingParty.setRpId("test_client_" + UUID.randomUUID());
        testRelyingParty.setRpName("Test Client");
        testRelyingParty.setSectorId("test-sector");
        testRelyingParty.setCreatedByUserId(testUser.getId());
        testRelyingParty.setStatus("ACTIVE");
        testRelyingParty = relyingPartyRepository.save(testRelyingParty);
        testClientId = testRelyingParty.getRpId();

        // Create test redirect URI
        testRedirectUri = "https://test.example.com/callback";
        RedirectUri redirectUri = new RedirectUri();
        redirectUri.setRelyingPartyId(testRelyingParty.getId());
        redirectUri.setUri(testRedirectUri);
        redirectUriRepository.save(redirectUri);
    }

    @Test
    void authorize_shouldRejectMissingResponseType() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unsupported_response_type"));
    }

    @Test
    void authorize_shouldRejectInvalidResponseType() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "token")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=unsupported_response_type*"));
    }

    @Test
    void authorize_shouldRejectInvalidScope() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid profile")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=invalid_scope*"));
    }

    @Test
    void authorize_shouldRejectMissingNonce() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=invalid_request*"))
                .andExpect(redirectedUrlPattern("*error_description=*nonce*"));
    }

    @Test
    void authorize_shouldRejectMissingCodeChallenge() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=invalid_request*"))
                .andExpect(redirectedUrlPattern("*error_description=*code_challenge*"));
    }

    @Test
    void authorize_shouldRejectWrongCodeChallengeMethod() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "plain"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=invalid_request*"))
                .andExpect(redirectedUrlPattern("*S256*"));
    }

    @Test
    void authorize_shouldRejectUnknownClientId() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", "unknown_client")
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("unauthorized_client"));
    }

    @Test
    void authorize_shouldRejectInvalidRedirectUri() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", "https://evil.com/callback")
                        .param("scope", "openid")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.error_description").value(containsString("redirect_uri")));
    }

    @Test
    void authorize_shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("state", "test-state-123")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("*/login?txn=*"));
    }

    @Test
    void authorize_shouldIssueCodeWhenAlreadyAuthenticated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        sessionService.setAuthenticatedUserId(session, testUser.getId());

        mockMvc.perform(get("/authorize")
                        .session(session)
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("state", "test-state-123")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?code=*"))
                .andExpect(redirectedUrlPattern("*state=test-state-123*"));
    }

    @Test
    void authorize_shouldForceReauthenticationWhenPromptLogin() throws Exception {
        MockHttpSession session = new MockHttpSession();
        sessionService.setAuthenticatedUserId(session, testUser.getId());

        mockMvc.perform(get("/authorize")
                        .session(session)
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("state", "test-state-123")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256")
                        .param("prompt", "login"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("*/login?txn=*"));
    }

    @Test
    void authorizeResume_shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(get("/authorize/resume")
                        .session(session)
                        .param("txn", "test-txn-id"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("*/login*"));
    }

    @Test
    void authorizeResume_shouldReturnErrorWhenNoTransaction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        sessionService.setAuthenticatedUserId(session, testUser.getId());

        mockMvc.perform(get("/authorize/resume")
                        .session(session))
                .andExpect(status().is5xxServerError())
                .andExpect(jsonPath("$.error").value("server_error"));
    }

    @Test
    void authorizeResume_shouldIssueCodeWhenAuthenticatedWithTransaction() throws Exception {
        MockHttpSession session = new MockHttpSession();
        
        // Create transaction
        sessionService.createTransaction(
                session,
                testClientId,
                testRedirectUri,
                "openid",
                "test-state-123",
                "test-nonce",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                "S256",
                true
        );

        // Authenticate
        sessionService.setAuthenticatedUserId(session, testUser.getId());

        mockMvc.perform(get("/authorize/resume")
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?code=*"))
                .andExpect(redirectedUrlPattern("*state=test-state-123*"));
    }

    @Test
    void authorize_shouldPreserveStateInAllResponses() throws Exception {
        String state = "my-unique-state-value";

        // Test with error response
        mockMvc.perform(get("/authorize")
                        .param("response_type", "invalid")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("state", state)
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("*state=" + state + "*"));
    }

    @Test
    void authorize_shouldRejectTooLongNonce() throws Exception {
        int maxLength = oidcProperties.getSecurity().getMaxNonceLength();
        String longNonce = "x".repeat(maxLength + 1);

        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", testClientId)
                        .param("redirect_uri", testRedirectUri)
                        .param("scope", "openid")
                        .param("nonce", longNonce)
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern(testRedirectUri + "?error=invalid_request*"))
                .andExpect(redirectedUrlPattern("*nonce*too*long*"));
    }
}
