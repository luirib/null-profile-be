package ch.nullprofile.billing.service;

import ch.nullprofile.billing.config.BillingProperties;
import ch.nullprofile.billing.model.BillingCustomer;
import ch.nullprofile.billing.repository.BillingCustomerRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.checkout.Session;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for handling donation payments via Stripe.
 * 
 * Manages Stripe Checkout session creation for one-time donations.
 * Automatically creates and maintains Stripe customer records.
 */
@Service
public class StripeDonationService {

    private static final Logger logger = LoggerFactory.getLogger(StripeDonationService.class);

    private final BillingProperties billingProperties;
    private final BillingCustomerRepository billingCustomerRepository;

    public StripeDonationService(
            BillingProperties billingProperties,
            BillingCustomerRepository billingCustomerRepository) {
        this.billingProperties = billingProperties;
        this.billingCustomerRepository = billingCustomerRepository;
    }

    /**
     * Create a Stripe Checkout session for a donation.
     *
     * @param userId The nullProfile user ID making the donation
     * @param amountMinorUnits The donation amount in minor units (e.g., cents)
     * @return The Stripe Checkout session URL
     * @throws IllegalStateException if billing is disabled
     * @throws IllegalArgumentException if amount is invalid
     * @throws StripeException if Stripe API call fails
     */
    @Transactional
    public String createDonationCheckoutSession(UUID userId, long amountMinorUnits) throws StripeException {
        logger.info("Creating donation checkout session for user {} with amount {}", userId, amountMinorUnits);

        // Check if billing is enabled
        if (!billingProperties.isEnabled()) {
            logger.warn("Billing is disabled, cannot create checkout session");
            throw new IllegalStateException("Billing is currently disabled");
        }

        // Validate mode
        if (billingProperties.getMode() != BillingProperties.Mode.DONATION) {
            logger.warn("Current billing mode is {}, not DONATION", billingProperties.getMode());
        }

        // Validate amount
        if (amountMinorUnits <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }

        // Get or create Stripe customer
        String stripeCustomerId = getOrCreateStripeCustomer(userId);
        logger.debug("Using Stripe customer ID: {}", stripeCustomerId);

        // Create Checkout session
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomer(stripeCustomerId)
                .setSuccessUrl(billingProperties.getSuccessUrl())
                .setCancelUrl(billingProperties.getCancelUrl())
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(billingProperties.getCurrency().toLowerCase())
                                                .setUnitAmount(amountMinorUnits)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Donation to nullProfile")
                                                                .setDescription("Thank you for supporting nullProfile!")
                                                                .build()
                                                )
                                                .build()
                                )
                                .setQuantity(1L)
                                .build()
                )
                .putMetadata("userId", userId.toString())
                .putMetadata("type", "donation")
                .build();

        Session session = Session.create(params);
        logger.info("Created checkout session {} for user {}", session.getId(), userId);

        return session.getUrl();
    }

    /**
     * Get existing Stripe customer or create a new one.
     *
     * @param userId The nullProfile user ID
     * @return The Stripe customer ID
     * @throws StripeException if Stripe API call fails
     */
    private String getOrCreateStripeCustomer(UUID userId) throws StripeException {
        // Check if customer already exists in our database
        return billingCustomerRepository.findByUserId(userId)
                .map(BillingCustomer::getStripeCustomerId)
                .orElseGet(() -> {
                    try {
                        return createStripeCustomer(userId);
                    } catch (StripeException e) {
                        logger.error("Failed to create Stripe customer for user {}", userId, e);
                        throw new RuntimeException("Failed to create Stripe customer", e);
                    }
                });
    }

    /**
     * Create a new Stripe customer and save to database.
     *
     * @param userId The nullProfile user ID
     * @return The Stripe customer ID
     * @throws StripeException if Stripe API call fails
     */
    private String createStripeCustomer(UUID userId) throws StripeException {
        logger.info("Creating new Stripe customer for user {}", userId);

        // Create customer in Stripe
        CustomerCreateParams params = CustomerCreateParams.builder()
                .putMetadata("nullProfileUserId", userId.toString())
                .setDescription("nullProfile user " + userId)
                .build();

        Customer customer = Customer.create(params);
        logger.debug("Created Stripe customer: {}", customer.getId());

        // Save to database
        BillingCustomer billingCustomer = new BillingCustomer(
                userId,
                customer.getId(),
                customer.getEmail()
        );
        billingCustomerRepository.save(billingCustomer);
        logger.info("Saved billing customer record for user {}", userId);

        return customer.getId();
    }
}
