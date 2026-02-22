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
class OidcDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testWellKnownDiscoveryEndpoint() throws Exception {
        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("http://localhost:8080"))
                .andExpect(jsonPath("$.authorization_endpoint").value("http://localhost:8080/authorize"))
                .andExpect(jsonPath("$.token_endpoint").value("http://localhost:8080/token"))
                .andExpect(jsonPath("$.jwks_uri").value("http://localhost:8080/jwks.json"))
                .andExpect(jsonPath("$.response_types_supported[0]").value("code"))
                .andExpect(jsonPath("$.subject_types_supported[0]").value("pairwise"))
                .andExpect(jsonPath("$.scopes_supported[0]").value("openid"))
                .andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"));
    }

    @Test
    void testJwksEndpoint() throws Exception {
        mockMvc.perform(get("/jwks.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].kid").exists());
    }
}
