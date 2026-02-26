package ch.nullprofile.billing.repository;

import ch.nullprofile.billing.model.BillingPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillingPayment entity.
 */
@Repository
public interface BillingPaymentRepository extends JpaRepository<BillingPayment, UUID> {
    
    List<BillingPayment> findByUserId(UUID userId);
    
    List<BillingPayment> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    Optional<BillingPayment> findByStripePaymentIntentId(String stripePaymentIntentId);
    
    Optional<BillingPayment> findByStripeInvoiceId(String stripeInvoiceId);
    
    List<BillingPayment> findByTypeAndStatus(String type, String status);
    
    /**
     * Sum all donation amounts for a specific user.
     * Returns 0 if user has no successful donations.
     *
     * @param userId The user ID to sum donations for
     * @return Total donation amount in minor units (cents)
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BillingPayment p " +
           "WHERE p.userId = :userId AND p.type = 'donation' AND p.status = 'succeeded'")
    Long sumUserDonations(@Param("userId") UUID userId);
    
    /**
     * Sum all successful donations across all users (project total).
     * Returns 0 if no successful donations exist.
     *
     * @return Total project donations in minor units (cents)
     */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM BillingPayment p " +
           "WHERE p.type = 'donation' AND p.status = 'succeeded'")
    Long sumProjectDonations();
    
    /**
     * Count distinct users who have made at least one successful donation.
     *
     * @return Number of unique supporters
     */
    @Query("SELECT COUNT(DISTINCT p.userId) FROM BillingPayment p " +
           "WHERE p.type = 'donation' AND p.status = 'succeeded'")
    Long countDistinctSupporters();
}
