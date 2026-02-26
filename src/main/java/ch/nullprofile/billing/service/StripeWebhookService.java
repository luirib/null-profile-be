package ch.nullprofile.billing.service;

import ch.nullprofile.billing.model.BillingEvent;
import ch.nullprofile.billing.model.BillingPayment;
import ch.nullprofile.billing.repository.BillingEventRepository;
import ch.nullprofile.billing.repository.BillingPaymentRepository;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing Stripe webhook events.
 * 
 * <p><strong>Idempotency Guarantees:</strong></p>
 * <ul>
 *   <li>Each webhook event is processed exactly once based on unique stripe_event_id</li>
 *   <li>Events are stored in billing_events table before processing</li>
 *   <li>Duplicate webhook deliveries are ignored (Stripe may retry failed webhooks)</li>
 * </ul>
 * 
 * <p><strong>Payment Status Flow:</strong></p>
 * <ol>
 *   <li>checkout.session.completed → Creates payment with status='pending'</li>
 *   <li>payment_intent.succeeded → Updates payment to status='succeeded'</li>
 * </ol>
 * 
 * <p>Only payments with status='succeeded' are counted in donation summaries.</p>
 * 
 * <p><strong>Duplicate Prevention:</strong></p>
 * <ul>
 *   <li>billing_events.stripe_event_id is unique (database constraint)</li>
 *   <li>billing_payments.stripe_payment_intent_id is unique (database constraint)</li>
 *   <li>Prevents duplicate payment records even if webhooks are delivered multiple times</li>
 * </ul>
 */
@Service
public class StripeWebhookService {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookService.class);

    private final BillingEventRepository billingEventRepository;
    private final BillingPaymentRepository billingPaymentRepository;

    public StripeWebhookService(
            BillingEventRepository billingEventRepository,
            BillingPaymentRepository billingPaymentRepository) {
        this.billingEventRepository = billingEventRepository;
        this.billingPaymentRepository = billingPaymentRepository;
    }

    /**
     * Process a Stripe webhook event with idempotency.
     *
     * @param event The Stripe Event object
     * @return true if event was processed, false if already processed
     */
    @Transactional
    public boolean processWebhookEvent(Event event) {
        String eventId = event.getId();
        String eventType = event.getType();

        logger.info("Processing webhook event {} of type {}", eventId, eventType);

        // Check if event already processed (idempotency)
        if (billingEventRepository.existsByStripeEventId(eventId)) {
            logger.info("Event {} already processed, skipping", eventId);
            return false;
        }

        // Store event for audit and idempotency
        BillingEvent billingEvent = new BillingEvent(
                eventId,
                eventType,
                event.toJson()
        );
        billingEventRepository.save(billingEvent);
        logger.debug("Saved billing event {}", eventId);

        // Process event based on type
        try {
            switch (eventType) {
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(event);
                    break;
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                default:
                    logger.debug("Event type {} not handled, recorded for audit only", eventType);
            }

            // Mark event as processed
            billingEvent.setProcessedAt(Instant.now());
            billingEventRepository.save(billingEvent);
            logger.info("Successfully processed event {}", eventId);
            return true;

        } catch (Exception e) {
            logger.error("Error processing event {}: {}", eventId, e.getMessage(), e);
            // Event is saved but not marked as processed, can be retried
            throw new RuntimeException("Failed to process webhook event", e);
        }
    }

    /**
     * Handle checkout.session.completed event.
     */
    private void handleCheckoutSessionCompleted(Event event) {
        Session session;
        try {
            session = (Session) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            logger.error("Failed to deserialize session from event {}", event.getId(), e);
            throw new RuntimeException("Failed to deserialize session", e);
        }

        logger.info("Processing checkout session completed: {}", session.getId());

        Map<String, String> metadata = session.getMetadata();
        if (metadata == null || !metadata.containsKey("userId")) {
            logger.warn("Session {} missing userId in metadata", session.getId());
            return;
        }

        UUID userId = UUID.fromString(metadata.get("userId"));
        String type = metadata.getOrDefault("type", "donation");

        // Check if payment already exists
        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId != null && billingPaymentRepository.findByStripePaymentIntentId(paymentIntentId).isPresent()) {
            logger.debug("Payment already recorded for payment intent {}", paymentIntentId);
            return;
        }

        // Create payment record
        BillingPayment payment = new BillingPayment(
                userId,
                type,
                session.getAmountTotal(),
                session.getCurrency()
        );
        payment.setStripePaymentIntentId(paymentIntentId);
        payment.setStatus("pending");
        payment.setPaidAt(Instant.ofEpochSecond(session.getCreated()));

        billingPaymentRepository.save(payment);
        logger.info("Created payment record for user {} from session {}", userId, session.getId());
    }

    /**
     * Handle payment_intent.succeeded event.
     */
    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent;
        try {
            paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            logger.error("Failed to deserialize payment intent from event {}", event.getId(), e);
            throw new RuntimeException("Failed to deserialize payment intent", e);
        }

        logger.info("Processing payment intent succeeded: {}", paymentIntent.getId());

        // Find existing payment record or create new one
        BillingPayment payment = billingPaymentRepository
                .findByStripePaymentIntentId(paymentIntent.getId())
                .orElseGet(() -> {
                    // Try to get userId from metadata
                    Map<String, String> metadata = paymentIntent.getMetadata();
                    if (metadata == null || !metadata.containsKey("userId")) {
                        logger.warn("Payment intent {} missing userId in metadata", paymentIntent.getId());
                        return null;
                    }

                    UUID userId = UUID.fromString(metadata.get("userId"));
                    String type = metadata.getOrDefault("type", "donation");

                    BillingPayment newPayment = new BillingPayment(
                            userId,
                            type,
                            paymentIntent.getAmount(),
                            paymentIntent.getCurrency()
                    );
                    newPayment.setStripePaymentIntentId(paymentIntent.getId());
                    return newPayment;
                });

        if (payment == null) {
            logger.warn("Could not create or find payment for payment intent {}", paymentIntent.getId());
            return;
        }

        // Update payment status
        payment.setStatus("succeeded");
        payment.setPaidAt(Instant.ofEpochSecond(paymentIntent.getCreated()));

        // Add charge information if available (latestCharge is an ExpandableField in Stripe SDK)
        if (paymentIntent.getLatestCharge() != null) {
            String chargeId = paymentIntent.getLatestCharge();
            payment.setStripeChargeId(chargeId);
        }

        billingPaymentRepository.save(payment);
        logger.info("Updated payment record for payment intent {}", paymentIntent.getId());
    }
}
