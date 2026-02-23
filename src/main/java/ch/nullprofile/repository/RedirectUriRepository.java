package ch.nullprofile.repository;

import ch.nullprofile.entity.RedirectUri;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedirectUriRepository extends JpaRepository<RedirectUri, UUID> {
    List<RedirectUri> findByRelyingPartyId(UUID relyingPartyId);
    Optional<RedirectUri> findByRelyingPartyIdAndUri(UUID relyingPartyId, String uri);
    
    @Modifying
    @Query("DELETE FROM RedirectUri r WHERE r.relyingPartyId = :relyingPartyId")
    void deleteByRelyingPartyId(@Param("relyingPartyId") UUID relyingPartyId);
}
