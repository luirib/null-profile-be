package ch.nullprofile.billing.repository;

import ch.nullprofile.billing.model.BillingSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillingSubscription entity.
 */
@Repository
public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscription, UUID> {
    
    List<BillingSubscription> findByUserId(UUID userId);
    
    Optional<BillingSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    List<BillingSubscription> findByUserIdAndStatus(UUID userId, String status);
}
