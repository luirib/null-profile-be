package ch.nullprofile.controller;

import ch.nullprofile.dto.UserInfo;
import ch.nullprofile.service.OidcSessionTransactionService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/session")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    private final OidcSessionTransactionService sessionService;

    public SessionController(OidcSessionTransactionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Get current authenticated user info
     */
    @GetMapping(value = "/current", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserInfo> getCurrentUser(HttpSession session) {
        if (!sessionService.isAuthenticated(session)) {
            logger.debug("No authenticated user in session");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID userId = UUID.fromString(sessionService.getUserId(session));
        logger.debug("Retrieved current user userId={}", userId);
        
        return ResponseEntity.ok(new UserInfo(userId));
    }

    /**
     * Logout - invalidate session
     */
    @PostMapping(value = "/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        if (sessionService.isAuthenticated(session)) {
            UUID userId = UUID.fromString(sessionService.getUserId(session));
            logger.info("User logout userId={}", userId);
        }
        
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
