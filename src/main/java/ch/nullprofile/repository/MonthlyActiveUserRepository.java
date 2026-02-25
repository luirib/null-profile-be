package ch.nullprofile.repository;

import ch.nullprofile.entity.MonthlyActiveUser;
import ch.nullprofile.entity.MonthlyActiveUserId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface MonthlyActiveUserRepository extends JpaRepository<MonthlyActiveUser, MonthlyActiveUserId> {

    /**
     * Get MAU count for a specific RP in a specific month
     */
    @Query("SELECT COUNT(m) FROM MonthlyActiveUser m WHERE m.relyingPartyId = :rpId AND m.month = :month")
    long countByRelyingPartyIdAndMonth(@Param("rpId") UUID rpId, @Param("month") LocalDate month);

    /**
     * Get MAU records for a specific RP over a date range
     */
    List<MonthlyActiveUser> findByRelyingPartyIdAndMonthBetweenOrderByMonthDesc(
            UUID relyingPartyId, LocalDate startMonth, LocalDate endMonth);

    /**
     * Get all MAU records for a specific RP
     */
    List<MonthlyActiveUser> findByRelyingPartyIdOrderByMonthDesc(UUID relyingPartyId);
}
