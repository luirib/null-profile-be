package ch.nullprofile.config;

import ch.nullprofile.util.SensitiveDataMasker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs effective configuration at application startup for debugging deployment issues.
 * 
 * Purpose:
 * - Verify environment variables are being read correctly
 * - Identify configuration differences between local and production
 * - Provide immediate visibility into active settings
 * - Help diagnose issues without needing to check multiple config files
 * 
 * Safety:
 * - Masks all sensitive values (passwords, secrets, tokens)
 * - Sanitizes connection strings
 * - Only logs after app is fully initialized
 */
@Component
public class ConfigurationLogger {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationLogger.class);

    // Database configuration
    @Value("${spring.datasource.url:NOT_SET}")
    private String databaseUrl;

    @Value("${spring.datasource.username:NOT_SET}")
    private String databaseUsername;

    @Value("${spring.datasource.hikari.maximum-pool-size:NOT_SET}")
    private String maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:NOT_SET}")
    private String minIdle;

    // Server configuration
    @Value("${server.port:NOT_SET}")
    private String serverPort;

    // Logging configuration
    @Value("${logging.level.root:NOT_SET}")
    private String logLevel;

    @Value("${logging.level.ch.nullprofile:NOT_SET}")
    private String appLogLevel;

    // Flyway configuration
    @Value("${spring.flyway.enabled:NOT_SET}")
    private String flywayEnabled;

    @Value("${spring.flyway.baseline-on-migrate:NOT_SET}")
    private String flywayBaselineOnMigrate;

    // OIDC configuration
    @Value("${oidc.issuer:NOT_SET}")
    private String oidcIssuer;

    // WebAuthn configuration
    @Value("${webauthn.rp.id:NOT_SET}")
    private String webauthnRpId;

    @Value("${webauthn.rp.name:NOT_SET}")
    private String webauthnRpName;

    @Value("${webauthn.origin:NOT_SET}")
    private String webauthnOrigin;

    @Value("${webauthn.challenge.timeout:NOT_SET}")
    private String webauthnChallengeTimeout;

    // CORS configuration
    @Value("${cors.allowed.origins:NOT_SET}")
    private String corsAllowedOrigins;

    // Session cookie configuration
    @Value("${session.cookie.same-site:NOT_SET}")
    private String sessionCookieSameSite;

    @Value("${session.cookie.secure:NOT_SET}")
    private String sessionCookieSecure;

    // Billing configuration
    @Value("${billing.mode:NOT_SET}")
    private String billingMode;

    @Value("${billing.currency:NOT_SET}")
    private String billingCurrency;

    @Value("${billing.success-url:NOT_SET}")
    private String billingSuccessUrl;

    @Value("${billing.cancel-url:NOT_SET}")
    private String billingCancelUrl;

    // Stripe configuration (sensitive - will mask)
    @Value("${stripe.secret-key:NOT_SET}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:NOT_SET}")
    private String stripeWebhookSecret;

    /**
     * Log configuration summary after application has fully started
     */
    @EventListener(ApplicationReadyEvent.class)
    public void logConfigurationSummary() {
        logger.info("========================================");
        logger.info("=== RUNTIME CONFIGURATION SUMMARY ===");
        logger.info("========================================");
        
        // Database
        logger.info("");
        logger.info("[DATABASE]");
        logger.info("  url: {}", SensitiveDataMasker.maskDatabaseUrl(databaseUrl));
        logger.info("  username: {}", databaseUsername);
        logger.info("  max-pool-size: {}", maxPoolSize);
        logger.info("  min-idle: {}", minIdle);
        
        // Server
        logger.info("");
        logger.info("[SERVER]");
        logger.info("  port: {}", serverPort);
        logger.info("  log-level (root): {}", logLevel);
        logger.info("  log-level (app): {}", appLogLevel);
        
        // Flyway
        logger.info("");
        logger.info("[FLYWAY]");
        logger.info("  enabled: {}", flywayEnabled);
        logger.info("  baseline-on-migrate: {}", flywayBaselineOnMigrate);
        
        // OIDC
        logger.info("");
        logger.info("[OIDC]");
        logger.info("  issuer: {}", oidcIssuer);
        
        // WebAuthn
        logger.info("");
        logger.info("[WEBAUTHN]");
        logger.info("  rp-id: {}", webauthnRpId);
        logger.info("  rp-name: {}", webauthnRpName);
        logger.info("  origin: {}", webauthnOrigin);
        logger.info("  challenge-timeout: {} seconds", webauthnChallengeTimeout);
        
        // CORS
        logger.info("");
        logger.info("[CORS]");
        logger.info("  allowed-origins: {}", corsAllowedOrigins);
        
        // Session Cookie - CRITICAL FOR DEBUGGING
        logger.info("");
        logger.info("[SESSION COOKIE] *** CRITICAL FOR CROSS-ORIGIN AUTH ***");
        logger.info("  same-site: {} (must be 'None' for cross-origin HTTPS)", sessionCookieSameSite);
        logger.info("  secure: {} (must be 'true' when same-site=None)", sessionCookieSecure);
        
        // Billing
        logger.info("");
        logger.info("[BILLING]");
        logger.info("  mode: {}", billingMode);
        logger.info("  currency: {}", billingCurrency);
        logger.info("  success-url: {}", billingSuccessUrl);
        logger.info("  cancel-url: {}", billingCancelUrl);
        
        // Stripe (masked)
        logger.info("");
        logger.info("[STRIPE]");
        logger.info("  secret-key: {}", SensitiveDataMasker.maskPresence(stripeSecretKey));
        logger.info("  webhook-secret: {}", SensitiveDataMasker.maskPresence(stripeWebhookSecret));
        
        logger.info("");
        logger.info("========================================");
        logger.info("Configuration loading complete. Check above values match your deployment expectations.");
        logger.info("========================================");
    }
}
