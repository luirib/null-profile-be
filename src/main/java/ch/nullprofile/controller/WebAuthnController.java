package ch.nullprofile.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/webauthn")
public class WebAuthnController {

    /**
     * TODO: Implement WebAuthn registration options
     * Generate challenge and options for credential creation
     */
    @PostMapping(value = "/registration/options", 
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registrationOptions(@RequestBody Map<String, Object> request) {
        // TODO: Implement WebAuthn registration options
        return ResponseEntity.ok(Map.of(
                "status", "not_implemented",
                "message", "WebAuthn registration options endpoint - to be implemented"
        ));
    }

    /**
     * TODO: Implement WebAuthn registration verification
     * Verify the credential and store it
     */
    @PostMapping(value = "/registration/verify",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> registrationVerify(@RequestBody Map<String, Object> request) {
        // TODO: Implement WebAuthn registration verification
        // - Verify attestation
        // - Create User entity
        // - Store WebAuthnCredential
        // - Create authenticated session
        return ResponseEntity.ok(Map.of(
                "status", "not_implemented",
                "message", "WebAuthn registration verification endpoint - to be implemented"
        ));
    }

    /**
     * TODO: Implement WebAuthn authentication options
     * Generate challenge for authentication
     */
    @PostMapping(value = "/authentication/options",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authenticationOptions(@RequestBody Map<String, Object> request) {
        // TODO: Implement WebAuthn authentication options
        return ResponseEntity.ok(Map.of(
                "status", "not_implemented",
                "message", "WebAuthn authentication options endpoint - to be implemented"
        ));
    }

    /**
     * TODO: Implement WebAuthn authentication verification
     * Verify the assertion and create session
     */
    @PostMapping(value = "/authentication/verify",
            produces = MediaType.APPLICATION_JSON_VALUE,
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> authenticationVerify(@RequestBody Map<String, Object> request) {
        // TODO: Implement WebAuthn authentication verification
        // - Lookup credential by credentialId
        // - Verify assertion signature
        // - Update sign_count and last_used_at
        // - Update user.last_login_at
        // - Create authenticated session (sessionService.setAuthenticatedUser)
        return ResponseEntity.ok(Map.of(
                "status", "not_implemented",
                "message", "WebAuthn authentication verification endpoint - to be implemented"
        ));
    }
}
