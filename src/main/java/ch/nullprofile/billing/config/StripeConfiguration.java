package ch.nullprofile.billing.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Stripe SDK configuration.
 * 
 * Initializes the Stripe API key on application startup.
 * The webhook secret is accessed separately via property injection where needed.
 */
@Configuration
@EnableConfigurationProperties(BillingProperties.class)
public class StripeConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(StripeConfiguration.class);

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${stripe.webhook-secret:}")
    private String stripeWebhookSecret;

    @PostConstruct
    public void init() {
        if (stripeSecretKey == null || stripeSecretKey.isBlank()) {
            logger.warn("Stripe secret key is not configured. Billing features will not work.");
            return;
        }

        if (!stripeSecretKey.startsWith("sk_")) {
            logger.error("Invalid Stripe secret key format. Must start with 'sk_'");
            throw new IllegalStateException("Invalid Stripe secret key format");
        }

        // Initialize Stripe SDK with API key
        Stripe.apiKey = stripeSecretKey;

        if (stripeSecretKey.startsWith("sk_test_")) {
            logger.info("Stripe initialized in TEST mode");
        } else if (stripeSecretKey.startsWith("sk_live_")) {
            logger.info("Stripe initialized in LIVE mode");
        }

        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            logger.warn("Stripe webhook secret is not configured. Webhook signature verification will fail.");
        }
    }

    public String getWebhookSecret() {
        return stripeWebhookSecret;
    }
}
