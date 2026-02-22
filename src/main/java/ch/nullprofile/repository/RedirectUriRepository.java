package ch.nullprofile.repository;

import ch.nullprofile.entity.RedirectUri;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedirectUriRepository extends JpaRepository<RedirectUri, UUID> {
    List<RedirectUri> findByRelyingPartyId(UUID relyingPartyId);
    Optional<RedirectUri> findByRelyingPartyIdAndUri(UUID relyingPartyId, String uri);
}
