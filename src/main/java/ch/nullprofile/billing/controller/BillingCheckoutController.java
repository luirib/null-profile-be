package ch.nullprofile.billing.controller;

import ch.nullprofile.billing.dto.CheckoutSessionResponse;
import ch.nullprofile.billing.dto.CreateDonationCheckoutRequest;
import ch.nullprofile.billing.dto.DonationSummaryResponse;
import ch.nullprofile.billing.service.DonationSummaryService;
import ch.nullprofile.billing.service.StripeDonationService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for billing and donation operations.
 * 
 * Handles:
 * - Creation of Stripe Checkout sessions for donations
 * - Donation summary statistics (user total, project total, supporter count)
 * 
 * Future: Can be extended to support subscription checkouts.
 */
@RestController
@RequestMapping("/api/billing")
public class BillingCheckoutController {

    private static final Logger logger = LoggerFactory.getLogger(BillingCheckoutController.class);

    private final StripeDonationService stripeDonationService;
    private final DonationSummaryService donationSummaryService;

    public BillingCheckoutController(
            StripeDonationService stripeDonationService,
            DonationSummaryService donationSummaryService) {
        this.stripeDonationService = stripeDonationService;
        this.donationSummaryService = donationSummaryService;
    }

    /**
     * Create a Stripe Checkout session for a donation.
     *
     * @param request The donation checkout request containing userId and amount
     * @return CheckoutSessionResponse with the Stripe Checkout URL
     */
    @PostMapping(
            value = "/donations/checkout-session",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createDonationCheckoutSession(
            @Valid @RequestBody CreateDonationCheckoutRequest request) {
        
        logger.info("Received donation checkout request for user {} with amount {}", 
                request.getUserId(), request.getAmount());

        try {
            String checkoutUrl = stripeDonationService.createDonationCheckoutSession(
                    request.getUserId(),
                    request.getAmount()
            );

            logger.info("Successfully created checkout session for user {}", request.getUserId());
            return ResponseEntity.ok(new CheckoutSessionResponse(checkoutUrl));

        } catch (IllegalStateException e) {
            logger.error("Billing is disabled: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Billing is currently disabled"));

        } catch (IllegalArgumentException e) {
            logger.error("Invalid request: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));

        } catch (StripeException e) {
            logger.error("Stripe API error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to create checkout session", 
                            "message", e.getMessage()));

        } catch (Exception e) {
            logger.error("Unexpected error creating checkout session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred"));
        }
    }

    /**
     * Get donation summary statistics for a user.
     * 
     * <p><strong>Frontend Usage:</strong></p>
     * <ol>
     *   <li>Call GET /api/billing/donations/summary?userId={uuid} on /billing page load</li>
     *   <li>Display donation statistics:
     *     <ul>
     *       <li>"You've contributed €X" (use userTotalMinor / 100)</li>
     *       <li>"Total raised €Y" (use projectTotalMinor / 100)</li>
     *       <li>"Supporters N" (use supporterCount)</li>
     *     </ul>
     *   </li>
     *   <li>On donation success, redirect to /billing/success</li>
     *   <li>On donation cancel, redirect to /billing/cancel</li>
     * </ol>
     * 
     * <p><strong>Response format:</strong></p>
     * <pre>
     * {
     *   "currency": "eur",
     *   "userTotalMinor": 5000,
     *   "projectTotalMinor": 125000,
     *   "supporterCount": 42
     * }
     * </pre>
     *
     * @param userId The UUID of the user to get donation summary for (required)
     * @return DonationSummaryResponse with user total, project total, and supporter count
     */
    @GetMapping(
            value = "/donations/summary",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getDonationSummary(@RequestParam(required = true) String userId) {
        logger.debug("Received donation summary request for user {}", userId);

        try {
            // Parse and validate userId
            UUID userUuid = UUID.fromString(userId);

            // Get summary from service
            DonationSummaryResponse summary = donationSummaryService.getSummary(userUuid);

            logger.debug("Successfully retrieved donation summary for user {}", userId);
            return ResponseEntity.ok(summary);

        } catch (IllegalArgumentException e) {
            logger.error("Invalid userId parameter: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Invalid userId parameter. Must be a valid UUID."));

        } catch (Exception e) {
            logger.error("Unexpected error retrieving donation summary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred"));
        }
    }
}
