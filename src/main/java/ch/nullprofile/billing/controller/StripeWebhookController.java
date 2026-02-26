package ch.nullprofile.billing.controller;

import ch.nullprofile.billing.config.StripeConfiguration;
import ch.nullprofile.billing.service.StripeWebhookService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for handling Stripe webhook events.
 * 
 * Verifies webhook signatures and processes events with idempotency.
 * All webhook events are stored for audit purposes.
 */
@RestController
@RequestMapping("/api/billing")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);

    private final StripeConfiguration stripeConfiguration;
    private final StripeWebhookService stripeWebhookService;

    public StripeWebhookController(
            StripeConfiguration stripeConfiguration,
            StripeWebhookService stripeWebhookService) {
        this.stripeConfiguration = stripeConfiguration;
        this.stripeWebhookService = stripeWebhookService;
    }

    /**
     * Handle Stripe webhook events.
     *
     * @param payload The raw webhook payload
     * @param signatureHeader The Stripe-Signature header
     * @return ResponseEntity with appropriate status
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {

        logger.debug("Received webhook request");

        if (signatureHeader == null || signatureHeader.isBlank()) {
            logger.warn("Missing Stripe-Signature header");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing Stripe-Signature header"));
        }

        Event event;
        try {
            // Verify webhook signature
            event = Webhook.constructEvent(
                    payload,
                    signatureHeader,
                    stripeConfiguration.getWebhookSecret()
            );
        } catch (SignatureVerificationException e) {
            logger.error("Webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            logger.error("Failed to parse webhook payload", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid payload"));
        }

        try {
            // Process webhook event (with idempotency)
            boolean processed = stripeWebhookService.processWebhookEvent(event);

            if (processed) {
                logger.info("Webhook event {} processed successfully", event.getId());
            } else {
                logger.info("Webhook event {} already processed (idempotent)", event.getId());
            }

            return ResponseEntity.ok(Map.of("received", true));

        } catch (Exception e) {
            logger.error("Error processing webhook event {}", event.getId(), e);
            // Return 500 so Stripe will retry
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process event"));
        }
    }
}
