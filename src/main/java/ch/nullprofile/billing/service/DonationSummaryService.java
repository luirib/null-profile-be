package ch.nullprofile.billing.service;

import ch.nullprofile.billing.config.BillingProperties;
import ch.nullprofile.billing.dto.DonationSummaryResponse;
import ch.nullprofile.billing.repository.BillingPaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Service for retrieving donation summary statistics.
 * 
 * Provides aggregated donation data for user and project-wide views.
 * Does not interact with Stripe API - reads from local database only.
 */
@Service
public class DonationSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(DonationSummaryService.class);

    private final BillingPaymentRepository billingPaymentRepository;
    private final BillingProperties billingProperties;

    public DonationSummaryService(
            BillingPaymentRepository billingPaymentRepository,
            BillingProperties billingProperties) {
        this.billingPaymentRepository = billingPaymentRepository;
        this.billingProperties = billingProperties;
    }

    /**
     * Get donation summary for a specific user.
     * 
     * Returns:
     * - User's lifetime donation total
     * - Project total raised across all users
     * - Number of distinct supporters
     * 
     * All amounts are in minor units (cents).
     * Null values from database queries are converted to 0.
     *
     * @param userId The user ID to get summary for
     * @return DonationSummaryResponse with all statistics
     */
    public DonationSummaryResponse getSummary(UUID userId) {
        logger.debug("Fetching donation summary for user {}", userId);

        // Get currency from configuration
        String currency = billingProperties.getCurrency();

        // Query database with null-safe handling
        Long userTotal = billingPaymentRepository.sumUserDonations(userId);
        Long projectTotal = billingPaymentRepository.sumProjectDonations();
        Long supporterCount = billingPaymentRepository.countDistinctSupporters();

        // Convert null to 0 (shouldn't happen with COALESCE, but be defensive)
        long userTotalSafe = userTotal != null ? userTotal : 0L;
        long projectTotalSafe = projectTotal != null ? projectTotal : 0L;
        long supporterCountSafe = supporterCount != null ? supporterCount : 0L;

        logger.debug("Summary for user {}: userTotal={}, projectTotal={}, supporters={}", 
                userId, userTotalSafe, projectTotalSafe, supporterCountSafe);

        return new DonationSummaryResponse(
                currency,
                userTotalSafe,
                projectTotalSafe,
                supporterCountSafe
        );
    }
}
