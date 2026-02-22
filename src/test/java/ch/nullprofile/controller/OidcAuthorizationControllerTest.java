package ch.nullprofile.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "platform.sub.salt=test-salt-for-testing-only",
        "oidc.issuer=http://localhost:8080"
})
class OidcAuthorizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAuthorizeRejectsMissingCodeChallenge() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "http://example.com/callback")
                        .param("scope", "openid")
                        .param("state", "test-state")
                        .param("nonce", "test-nonce")
                        // Missing code_challenge
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("http://example.com/callback?error=*"));
    }

    @Test
    void testAuthorizeRejectsInvalidScope() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "http://example.com/callback")
                        .param("scope", "invalid_scope")
                        .param("state", "test-state")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "test-challenge")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://example.com/callback?error=invalid_scope&error_description=Only+scope=openid+is+supported&state=test-state"));
    }

    @Test
    void testAuthorizeRejectsWrongCodeChallengeMethod() throws Exception {
        mockMvc.perform(get("/authorize")
                        .param("response_type", "code")
                        .param("client_id", "test-client")
                        .param("redirect_uri", "http://example.com/callback")
                        .param("scope", "openid")
                        .param("state", "test-state")
                        .param("nonce", "test-nonce")
                        .param("code_challenge", "test-challenge")
                        .param("code_challenge_method", "plain"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://example.com/callback?error=invalid_request&error_description=Only+code_challenge_method=S256+is+supported&state=test-state"));
    }
}
