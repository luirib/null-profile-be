package ch.nullprofile.billing.repository;

import ch.nullprofile.billing.model.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillingEvent entity.
 */
@Repository
public interface BillingEventRepository extends JpaRepository<BillingEvent, UUID> {
    
    Optional<BillingEvent> findByStripeEventId(String stripeEventId);
    
    boolean existsByStripeEventId(String stripeEventId);
}
