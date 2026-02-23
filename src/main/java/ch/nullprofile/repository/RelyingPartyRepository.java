package ch.nullprofile.repository;

import ch.nullprofile.entity.RelyingParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RelyingPartyRepository extends JpaRepository<RelyingParty, UUID> {
    Optional<RelyingParty> findByRpId(String rpId);
    List<RelyingParty> findByCreatedByUserId(UUID createdByUserId);
    
    @Modifying
    @Query("DELETE FROM RelyingParty rp WHERE rp.createdByUserId = :userId")
    void deleteByCreatedByUserId(@Param("userId") UUID userId);
}
