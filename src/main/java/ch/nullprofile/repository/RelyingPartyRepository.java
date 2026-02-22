package ch.nullprofile.repository;

import ch.nullprofile.entity.RelyingParty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RelyingPartyRepository extends JpaRepository<RelyingParty, UUID> {
    Optional<RelyingParty> findByRpId(String rpId);
}
