package ch.nullprofile.billing.repository;

import ch.nullprofile.billing.model.BillingCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for BillingCustomer entity.
 */
@Repository
public interface BillingCustomerRepository extends JpaRepository<BillingCustomer, UUID> {
    
    Optional<BillingCustomer> findByUserId(UUID userId);
    
    Optional<BillingCustomer> findByStripeCustomerId(String stripeCustomerId);
    
    boolean existsByUserId(UUID userId);
}
