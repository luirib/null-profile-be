package ch.nullprofile.repository;

import ch.nullprofile.entity.RpMonthlyCounter;
import ch.nullprofile.entity.RpMonthlyCounterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RpMonthlyCounterRepository extends JpaRepository<RpMonthlyCounter, RpMonthlyCounterId> {

    /**
     * Get authentication count for a specific RP in a specific month
     */
    Optional<RpMonthlyCounter> findByRelyingPartyIdAndMonth(UUID relyingPartyId, LocalDate month);

    /**
     * Get authentication counters for a specific RP over a date range
     */
    List<RpMonthlyCounter> findByRelyingPartyIdAndMonthBetweenOrderByMonthDesc(
            UUID relyingPartyId, LocalDate startMonth, LocalDate endMonth);

    /**
     * Get all authentication counters for a specific RP
     */
    List<RpMonthlyCounter> findByRelyingPartyIdOrderByMonthDesc(UUID relyingPartyId);
}
